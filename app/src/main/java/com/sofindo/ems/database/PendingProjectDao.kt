package com.sofindo.ems.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingProjectDao {
    
    @Query("SELECT * FROM pending_projects ORDER BY createdAt ASC")
    fun getAllPendingProjects(): Flow<List<PendingProject>>
    
    @Query("SELECT * FROM pending_projects WHERE id = :id")
    suspend fun getPendingProjectById(id: Long): PendingProject?
    
    @Query("SELECT * FROM pending_projects ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPendingProjects(limit: Int = 10): List<PendingProject>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingProject(project: PendingProject): Long
    
    @Update
    suspend fun updatePendingProject(project: PendingProject)
    
    @Delete
    suspend fun deletePendingProject(project: PendingProject)
    
    @Query("DELETE FROM pending_projects WHERE id = :id")
    suspend fun deletePendingProjectById(id: Long)
    
    @Query("SELECT COUNT(*) FROM pending_projects")
    suspend fun getPendingCount(): Int
    
    @Query("SELECT COUNT(*) FROM pending_projects WHERE requestType = :requestType")
    suspend fun getPendingCountByType(requestType: String): Int
}

































