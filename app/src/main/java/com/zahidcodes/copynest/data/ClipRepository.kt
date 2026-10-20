package com.zahidcodes.copynest.data

import kotlinx.coroutines.flow.Flow

class ClipRepository(private val clipDao: ClipDao) {
    val allClips: Flow<List<ClipEntity>> = clipDao.getAllClips()

    fun searchClips(query: String): Flow<List<ClipEntity>> = clipDao.searchClips(query)

    fun getClipsByType(type: String): Flow<List<ClipEntity>> = clipDao.getClipsByType(type)

    fun getPinnedClips(): Flow<List<ClipEntity>> = clipDao.getPinnedClips()

    suspend fun getMostRecentClip(): ClipEntity? = clipDao.getMostRecentClip()

    suspend fun getClipByText(text: String): ClipEntity? = clipDao.getClipByText(text)

    suspend fun getClipById(id: Long): ClipEntity? = clipDao.getClipById(id)

    suspend fun insertClip(clip: ClipEntity): Long = clipDao.insertClip(clip)

    suspend fun updateClip(clip: ClipEntity) = clipDao.updateClip(clip)

    suspend fun getAllClipsList(): List<ClipEntity> = clipDao.getAllClipsList()

    suspend fun insertClips(clips: List<ClipEntity>) = clipDao.insertClips(clips)

    suspend fun deleteClipById(id: Long) = clipDao.deleteClipById(id)

    suspend fun deleteClipsByIds(ids: List<Long>) = clipDao.deleteClipsByIds(ids)

    suspend fun deleteAllClips() = clipDao.deleteAllClips()

    suspend fun getAllCachedLinkMetadata(): List<LinkMetadataEntity> =
        clipDao.getAllCachedLinkMetadata()

    suspend fun getCachedLinkMetadata(url: String): LinkMetadataEntity? =
        clipDao.getCachedLinkMetadata(url)

    suspend fun insertCachedLinkMetadata(entity: LinkMetadataEntity) =
        clipDao.insertCachedLinkMetadata(entity)

    suspend fun updateClipLinkMetadataById(
        clipId: Long,
        linkUrl: String,
        linkDomain: String,
        linkSiteName: String,
        linkTitle: String,
        linkDescription: String,
        linkFaviconBase64: String
    ) = clipDao.updateClipLinkMetadataById(
        clipId = clipId,
        linkUrl = linkUrl,
        linkDomain = linkDomain,
        linkSiteName = linkSiteName,
        linkTitle = linkTitle,
        linkDescription = linkDescription,
        linkFaviconBase64 = linkFaviconBase64
    )

    suspend fun updateClipsLinkMetadataByUrl(
        linkUrl: String,
        linkDomain: String,
        linkSiteName: String,
        linkTitle: String,
        linkDescription: String,
        linkFaviconBase64: String
    ) = clipDao.updateClipsLinkMetadataByUrl(
        linkUrl = linkUrl,
        linkDomain = linkDomain,
        linkSiteName = linkSiteName,
        linkTitle = linkTitle,
        linkDescription = linkDescription,
        linkFaviconBase64 = linkFaviconBase64
    )

    suspend fun getDeletedClip(hash: String): DeletedClipEntity? = clipDao.getDeletedClip(hash)

    suspend fun insertDeletedClip(deletedClip: DeletedClipEntity) =
        clipDao.insertDeletedClip(deletedClip)

    suspend fun insertDeletedClips(deletedClips: List<DeletedClipEntity>) =
        clipDao.insertDeletedClips(deletedClips)

    suspend fun deleteDeletedClip(hash: String) = clipDao.deleteDeletedClip(hash)

    suspend fun deleteDeletedClips(hashes: List<String>) = clipDao.deleteDeletedClips(hashes)

    suspend fun pruneOldDeletedClips(cutoffTime: Long) = clipDao.pruneOldDeletedClips(cutoffTime)

    suspend fun deleteAllDeletedClips() = clipDao.deleteAllDeletedClips()
}
