package com.cussou.autotiq.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cussou.autotiq.data.local.dao.MapPointDao
import com.cussou.autotiq.data.local.dao.ProximityStateDao
import com.cussou.autotiq.data.local.entity.MapPointEntity
import com.cussou.autotiq.data.local.entity.ProximityStateEntity

@Database(
    entities = [MapPointEntity::class, ProximityStateEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AutoTiqDatabase : RoomDatabase() {
    abstract fun mapPointDao(): MapPointDao
    abstract fun proximityStateDao(): ProximityStateDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SQLite doesn't support changing column nullability directly
        // We need to recreate the table
        
        // 1. Create new table with updated schema
        db.execSQL("""
            CREATE TABLE map_points_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                name TEXT NOT NULL DEFAULT '',
                startHour INTEGER NOT NULL DEFAULT 0,
                endHour INTEGER NOT NULL DEFAULT 23,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        
        // 2. Copy data from old table, converting null names to empty strings
        db.execSQL("""
            INSERT INTO map_points_new (id, latitude, longitude, name, startHour, endHour, createdAt)
            SELECT id, latitude, longitude, COALESCE(name, ''), 0, 23, createdAt
            FROM map_points
        """.trimIndent())
        
        // 3. Drop old table
        db.execSQL("DROP TABLE map_points")
        
        // 4. Rename new table to original name
        db.execSQL("ALTER TABLE map_points_new RENAME TO map_points")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add minute fields to map_points table
        db.execSQL("ALTER TABLE map_points ADD COLUMN startMinute INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE map_points ADD COLUMN endMinute INTEGER NOT NULL DEFAULT 59")
    }
}
