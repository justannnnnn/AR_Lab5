package com.example.ar_lab5.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geo_markers")
data class GeoMarker(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,

    val name: String,

    val createdAt: Long = System.currentTimeMillis()
)