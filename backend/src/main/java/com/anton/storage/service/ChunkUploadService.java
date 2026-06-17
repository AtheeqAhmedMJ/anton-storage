package com.anton.storage.service;

import com.anton.storage.config.StorageEngine;
import com.anton.storage.dto.Dto;
import com.anton.storage.model.ChunkUpload;
import com.anton.storage.model.FileObject;
import com.anton.storage.model.FileVersion;
import com.anton.storage.repository.ChunkUploadRepository;
import com.anton.storage.repository.FileObjectRepository;
import com.anton.storage.repository.FileVersionRepository;
import com.anton.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkUploadService {

    private final ChunkUploadRepository chunkRepo;
    private final FileObjectRepository fileRepo;
    private final FileVersionRepository versionRepo;
    private final UserRepository userRepo;
    private final StorageEngine storage;

    /** Initialise a multipart upload session. */
    @Transactional
    public Dto.ChunkUploadResponse initUpload(UUID ownerId, Dto.InitChunkRequest req) throws IOException {
        Path tempDir = storage.createTempDir(ownerId + "-" + UUID.randomUUID());
        ChunkUpload session = chunkRepo.save(ChunkUpload.builder()
                .ownerId(ownerId)
                .filename(req.getFilename())
                .contentType(req.getContentType())
                .totalSizeBytes(req.getTotalSizeBytes())
                .totalChunks(req.getTotalChunks())
                .receivedChunks("")
                .tempDir(tempDir.toString())
                .destinationPath(req.getDestinationPath())
                .build());

        return toResponse(session);
    }

    /** Upload a single chunk (0-indexed). */
    @Transactional
    public Dto.ChunkUploadResponse uploadChunk(UUID ownerId, UUID uploadId,
                                                int chunkIndex, InputStream data) throws IOException {
        ChunkUpload session = getOwned(ownerId, uploadId);
        storage.writeChunk(Path.of(session.getTempDir()), chunkIndex, data);

        Set<Integer> received = parseReceived(session.getReceivedChunks());
        received.add(chunkIndex);
        session.setReceivedChunks(received.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")));
        chunkRepo.save(session);

        return toResponse(session);
    }

    /** Complete: assemble all chunks, create FileObject record. */
    @Transactional
    public Dto.FileResponse completeUpload(UUID ownerId, UUID uploadId) throws IOException {
        ChunkUpload session = getOwned(ownerId, uploadId);
        Set<Integer> received = parseReceived(session.getReceivedChunks());

        if (received.size() != session.getTotalChunks())
            throw new IllegalStateException("Missing chunks: expected " + session.getTotalChunks()
                    + " received " + received.size());

        String virtualPath = buildVirtualPath(session.getDestinationPath(), session.getFilename());
        String storageKey  = ownerId + virtualPath + ".v1";

        StorageEngine.WriteResult result = storage.assembleChunks(
                Path.of(session.getTempDir()), session.getTotalChunks(), storageKey);

        FileObject obj = fileRepo.save(FileObject.builder()
                .ownerId(ownerId)
                .virtualPath(virtualPath)
                .filename(session.getFilename())
                .contentType(session.getContentType())
                .sizeBytes(result.sizeBytes())
                .storagePath(result.path())
                .checksum(result.checksum())
                .currentVersion(1)
                .status(FileObject.FileStatus.ACTIVE)
                .build());

        versionRepo.save(FileVersion.builder()
                .fileId(obj.getId()).versionNumber(1)
                .storagePath(result.path())
                .sizeBytes(result.sizeBytes())
                .checksum(result.checksum())
                .uploadedBy(ownerId)
                .build());

        session.setStatus(ChunkUpload.UploadStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
        chunkRepo.save(session);

        userRepo.findById(ownerId).ifPresent(u -> {
            u.setStorageUsedBytes(u.getStorageUsedBytes() + result.sizeBytes());
            userRepo.save(u);
        });

        return com.anton.storage.dto.Dto.FileResponse.builder()
                .id(obj.getId()).filename(obj.getFilename())
                .virtualPath(obj.getVirtualPath()).contentType(obj.getContentType())
                .sizeBytes(obj.getSizeBytes()).currentVersion(1)
                .status(obj.getStatus()).checksum(obj.getChecksum())
                .createdAt(obj.getCreatedAt()).updatedAt(obj.getUpdatedAt())
                .build();
    }

    /** Resume info — tells client which chunks already arrived. */
    public Dto.ChunkUploadResponse getStatus(UUID ownerId, UUID uploadId) {
        return toResponse(getOwned(ownerId, uploadId));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ChunkUpload getOwned(UUID ownerId, UUID id) {
        ChunkUpload s = chunkRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Upload session not found"));
        if (!s.getOwnerId().equals(ownerId)) throw new SecurityException("Access denied");
        return s;
    }

    private Set<Integer> parseReceived(String csv) {
        if (csv == null || csv.isBlank()) return new HashSet<>();
        return Arrays.stream(csv.split(",")).map(Integer::parseInt).collect(Collectors.toCollection(HashSet::new));
    }

    private String buildVirtualPath(String dir, String filename) {
        if (dir == null || dir.isBlank()) return "/" + filename;
        String base = dir.startsWith("/") ? dir : "/" + dir;
        return base.endsWith("/") ? base + filename : base + "/" + filename;
    }

    private Dto.ChunkUploadResponse toResponse(ChunkUpload s) {
        return Dto.ChunkUploadResponse.builder()
                .uploadId(s.getId()).filename(s.getFilename())
                .totalChunks(s.getTotalChunks()).receivedChunks(s.getReceivedChunks())
                .status(s.getStatus().name()).build();
    }
}
