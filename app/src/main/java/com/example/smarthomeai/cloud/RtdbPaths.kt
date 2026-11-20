package com.example.smarthomeai.cloud

/**
 * Pure Kotlin RTDB path builder & segment validator aligned with P1.1-B0 §6.2, §7, and F10.
 */
object RtdbPaths {
    private val ENTITY_ID_REGEX = Regex("^[A-Za-z0-9_-]{8,64}$")
    private val FORBIDDEN_RTDB_CHARS = charArrayOf('/', '.', '#', '$', '[', ']')
    private const val MAX_UID_LENGTH = 128

    fun isValidEntityId(id: String): Boolean =
        ENTITY_ID_REGEX.matches(id) && !containsForbiddenRtdbChars(id)

    fun isValidUid(uid: String): Boolean {
        if (uid.isBlank() || uid.length > MAX_UID_LENGTH) return false
        if (uid != uid.trim()) return false
        if (containsForbiddenRtdbChars(uid)) return false
        return uid.none { it.isISOControl() }
    }

    fun containsForbiddenRtdbChars(segment: String): Boolean =
        segment.indexOfAny(FORBIDDEN_RTDB_CHARS) >= 0

    fun requireValidEntityId(id: String, label: String = "id"): String {
        require(isValidEntityId(id)) {
            "Invalid RTDB $label: must match ^[A-Za-z0-9_-]{8,64}$ and contain no forbidden characters."
        }
        return id
    }

    fun requireValidUid(uid: String): String {
        require(isValidUid(uid)) {
            "Invalid RTDB uid: must be non-blank, <= $MAX_UID_LENGTH chars, and contain no forbidden RTDB characters (/ . # $ [ ])."
        }
        return uid
    }

    fun userHomesPath(uid: String): String {
        val validUid = requireValidUid(uid)
        return "userHomes/$validUid"
    }

    fun userHomePath(uid: String, homeId: String): String {
        val validUid = requireValidUid(uid)
        val validHomeId = requireValidEntityId(homeId, "homeId")
        return "userHomes/$validUid/$validHomeId"
    }

    fun homeMetaPath(homeId: String): String {
        val validHomeId = requireValidEntityId(homeId, "homeId")
        return "homes/$validHomeId/meta"
    }

    fun homeMembersPath(homeId: String): String {
        val validHomeId = requireValidEntityId(homeId, "homeId")
        return "homes/$validHomeId/members"
    }

    fun homeMemberPath(homeId: String, uid: String): String {
        val validHomeId = requireValidEntityId(homeId, "homeId")
        val validUid = requireValidUid(uid)
        return "homes/$validHomeId/members/$validUid"
    }
}
