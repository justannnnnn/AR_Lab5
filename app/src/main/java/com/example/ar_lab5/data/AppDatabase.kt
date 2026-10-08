package com.example.ar_lab5.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [GeoMarker::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun geoMarkerDao(): GeoMarkerDao
}