package com.example.smarthomeai.cloud

import kotlinx.coroutines.flow.Flow

/**
 * Pure Kotlin Cloud Repository contract (P1.1-B1 + P1.1-B3 member operations).
 * Must not import any Firebase SDK types.
 */
interface CloudRepository {
    fun observeHomes(): Flow<CloudResult<List<CloudHome>>>
    suspend fun createHome(name: String): CloudResult<String>
    fun observeMembers(homeId: String): Flow<CloudResult<List<CloudMember>>>
    suspend fun leaveHome(homeId: String): CloudResult<Unit>
    suspend fun removeMember(homeId: String, memberUid: String): CloudResult<Unit>
}
