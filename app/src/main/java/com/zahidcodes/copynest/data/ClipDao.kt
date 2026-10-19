package com.zahidcodes.copynest.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {
    @Query("SELECT * FROM clips ORDER BY isPinned DESC, timestamp DESC")
    fun getAllClips(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE text LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' ORDER BY isPinned DESC, timestamp DESC")
    fun searchClips(query: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE contentType = :type ORDER BY isPinned DESC, timestamp DESC")
    fun getClipsByType(type: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE isPinned = 1 ORDER BY timestamp DESC")
    fun getPinnedClips(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips ORDER BY timestamp DESC LIMIT 1")
    suspend fun getMostRecentClip(): ClipEntity?

    @Query("SELECT * FROM clips WHERE TRIM(text) = TRIM(:text) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getClipByText(text: String): ClipEntity?

    @Query("SELECT * FROM clips WHERE id = :id LIMIT 1")
    suspend fun getClipById(id: Long): ClipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: ClipEntity): Long

    @Update
    suspend fun updateClip(clip: ClipEntity)

    @Query("SELECT * FROM clips ORDER BY timestamp DESC")
    suspend fun getAllClipsList(): List<ClipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClips(clips: List<ClipEntity>)

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun deleteClipById(id: Long)

    @Query("DELETE FROM clips WHERE id IN (:ids)")
    suspend fun deleteClipsByIds(ids: List<Long>)

    @Query("DELETE FROM clips")
    suspend fun deleteAllClips()

    @Query("SELECT * FROM link_metadata_cache")
    suspend fun getAllCachedLinkMetadata(): List<LinkMetadataEntity>

    @Query("SELECT * FROM link_metadata_cache WHERE normalizedUrl = :url LIMIT 1")
    suspend fun getCachedLinkMetadata(url: String): LinkMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedLinkMetadata(entity: LinkMetadataEntity)

    @Query(
        """
        UPDATE clips 
        SET linkUrl = :linkUrl,
            linkDomain = :linkDomain,
            linkSiteName = :linkSiteName,
            linkTitle = :linkTitle,
            linkDescription = :linkDescription,
            linkFaviconBase64 = :linkFaviconBase64,
            linkMetadataLoaded = 1
        WHERE id = :clipId
        """
    )
    suspend fun updateClipLinkMetadataById(
        clipId: Long,
        linkUrl: String,
        linkDomain: String,
        linkSiteName: String,
        linkTitle: String,
        linkDescription: String,
        linkFaviconBase64: String
    )

    @Query(
        """
        UPDATE clips 
        SET linkDomain = :linkDomain,
            linkSiteName = :linkSiteName,
            linkTitle = :linkTitle,
            linkDescription = :linkDescription,
            linkFaviconBase64 = :linkFaviconBase64,
            linkMetadataLoaded = 1
        WHERE linkUrl = :linkUrl
        """
    )
    suspend fun updateClipsLinkMetadataByUrl(
        linkUrl: String,
        linkDomain: String,
        linkSiteName: String,
        linkTitle: String,
        linkDescription: String,
        linkFaviconBase64: String
    )
}
