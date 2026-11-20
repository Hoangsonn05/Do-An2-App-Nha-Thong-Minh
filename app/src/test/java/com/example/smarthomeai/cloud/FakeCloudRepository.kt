package com.example.smarthomeai.cloud

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory test double for CloudRepository (src/test ONLY).
 */
class FakeCloudRepository(
    initialUid: String? = "test_user_uA",
    initialAvailable: Boolean = true
) : CloudRepository {

    private var available: Boolean = initialAvailable
    private var currentUid: String? = initialUid
    private var nextFailure: Pair<CloudError, String?>? = null
    private var idCounter: Int = 1000

    private val userHomeIds = MutableStateFlow<List<String>>(emptyList())
    private val homeMetas = MutableStateFlow<Map<String, HomeMetaFetchResult>>(emptyMap())
    private val membersByHome = MutableStateFlow<Map<String, List<CloudMember>>>(emptyMap())

    fun setAvailable(isAvailable: Boolean) {
        available = isAvailable
    }

    fun setSignedInUid(uid: String?) {
        currentUid = uid
    }

    fun failNext(error: CloudError, message: String? = null) {
        nextFailure = error to message
    }

    fun seedStaleHomePermissionDenied(homeId: String) {
        userHomeIds.value = (userHomeIds.value + homeId).distinct()
        homeMetas.value = homeMetas.value + (homeId to HomeMetaFetchResult.PermissionDenied)
    }

    fun seedMember(homeId: String, member: CloudMember) {
        val current = membersByHome.value[homeId].orEmpty()
        membersByHome.value = membersByHome.value + (homeId to (current.filterNot { it.uid == member.uid } + member))
    }

    override fun observeHomes(): Flow<CloudResult<List<CloudHome>>> {
        if (!available) return MutableStateFlow(CloudResult.Unavailable)
        val uid = currentUid ?: return MutableStateFlow(
            CloudResult.Err(CloudError.Unauthenticated, "User is not signed in.")
        )
        nextFailure?.let { (err, msg) ->
            nextFailure = null
            return MutableStateFlow(CloudResult.Err(err, msg))
        }
        return userHomeIds.map { ids ->
            RtdbHomeMerger.mergeHomes(uid, ids, homeMetas.value)
        }
    }

    override suspend fun createHome(name: String): CloudResult<String> {
        if (!available) return CloudResult.Unavailable
        val uid = currentUid ?: return CloudResult.Err(CloudError.Unauthenticated, "User is not signed in.")
        nextFailure?.let { (err, msg) ->
            nextFailure = null
            return CloudResult.Err(err, msg)
        }

        val cleanName = try {
            RtdbPayloadBuilder.validateAndNormalizeHomeName(name)
        } catch (e: IllegalArgumentException) {
            return CloudResult.Err(CloudError.InvalidData, e.message)
        }

        val homeId = "home_FAKE_${idCounter++}"
        val nowMs = System.currentTimeMillis()
        homeMetas.value = homeMetas.value + (homeId to HomeMetaFetchResult.Found(homeId, cleanName, uid))
        membersByHome.value = membersByHome.value + (
            homeId to listOf(
                CloudMember(uid = uid, role = CloudRole.OWNER, addedAt = nowMs, addedBy = uid)
            )
        )
        userHomeIds.value = userHomeIds.value + homeId
        return CloudResult.Ok(homeId)
    }

    override fun observeMembers(homeId: String): Flow<CloudResult<List<CloudMember>>> {
        if (!available) return MutableStateFlow(CloudResult.Unavailable)
        if (currentUid == null) {
            return MutableStateFlow(CloudResult.Err(CloudError.Unauthenticated, "User is not signed in."))
        }
        if (!RtdbPaths.isValidEntityId(homeId)) {
            return MutableStateFlow(CloudResult.Err(CloudError.InvalidData, "Invalid homeId: $homeId"))
        }
        nextFailure?.let { (err, msg) ->
            nextFailure = null
            return MutableStateFlow(CloudResult.Err(err, msg))
        }
        return membersByHome.map { map ->
            CloudResult.Ok(map[homeId].orEmpty())
        }
    }
}
