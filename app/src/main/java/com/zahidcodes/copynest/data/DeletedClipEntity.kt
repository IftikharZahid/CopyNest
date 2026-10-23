package com.zahidcodes.copynest.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_clips")
data class DeletedClipEntity(
    @PrimaryKey
    val contentHash: String,
    val normalizedText: String = "",
    val deletedAt: Long = System.currentTimeMillis(),
    val clipboardTimestamp: Long = 0L,
    val driveFileId: String = "",
    val syncedDeletionToDrive: Boolean = false
)
