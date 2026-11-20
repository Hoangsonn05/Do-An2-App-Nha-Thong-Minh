package com.example.smarthomeai.cloud

import kotlinx.coroutines.flow.Flow

/**
 * Pure Kotlin Cloud Repository contract for P1.1-B1.
 * Must not import any Firebase SDK types.
 */
interface CloudRepository {
    fun observeHomes(): Flow<CloudResult<List<CloudHome>>>
    suspend fun createHome(name: String): CloudResult<String>
    fun observeMembers(homeId: String): Flow<CloudResult<List<CloudMember>>>
}
