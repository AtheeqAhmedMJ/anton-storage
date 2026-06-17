package com.anton.storage.service;

import com.anton.storage.config.StorageEngine;
import com.anton.storage.dto.Dto;
import com.anton.storage.model.FileObject;
import com.anton.storage.model.FileVersion;
import com.anton.storage.model.User;
import com.anton.storage.repository.FileObjectRepository;
import com.anton.storage.repository.FileVersionRepository;
import com.anton.storage.repository.UserRepository;
import com.anton.storage.util.SizeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileObjectRepository fileRepo;
    private final FileVersionRepository versionRepo;
    private final UserRepository userRepo;
    private final StorageEngine storage;

    // ─── Upload ───────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "file-list", key = "#ownerId")
    public Dto.FileResponse upload(UUID ownerId, MultipartFile file, String destinationPath)
            throws IOException {

        String virtualPath = buildVirtualPath(destinationPath, file.getOriginalFilename());
        String storageKey = buildStorageKey(ownerId, virtualPath, 1);

        StorageEngine.WriteResult result = storage.write(storageKey, file.getInputStream());
        enforceStorageQuota(ownerId, result.sizeBytes());

        FileObject existing = fileRepo.findByOwnerIdAndVirtualPath(ownerId, virtualPath).orElse(null);

        if (existing != null) {
            // New version of existing file
            return newVersion(existing, ownerId, file, result);
        }

        // Brand new file
        FileObject obj = fileRepo.save(FileObject.builder()
                .ownerId(ownerId)
                .virtualPath(virtualPath)
                .filename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeBytes(result.sizeBytes())
                .storagePath(result.path())
                .checksum(result.checksum())
                .currentVersion(1)
                .status(FileObject.FileStatus.ACTIVE)
                .build());

        versionRepo.save(FileVersion.builder()
                .fileId(obj.getId())
                .versionNumber(1)
                .storagePath(result.path())
                .sizeBytes(result.sizeBytes())
                .checksum(result.checksum())
                .uploadedBy(ownerId)
                .build());

        updateStorageUsed(ownerId, result.sizeBytes());
        return toResponse(obj);
    }

    private Dto.FileResponse newVersion(FileObject existing, UUID ownerId,
                                         MultipartFile file, StorageEngine.WriteResult result) throws IOException {
        int nextVer = existing.getCurrentVersion() + 1;
        String newKey = buildStorageKey(ownerId, existing.getVirtualPath(), nextVer);
        StorageEngine.WriteResult newResult = storage.write(newKey, file.getInputStream());

        long delta = newResult.sizeBytes() - existing.getSizeBytes();

        existing.setCurrentVersion(nextVer);
        existing.setStoragePath(newResult.path());
        existing.setSizeBytes(newResult.sizeBytes());
        existing.setChecksum(newResult.checksum());
        fileRepo.save(existing);

        versionRepo.save(FileVersion.builder()
                .fileId(existing.getId())
                .versionNumber(nextVer)
                .storagePath(newResult.path())
                .sizeBytes(newResult.sizeBytes())
                .checksum(newResult.checksum())
                .uploadedBy(ownerId)
                .build());

        updateStorageUsed(ownerId, delta);
        return toResponse(existing);
    }

    // ─── Download ─────────────────────────────────────────────────────────────

    public InputStream download(UUID ownerId, UUID fileId, Integer version) throws IOException {
        FileObject file = getOwned(ownerId, fileId);
        if (version == null) {
            return storage.read(file.getStoragePath());
        }
        FileVersion ver = versionRepo.findByFileIdAndVersionNumber(fileId, version)
                .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        return storage.read(ver.getStoragePath());
    }

    // ─── List / Metadata ──────────────────────────────────────────────────────

    @Cacheable(value = "file-list", key = "#ownerId")
    public List<Dto.FileResponse> list(UUID ownerId) {
        return fileRepo.findByOwnerIdAndStatusOrderByVirtualPathAsc(ownerId, FileObject.FileStatus.ACTIVE)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public Dto.FileResponse getMetadata(UUID ownerId, UUID fileId) {
        return toResponse(getOwned(ownerId, fileId));
    }

    public List<Dto.VersionResponse> listVersions(UUID ownerId, UUID fileId) {
        getOwned(ownerId, fileId); // ownership check
        return versionRepo.findByFileIdOrderByVersionNumberDesc(fileId)
                .stream().map(this::toVersionResponse).collect(Collectors.toList());
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "file-list", key = "#ownerId")
    public void delete(UUID ownerId, UUID fileId) {
        FileObject file = getOwned(ownerId, fileId);
        file.setStatus(FileObject.FileStatus.DELETED);
        fileRepo.save(file);
        updateStorageUsed(ownerId, -file.getSizeBytes());
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

    public Dto.StorageStats stats(UUID ownerId) {
        User user = userRepo.findById(ownerId).orElseThrow();
        long used = fileRepo.sumStorageByOwner(ownerId);
        long count = fileRepo.findByOwnerIdAndStatusOrderByVirtualPathAsc(ownerId, FileObject.FileStatus.ACTIVE).size();

        return Dto.StorageStats.builder()
                .usedBytes(used)
                .limitBytes(user.getStorageLimitBytes())
                .percentUsed(used * 100.0 / user.getStorageLimitBytes())
                .usedHuman(SizeFormatter.format(used))
                .limitHuman(SizeFormatter.format(user.getStorageLimitBytes()))
                .fileCount(count)
                .build();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private FileObject getOwned(UUID ownerId, UUID fileId) {
        FileObject f = fileRepo.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        if (!f.getOwnerId().equals(ownerId))
            throw new SecurityException("Access denied");
        if (f.getStatus() == FileObject.FileStatus.DELETED)
            throw new IllegalArgumentException("File deleted");
        return f;
    }

    private void enforceStorageQuota(UUID ownerId, long newBytes) {
        User user = userRepo.findById(ownerId).orElseThrow();
        if (user.getStorageUsedBytes() + newBytes > user.getStorageLimitBytes())
            throw new IllegalStateException("Storage quota exceeded");
    }

    private void updateStorageUsed(UUID ownerId, long delta) {
        userRepo.findById(ownerId).ifPresent(u -> {
            u.setStorageUsedBytes(Math.max(0, u.getStorageUsedBytes() + delta));
            userRepo.save(u);
        });
    }

    private String buildVirtualPath(String dir, String filename) {
        if (dir == null || dir.isBlank()) return "/" + filename;
        String base = dir.startsWith("/") ? dir : "/" + dir;
        return base.endsWith("/") ? base + filename : base + "/" + filename;
    }

    private String buildStorageKey(UUID ownerId, String virtualPath, int version) {
        return ownerId + virtualPath + ".v" + version;
    }

    private Dto.FileResponse toResponse(FileObject f) {
        return Dto.FileResponse.builder()
                .id(f.getId())
                .filename(f.getFilename())
                .virtualPath(f.getVirtualPath())
                .contentType(f.getContentType())
                .sizeBytes(f.getSizeBytes())
                .sizeHuman(SizeFormatter.format(f.getSizeBytes()))
                .currentVersion(f.getCurrentVersion())
                .status(f.getStatus())
                .checksum(f.getChecksum())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }

    private Dto.VersionResponse toVersionResponse(FileVersion v) {
        return Dto.VersionResponse.builder()
                .id(v.getId())
                .versionNumber(v.getVersionNumber())
                .sizeBytes(v.getSizeBytes())
                .checksum(v.getChecksum())
                .comment(v.getComment())
                .createdAt(v.getCreatedAt())
                .uploadedBy(v.getUploadedBy())
                .build();
    }
}
