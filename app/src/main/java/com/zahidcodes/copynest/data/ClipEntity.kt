package com.zahidcodes.copynest.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clips")
data class ClipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String = "",
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val contentType: String = "TEXT", // TEXT, URL, CODE, NOTE
    val category: String = "General",
    val linkUrl: String = "",
    val linkDomain: String = "",
    val linkSiteName: String = "",
    val linkTitle: String = "",
    val linkDescription: String = "",
    val linkFaviconBase64: String = "",
    val linkMetadataLoaded: Boolean = false
)

@Entity(tableName = "link_metadata_cache")
data class LinkMetadataEntity(
    @PrimaryKey
    val normalizedUrl: String,
    val domain: String,
    val siteName: String,
    val title: String,
    val description: String = "",
    val faviconBase64: String = "",
    val isLoaded: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
