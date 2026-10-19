package com.zahidcodes.copynest.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ClipEntity::class, LinkMetadataEntity::class],
    version = 3,
    exportSchema = false
)
abstract class ClipDatabase : RoomDatabase() {
    abstract fun clipDao(): ClipDao

    companion object {
        @Volatile
        private var INSTANCE: ClipDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clips ADD COLUMN title TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clips ADD COLUMN linkUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE clips ADD COLUMN linkDomain TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE clips ADD COLUMN linkSiteName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE clips ADD COLUMN linkTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE clips ADD COLUMN linkDescription TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE clips ADD COLUMN linkFaviconBase64 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE clips ADD COLUMN linkMetadataLoaded INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `link_metadata_cache` (
                        `normalizedUrl` TEXT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `siteName` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `faviconBase64` TEXT NOT NULL,
                        `isLoaded` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`normalizedUrl`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): ClipDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClipDatabase::class.java,
                    "copynest_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
