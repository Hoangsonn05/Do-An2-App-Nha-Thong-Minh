package com.example.smarthomeai.cloud

/**
 * Pure Kotlin RTDB payload builder aligned with P1.1-B0 §7 & §8.4 and F4/F10.
 * Accepts timestampValue as Any so production passes ServerValue.TIMESTAMP while JVM tests pass a sentinel.
 */
object RtdbPayloadBuilder {
    const val SCHEMA_VERSION: Int = 1
    const val MAX_HOME_NAME_LENGTH: Int = 60

    // Guard token checked to prevent accidental secret literal leakage into Cloud names (B0 §8.5).
    private const val FORBIDDEN_NAME_SUBSTRING = "deviceKey"

    fun validateAndNormalizeHomeName(rawName: String): String {
        val trimmed = rawName.trim()
        require(trimmed.isNotBlank()) { "Home name must not be blank." }
        require(trimmed.length <= MAX_HOME_NAME_LENGTH) {
            "Home name must be at most $MAX_HOME_NAME_LENGTH characters."
        }
        require(!trimmed.contains(FORBIDDEN_NAME_SUBSTRING, ignoreCase = true)) {
            "Home name contains a forbidden secret token."
        }
        return trimmed
    }

    fun buildHomeMetaPayload(
        name: String,
        ownerUid: String,
        timestampValue: Any
    ): Map<String, Any> {
        val cleanName = validateAndNormalizeHomeName(name)
        val validUid = RtdbPaths.requireValidUid(ownerUid)
        return mapOf(
            "name" to cleanName,
            "ownerUid" to validUid,
            "createdAt" to timestampValue,
            "schemaVersion" to SCHEMA_VERSION
        )
    }

    fun buildOwnerMemberPayload(
        ownerUid: String,
        timestampValue: Any
    ): Map<String, Any> {
        val validUid = RtdbPaths.requireValidUid(ownerUid)
        return mapOf(
            "role" to CloudRole.OWNER.wireValue,
            "addedAt" to timestampValue,
            "addedBy" to validUid
        )
    }

    fun buildUserHomeRoleValue(role: CloudRole): String = role.wireValue

    /**
     * Builds the root-level multi-path atomic delete map for Leave Home and Remove Member (B0 §6.7, B3.3).
     * Atomically clears both homes/{homeId}/members/{memberUid} and userHomes/{memberUid}/{homeId}.
     */
    fun buildRemoveMembershipUpdates(homeId: String, memberUid: String): Map<String, Any?> {
        val memberPath = RtdbPaths.homeMemberPath(homeId, memberUid)
        val userHomePath = RtdbPaths.userHomePath(memberUid, homeId)
        return mapOf(
            memberPath to null,
            userHomePath to null
        )
    }
}
