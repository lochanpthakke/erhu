@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.database.usecase

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.getOrDefault
import com.lochanpthakke.empee3.core.database.dao.MetadataProcessingDao
import com.lochanpthakke.empee3.core.database.entity.CURRENT_METADATA_PROCESSING_VERSION
import com.lochanpthakke.empee3.core.database.entity.MetadataFailureReason
import com.lochanpthakke.empee3.core.database.entity.MetadataProcessingEntity
import com.lochanpthakke.empee3.core.database.entity.ProcessingStatus
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.network.repository.MetadataRepository
import com.lochanpthakke.empee3.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.UNCERTAIN_THRESHOLD
import com.lochanpthakke.empee3.core.network.tagger.TagWriteRequest
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessSingleTrackUseCase
    @Inject
    constructor(
        private val metadataRepository: MetadataRepository,
        private val metadataProcessingDao: MetadataProcessingDao,
    ) {
        suspend operator fun invoke(
            song: Song,
            filePath: String,
            forceScan: Boolean = false,
            customBlocklist: List<String> = emptyList(),
        ): MetadataProcessingEntity {
            val file = File(filePath)
            if (!file.exists()) {
                val failedEntity =
                    MetadataProcessingEntity(
                        filePath = filePath,
                        fileSize = 0L,
                        dateModified = 0L,
                        processingVersion = CURRENT_METADATA_PROCESSING_VERSION,
                        status = ProcessingStatus.FAILED.name,
                        lastProcessedTimestamp = System.currentTimeMillis(),
                        failureReason = MetadataFailureReason.FILE_NOT_FOUND.name,
                    )
                metadataProcessingDao.upsertProcessingRecord(failedEntity)
                return failedEntity
            }

            val fileSize = file.length()
            val dateModified = file.lastModified()

            if (!forceScan) {
                val existingRecord = metadataProcessingDao.getProcessingRecord(filePath)
                if (existingRecord != null &&
                    existingRecord.status == ProcessingStatus.SUCCESS.name &&
                    existingRecord.processingVersion == CURRENT_METADATA_PROCESSING_VERSION &&
                    existingRecord.dateModified == dateModified &&
                    existingRecord.fileSize == fileSize
                ) {
                    return existingRecord
                }
            }

            val rawFilename = file.name
            val cleaned = metadataRepository.cleanFilename(rawFilename, customBlocklist)
            val searchTitle = cleaned.title.ifBlank { song.title }
            val searchArtist = cleaned.artistGuess ?: song.artist.takeIf { it != "Unknown Artist" }

            val searchResult =
                metadataRepository.searchAndScoreTrack(
                    title = searchTitle,
                    artist = searchArtist,
                    durationMs = song.durationMs,
                )

            val candidates = searchResult.getOrDefault(emptyList())
            val bestCandidate = candidates.firstOrNull()

            val record =
                if (bestCandidate != null && bestCandidate.confidenceScore >= HIGH_CONFIDENCE_THRESHOLD) {
                    val writeRequest =
                        TagWriteRequest(
                            filePath = filePath,
                            title = bestCandidate.title,
                            artist = bestCandidate.artist,
                            album = bestCandidate.album,
                        )
                    val writeResult = metadataRepository.writeTagsToFile(writeRequest)

                    if (writeResult is AppResult.Success<*>) {
                        MetadataProcessingEntity(
                            filePath = filePath,
                            fileSize = file.length(),
                            dateModified = file.lastModified(),
                            processingVersion = CURRENT_METADATA_PROCESSING_VERSION,
                            status = ProcessingStatus.SUCCESS.name,
                            lastProcessedTimestamp = System.currentTimeMillis(),
                            confidenceScore = bestCandidate.confidenceScore,
                            matchedSource = bestCandidate.sourceName,
                        )
                    } else {
                        MetadataProcessingEntity(
                            filePath = filePath,
                            fileSize = fileSize,
                            dateModified = dateModified,
                            processingVersion = CURRENT_METADATA_PROCESSING_VERSION,
                            status = ProcessingStatus.FAILED.name,
                            lastProcessedTimestamp = System.currentTimeMillis(),
                            confidenceScore = bestCandidate.confidenceScore,
                            matchedSource = bestCandidate.sourceName,
                            failureReason = MetadataFailureReason.WRITE_ERROR.name,
                        )
                    }
                } else if (bestCandidate != null && bestCandidate.confidenceScore >= UNCERTAIN_THRESHOLD) {
                    MetadataProcessingEntity(
                        filePath = filePath,
                        fileSize = fileSize,
                        dateModified = dateModified,
                        processingVersion = CURRENT_METADATA_PROCESSING_VERSION,
                        status = ProcessingStatus.NEEDS_REVIEW.name,
                        lastProcessedTimestamp = System.currentTimeMillis(),
                        confidenceScore = bestCandidate.confidenceScore,
                        matchedSource = bestCandidate.sourceName,
                        failureReason = MetadataFailureReason.LOW_CONFIDENCE.name,
                    )
                } else {
                    MetadataProcessingEntity(
                        filePath = filePath,
                        fileSize = fileSize,
                        dateModified = dateModified,
                        processingVersion = CURRENT_METADATA_PROCESSING_VERSION,
                        status = ProcessingStatus.FAILED.name,
                        lastProcessedTimestamp = System.currentTimeMillis(),
                        confidenceScore = bestCandidate?.confidenceScore ?: 0.0f,
                        matchedSource = bestCandidate?.sourceName,
                        failureReason = MetadataFailureReason.NO_MATCH.name,
                    )
                }

            metadataProcessingDao.upsertProcessingRecord(record)
            return record
        }
    }
