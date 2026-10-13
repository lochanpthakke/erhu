@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.database.usecase

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.getOrDefault
import com.lochanpthakke.empee3.core.database.dao.LearnedWatermarkDao
import com.lochanpthakke.empee3.core.database.dao.MetadataProcessingDao
import com.lochanpthakke.empee3.core.database.entity.CURRENT_METADATA_PROCESSING_VERSION
import com.lochanpthakke.empee3.core.database.entity.MetadataFailureReason
import com.lochanpthakke.empee3.core.database.entity.MetadataProcessingEntity
import com.lochanpthakke.empee3.core.database.entity.ProcessingStatus
import com.lochanpthakke.empee3.core.model.MetadataFixAction
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.network.cleaner.FilenameCleaner
import com.lochanpthakke.empee3.core.network.repository.MetadataRepository
import com.lochanpthakke.empee3.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.UNCERTAIN_THRESHOLD
import com.lochanpthakke.empee3.core.network.tagger.TagWriteRequest
import com.lochanpthakke.empee3.core.network.tagger.FileRenamer
import com.lochanpthakke.empee3.core.network.cleaner.TitleCandidateGenerator
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessSingleTrackUseCase
    @Inject
    constructor(
        private val metadataRepository: MetadataRepository,
        private val metadataProcessingDao: MetadataProcessingDao,
        private val learnedWatermarkDao: LearnedWatermarkDao,
        private val filenameCleaner: FilenameCleaner,
        private val fileRenamer: FileRenamer,
        private val titleCandidateGenerator: TitleCandidateGenerator,
    ) {
        suspend operator fun invoke(
            song: Song,
            filePath: String,
            forceScan: Boolean = false,
            customBlocklist: List<String> = emptyList(),
            sourceProvider: String = "ALL",
            fixAction: MetadataFixAction = MetadataFixAction.ALL_FIELDS,
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
            val activeLearnedTokens = learnedWatermarkDao.getAllActiveTokensSync()
            val cleanerResult = filenameCleaner.cleanFilenameAndExtractJunk(rawFilename, customBlocklist + activeLearnedTokens)
            
            // Auto-persist extracted junk tokens from filename cleaning
            cleanerResult.extractedJunkTokens.forEach { junkToken ->
                learnedWatermarkDao.insertOrIncrement(junkToken)
            }

            val searchTitle = cleanerResult.cleaned.title.ifBlank { song.title }
            val searchArtist = cleanerResult.cleaned.artistGuess ?: song.artist.takeIf { it != "Unknown Artist" }

            val candidateTitles = titleCandidateGenerator.generateCandidates(rawFilename, customBlocklist + activeLearnedTokens)
            var bestCandidate: com.lochanpthakke.empee3.core.model.MetadataCandidate? = null
            var successfulTitleQuery: String? = null

            for (queryTitle in candidateTitles) {
                val queryArtist = if (!searchArtist.isNullOrBlank() && queryTitle.equals(searchArtist, ignoreCase = true)) null else searchArtist
                val searchResult =
                    metadataRepository.searchAndScoreTrack(
                        title = queryTitle,
                        artist = queryArtist,
                        album = song.album,
                        durationMs = song.durationMs,
                        sourceProvider = sourceProvider,
                    )

                val candidates = searchResult.getOrDefault(emptyList())
                val top = candidates.firstOrNull()
                if (top != null) {
                    if (bestCandidate == null || top.confidenceScore > bestCandidate.confidenceScore) {
                        bestCandidate = top
                        successfulTitleQuery = queryTitle
                    }
                    if (top.confidenceScore >= HIGH_CONFIDENCE_THRESHOLD) {
                        break // Early exit as soon as high confidence match >= 0.85 is found!
                    }
                }
            }

            val record =
                if (bestCandidate != null && bestCandidate.confidenceScore >= HIGH_CONFIDENCE_THRESHOLD) {
                    val writeRequest =
                        TagWriteRequest(
                            contentUri = song.uri,
                            filePath = filePath,
                            title = bestCandidate.title.takeIf { fixAction == MetadataFixAction.ALL_FIELDS || fixAction == MetadataFixAction.TITLE_ONLY },
                            artist = bestCandidate.artist.takeIf { fixAction == MetadataFixAction.ALL_FIELDS || fixAction == MetadataFixAction.ARTIST_ONLY },
                            album = bestCandidate.album.takeIf { fixAction == MetadataFixAction.ALL_FIELDS || fixAction == MetadataFixAction.ALBUM_ONLY },
                            bpm = bestCandidate.bpm.takeIf { fixAction == MetadataFixAction.ALL_FIELDS || fixAction == MetadataFixAction.BPM_ONLY },
                        )
                    val writeResult = metadataRepository.writeTagsToFile(writeRequest)

                    if (writeResult is AppResult.Success<*>) {
                        // Extract title difference between raw local title and verified clean API title
                        extractAndLearnJunkFromTitles(song.title, bestCandidate.title)

                        val ext = file.extension.ifBlank { "mp3" }
                        val targetFileName = fileRenamer.buildTargetFileName(bestCandidate.title, ext)
                        val renameResult = fileRenamer.renameAudioFile(song.uri, filePath, targetFileName)
                        val finalFilePath = (renameResult as? AppResult.Success)?.data?.newFilePath ?: filePath
                        val finalFile = File(finalFilePath)

                        MetadataProcessingEntity(
                            filePath = finalFilePath,
                            fileSize = finalFile.length(),
                            dateModified = finalFile.lastModified(),
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

        private suspend fun extractAndLearnJunkFromTitles(
            rawTitle: String,
            cleanTitle: String,
        ) {
            if (rawTitle.equals(cleanTitle, ignoreCase = true)) return
            var remainder = rawTitle
            cleanTitle.split(" ").forEach { word ->
                if (word.length >= 3) {
                    remainder = remainder.replace(word, "", ignoreCase = true)
                }
            }
            remainder = remainder.replace('_', ' ').replace('-', ' ').trim()
            remainder.split(" ", "(", ")", "[", "]").forEach { token ->
                val cleanedToken = token.trim(' ', '.', '-', '_', '[', ']', '(', ')')
                if (cleanedToken.length >= 3 && !cleanedToken.equals(cleanTitle, ignoreCase = true)) {
                    learnedWatermarkDao.insertOrIncrement(cleanedToken)
                }
            }
        }
    }
