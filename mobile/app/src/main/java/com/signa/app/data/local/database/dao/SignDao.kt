package com.signa.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.signa.app.data.local.database.entity.TrafficSignEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SignDao {
    @Query("SELECT * FROM traffic_signs ORDER BY code, id")
    fun observeAll(): Flow<List<TrafficSignEntity>>

    @Query("SELECT * FROM traffic_signs WHERE id = :id")
    fun observeById(id: String): Flow<TrafficSignEntity?>

    @Query("SELECT COUNT(*) FROM traffic_signs")
    suspend fun count(): Int

    @Query("UPDATE traffic_signs SET latitude = :lat, longitude = :lng WHERE id = :id AND code = :code AND latitude IS NULL AND longitude IS NULL")
    suspend fun fillMissingSamplePosition(id: String, code: String, lat: Double, lng: Double)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(signs: List<TrafficSignEntity>)
}
