package com.example.smarthomeai.cloud

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Firebase Realtime Database implementation of CloudRepository (P1.1-B1).
 *
 * Implements only the 3 B1 APIs: observeHomes, createHome, observeMembers.
 * Never wraps continuous callbackFlow observers with withTimeout.
 * Only converts TimeoutCancellationException to CloudError.Timeout; other CancellationExceptions propagate.
 */
class RtdbCloudRepository(
    private val provider: FirebaseProvider,
    private val timeoutMs: Long = CLOUD_OPERATION_TIMEOUT_MS,
    private val orphanHomeLogger: (homeId: String, step: String, error: CloudError) -> Unit = { homeId, step, error ->
        Log.w(TAG, "createHome failed at step=$step ($error); orphan homeId=$homeId")
    }
) : CloudRepository {

    override fun observeHomes(): Flow<CloudResult<List<CloudHome>>> = callbackFlow {
        val db = provider.databaseOrNull()
        if (!provider.isConfigured || db == null) {
            trySend(CloudResult.Unavailable)
            close()
            return@callbackFlow
        }
        val currentUid = provider.uid
        if (currentUid == null || !RtdbPaths.isValidUid(currentUid)) {
            trySend(CloudResult.Err(CloudError.Unauthenticated, "User is not signed in."))
            close()
            return@callbackFlow
        }

        val userHomesRef = db.getReference(RtdbPaths.userHomesPath(currentUid))
        var activeMergeJob: Job? = null

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                activeMergeJob?.cancel()
                val homeIds = try {
                    snapshot.children
                        .mapNotNull { it.key }
                        .filter { RtdbPaths.isValidEntityId(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    val mapped = if (t is DatabaseException) {
                        CloudError.InvalidData
                    } else {
                        CloudErrorMapper.mapException(t)
                    }
                    trySend(CloudResult.Err(mapped, t.message))
                    return
                }

                if (homeIds.isEmpty()) {
                    trySend(CloudResult.Ok(emptyList()))
                    return
                }

                activeMergeJob = launch {
                    val metaResults = mutableMapOf<String, HomeMetaFetchResult>()
                    for (homeId in homeIds) {
                        metaResults[homeId] = fetchHomeMeta(db, homeId)
                    }
                    trySend(RtdbHomeMerger.mergeHomes(currentUid, homeIds, metaResults))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(CloudResult.Err(CloudErrorMapper.mapDatabaseError(error), error.message))
                close()
            }
        }

        userHomesRef.addValueEventListener(listener)
        awaitClose {
            activeMergeJob?.cancel()
            userHomesRef.removeEventListener(listener)
        }
    }

    override suspend fun createHome(name: String): CloudResult<String> {
        val db = provider.databaseOrNull()
        if (!provider.isConfigured || db == null) {
            return CloudResult.Unavailable
        }
        val currentUid = provider.uid
        if (currentUid == null || !RtdbPaths.isValidUid(currentUid)) {
            return CloudResult.Err(CloudError.Unauthenticated, "User is not signed in.")
        }

        val metaPayload: Map<String, Any>
        val memberPayload: Map<String, Any>
        val userHomeRole: String
        try {
            metaPayload = RtdbPayloadBuilder.buildHomeMetaPayload(
                name = name,
                ownerUid = currentUid,
                timestampValue = ServerValue.TIMESTAMP
            )
            memberPayload = RtdbPayloadBuilder.buildOwnerMemberPayload(
                ownerUid = currentUid,
                timestampValue = ServerValue.TIMESTAMP
            )
            userHomeRole = RtdbPayloadBuilder.buildUserHomeRoleValue(CloudRole.OWNER)
        } catch (e: IllegalArgumentException) {
            return CloudResult.Err(CloudError.InvalidData, e.message)
        }

        val homeId = db.getReference("homes").push().key
        if (homeId == null || !RtdbPaths.isValidEntityId(homeId)) {
            return CloudResult.Err(CloudError.InvalidData, "Generated homeId is invalid.")
        }

        // Step 1: create homes/{homeId}/meta
        val step1Error = runTimedWrite {
            db.getReference(RtdbPaths.homeMetaPath(homeId)).setValue(metaPayload).await()
        }
        if (step1Error != null) {
            return step1Error
        }

        // Step 2: create homes/{homeId}/members/{currentUid}
        val step2Error = runTimedWrite {
            db.getReference(RtdbPaths.homeMemberPath(homeId, currentUid))
                .setValue(memberPayload)
                .await()
        }
        if (step2Error != null) {
            orphanHomeLogger(homeId, "members", step2Error.error)
            return CloudResult.Err(
                step2Error.error,
                "Failed to write owner membership for orphan homeId=$homeId: ${step2Error.message.orEmpty()}"
            )
        }

        // Step 3: create userHomes/{currentUid}/{homeId}
        val step3Error = runTimedWrite {
            db.getReference(RtdbPaths.userHomePath(currentUid, homeId))
                .setValue(userHomeRole)
                .await()
        }
        if (step3Error != null) {
            orphanHomeLogger(homeId, "userHomes", step3Error.error)
            return CloudResult.Err(
                step3Error.error,
                "Failed to write userHomes index for orphan homeId=$homeId: ${step3Error.message.orEmpty()}"
            )
        }

        return CloudResult.Ok(homeId)
    }

    override fun observeMembers(homeId: String): Flow<CloudResult<List<CloudMember>>> = callbackFlow {
        val db = provider.databaseOrNull()
        if (!provider.isConfigured || db == null) {
            trySend(CloudResult.Unavailable)
            close()
            return@callbackFlow
        }
        val currentUid = provider.uid
        if (currentUid == null || !RtdbPaths.isValidUid(currentUid)) {
            trySend(CloudResult.Err(CloudError.Unauthenticated, "User is not signed in."))
            close()
            return@callbackFlow
        }
        if (!RtdbPaths.isValidEntityId(homeId)) {
            trySend(CloudResult.Err(CloudError.InvalidData, "Invalid homeId: $homeId"))
            close()
            return@callbackFlow
        }

        val membersRef = db.getReference(RtdbPaths.homeMembersPath(homeId))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val members = snapshot.children.mapNotNull { child ->
                        parseMemberValues(
                            uid = child.key,
                            rawRole = child.child("role").value,
                            rawAddedAt = child.child("addedAt").value,
                            rawAddedBy = child.child("addedBy").value
                        )
                    }
                    trySend(CloudResult.Ok(members))
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    val mapped = if (t is DatabaseException) {
                        CloudError.InvalidData
                    } else {
                        CloudErrorMapper.mapException(t)
                    }
                    trySend(CloudResult.Err(mapped, t.message))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(CloudResult.Err(CloudErrorMapper.mapDatabaseError(error), error.message))
                close()
            }
        }

        membersRef.addValueEventListener(listener)
        awaitClose { membersRef.removeEventListener(listener) }
    }

    private suspend fun runTimedWrite(block: suspend () -> Unit): CloudResult.Err? {
        return try {
            withTimeout(timeoutMs) { block() }
            null
        } catch (e: TimeoutCancellationException) {
            CloudResult.Err(CloudError.Timeout, e.message ?: "Cloud operation timed out.")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            CloudResult.Err(CloudErrorMapper.mapException(t), t.message)
        }
    }

    private suspend fun fetchHomeMeta(
        db: FirebaseDatabase,
        homeId: String
    ): HomeMetaFetchResult {
        return try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val ref = db.getReference(RtdbPaths.homeMetaPath(homeId))
                    val listener = object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (!cont.isActive) return
                            val result = try {
                                if (!snapshot.exists()) {
                                    HomeMetaFetchResult.NotFound
                                } else {
                                    parseHomeMetaValues(
                                        homeId = homeId,
                                        rawName = snapshot.child("name").value,
                                        rawOwnerUid = snapshot.child("ownerUid").value
                                    )
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (t: Throwable) {
                                val mapped = if (t is DatabaseException) {
                                    CloudError.InvalidData
                                } else {
                                    CloudErrorMapper.mapException(t)
                                }
                                HomeMetaFetchResult.Failed(
                                    mapped,
                                    t.message ?: "Failed to parse home meta for $homeId"
                                )
                            }
                            if (cont.isActive) {
                                cont.resume(result)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            if (!cont.isActive) return
                            val mapped = CloudErrorMapper.mapDatabaseError(error)
                            if (mapped == CloudError.PermissionDenied) {
                                cont.resume(HomeMetaFetchResult.PermissionDenied)
                            } else {
                                cont.resume(HomeMetaFetchResult.Failed(mapped, error.message))
                            }
                        }
                    }
                    ref.addListenerForSingleValueEvent(listener)
                    cont.invokeOnCancellation { ref.removeEventListener(listener) }
                }
            }
        } catch (e: TimeoutCancellationException) {
            HomeMetaFetchResult.Failed(CloudError.Timeout, e.message ?: "Timed out reading home meta.")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val mapped = CloudErrorMapper.mapException(t)
            if (mapped == CloudError.PermissionDenied) {
                HomeMetaFetchResult.PermissionDenied
            } else {
                HomeMetaFetchResult.Failed(mapped, t.message)
            }
        }
    }

    companion object {
        private const val TAG = "RtdbCloudRepository"

        internal fun safeString(raw: Any?): String? = raw as? String

        internal fun safeLong(raw: Any?): Long? = when (raw) {
            is Long -> raw
            is Int -> raw.toLong()
            is Short -> raw.toLong()
            is Byte -> raw.toLong()
            is Number -> raw.toDouble().takeIf { it.isFinite() }?.toLong()
            else -> null
        }

        internal fun parseHomeMetaValues(
            homeId: String,
            rawName: Any?,
            rawOwnerUid: Any?
        ): HomeMetaFetchResult {
            val name = safeString(rawName)
            val ownerUid = safeString(rawOwnerUid)
            return if (name.isNullOrBlank() || ownerUid.isNullOrBlank()) {
                HomeMetaFetchResult.Failed(
                    CloudError.InvalidData,
                    "Malformed home meta for $homeId"
                )
            } else {
                HomeMetaFetchResult.Found(homeId, name, ownerUid)
            }
        }

        internal fun parseMemberValues(
            uid: String?,
            rawRole: Any?,
            rawAddedAt: Any?,
            rawAddedBy: Any?
        ): CloudMember? {
            if (uid.isNullOrBlank()) return null
            val role = CloudRole.fromWire(safeString(rawRole)) ?: return null
            val addedAt = safeLong(rawAddedAt)
            val addedBy = safeString(rawAddedBy)
            return CloudMember(
                uid = uid,
                role = role,
                addedAt = addedAt,
                addedBy = addedBy
            )
        }
    }
}
