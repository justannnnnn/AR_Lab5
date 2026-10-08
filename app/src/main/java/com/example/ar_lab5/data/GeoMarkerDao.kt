package com.example.ar_lab5.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GeoMarkerDao {
    @Insert
    suspend fun insert(marker: GeoMarker)

    @Query("""
        SELECT * FROM geo_markers
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    suspend fun getLastMarkers(limit: Int): List<GeoMarker>

    @Query("""
        SELECT * FROM geo_markers
        ORDER BY createdAt DESC
    """)
    suspend fun getAllMarkers(): List<GeoMarker>

    @Query("DELETE FROM geo_markers")
    suspend fun deleteAll()
}