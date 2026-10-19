package com.zahidcodes.copynets.data

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
}
