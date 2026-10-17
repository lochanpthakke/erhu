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
import com.lochanpthakke.empee3.core.network.cleaner.TitleCandidateGenerator
import com.lochanpthakke.empee3.core.network.repository.MetadataRepository
import com.lochanpthakke.empee3.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.UNCERTAIN_THRESHOLD
import com.lochanpthakke.empee3.core.network.tagger.FileRenamer
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
            val rawArtist = cleanerResult.cleaned.artistGuess ?: song.artist.takeIf { it != "Unknown Artist" }
            val searchArtist =
                com.lochanpthakke.empee3.core.database.library.PrimaryArtistExtractor
                    .extractPrimaryArtist(rawArtist)

            val candidateTitles =
                titleCandidateGenerator.generateCandidates(
                    song.title.ifBlank { rawFilename },
                    customBlocklist + activeLearnedTokens,
                )
            var bestCandidate: com.lochanpthakke.empee3.core.model.MetadataCandidate? = null
            var successfulTitleQuery: String? = null

            for (index in candidateTitles.indices) {
                val queryTitle = candidateTitles[index]
                var queryArtist =
                    if (!searchArtist.isNullOrBlank() &&
                        queryTitle.equals(searchArtist, ignoreCase = true)
                    ) {
                        null
                    } else {
                        searchArtist
                    }
                var searchResult =
                    metadataRepository.searchAndScoreTrack(
                        title = queryTitle,
                        artist = queryArtist,
                        album = song.album,
                        durationMs = song.durationMs,
                        sourceProvider = sourceProvider,
                    )

                var candidates = searchResult.getOrDefault(emptyList())
                if (candidates.isEmpty() && queryArtist != null) {
                    queryArtist = null
                    searchResult =
                        metadataRepository.searchAndScoreTrack(
                            title = queryTitle,
                            artist = null,
                            album = song.album,
                            durationMs = song.durationMs,
                            sourceProvider = sourceProvider,
                        )
                    candidates = searchResult.getOrDefault(emptyList())
                }

                if (candidates.isNotEmpty()) {
                    val top = candidates.first()
                    if (bestCandidate == null || top.confidenceScore >= bestCandidate!!.confidenceScore + 0.08f) {
                        bestCandidate = top
                        successfulTitleQuery = if (index > 0) "$queryTitle (Attempt ${index + 1})" else queryTitle
                    }
                    if (bestCandidate!!.confidenceScore >= HIGH_CONFIDENCE_THRESHOLD) {
                        break
                    }
                }
            }

            val record =
                if (bestCandidate != null && bestCandidate.confidenceScore >= HIGH_CONFIDENCE_THRESHOLD) {
                    val writeRequest =
                        TagWriteRequest(
                            contentUri = song.uri,
                            filePath = filePath,
                            title =
                                bestCandidate.title.takeIf {
                                    fixAction == MetadataFixAction.ALL_FIELDS ||
                                        fixAction == MetadataFixAction.TITLE_ONLY
                                },
                            artist =
                                bestCandidate.artist.takeIf {
                                    fixAction == MetadataFixAction.ALL_FIELDS ||
                                        fixAction == MetadataFixAction.ARTIST_ONLY
                                },
                            album =
                                bestCandidate.album.takeIf {
                                    fixAction == MetadataFixAction.ALL_FIELDS ||
                                        fixAction == MetadataFixAction.ALBUM_ONLY
                                },
                            bpm =
                                bestCandidate.bpm.takeIf {
                                    fixAction == MetadataFixAction.ALL_FIELDS ||
                                        fixAction == MetadataFixAction.BPM_ONLY
                                },
                        )
                    val writeResult =
                        if (writeRequest.hasChanges) {
                            metadataRepository.writeTagsToFile(
                                writeRequest,
                            )
                        } else {
                            AppResult.Success(Unit)
                        }

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
            val featRegex = Regex("(?i)[(\\[]?(feat|ft|featuring|with)\\.?\\s+[^)\\]]+[)\\]]?")
            val titleWithoutFeat = rawTitle.replace(featRegex, "").trim()

            var remainder = titleWithoutFeat
            cleanTitle.split(" ").forEach { word ->
                if (word.length >= 3) {
                    remainder = remainder.replace(word, "", ignoreCase = true)
                }
            }
            remainder = remainder.replace('_', ' ').replace('-', ' ').trim()
            val ignoredWords =
                setOf(
                    "feat",
                    "featuring",
                    "with",
                    "official",
                    "video",
                    "audio",
                    "radio",
                    "edit",
                    "live",
                    "remastered",
                    "single",
                    "album",
                    "version",
                    "clean",
                    "explicit",
                    "deluxe",
                )
            remainder.split(" ", "(", ")", "[", "]").forEach { token ->
                val cleanedToken = token.trim(' ', '.', '-', '_', '[', ']', '(', ')')
                if (cleanedToken.length >= 4 &&
                    !cleanedToken.equals(cleanTitle, ignoreCase = true) &&
                    !ignoredWords.contains(cleanedToken.lowercase())
                ) {
                    learnedWatermarkDao.insertOrIncrement(cleanedToken)
                }
            }
        }
    }
