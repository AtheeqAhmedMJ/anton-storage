package com.anton.storage.service;

import com.anton.storage.config.StorageEngine;
import com.anton.storage.dto.Dto;
import com.anton.storage.model.FileObject;
import com.anton.storage.model.ShareLink;
import com.anton.storage.repository.FileObjectRepository;
import com.anton.storage.repository.ShareLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShareService {

    private final ShareLinkRepository shareRepo;
    private final FileObjectRepository fileRepo;
    private final StorageEngine storage;

    @Transactional
    public Dto.ShareResponse createLink(UUID ownerId, Dto.ShareRequest req) {
        FileObject file = fileRepo.findById(req.getFileId())
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        if (!file.getOwnerId().equals(ownerId)) throw new SecurityException("Access denied");

        ShareLink link = shareRepo.save(ShareLink.builder()
                .token(UUID.randomUUID().toString().replace("-", ""))
                .fileId(req.getFileId())
                .createdBy(ownerId)
                .expiresAt(req.getExpiresAt())
                .permission(req.getPermission() != null ? req.getPermission() : ShareLink.Permission.READ)
                .build());

        return toResponse(link, file);
    }

    public List<Dto.ShareResponse> myLinks(UUID ownerId) {
        return shareRepo.findByCreatedByOrderByCreatedAtDesc(ownerId).stream()
                .map(link -> {
                    FileObject file = fileRepo.findById(link.getFileId()).orElse(null);
                    return toResponse(link, file);
                }).collect(Collectors.toList());
    }

    @Transactional
    public void revokeLink(UUID ownerId, UUID linkId) {
        ShareLink link = shareRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("Link not found"));
        if (!link.getCreatedBy().equals(ownerId)) throw new SecurityException("Access denied");
        link.setActive(false);
        shareRepo.save(link);
    }

    /** Public download via share token — no auth required. */
    @Transactional
    public InputStream downloadShared(String token) throws IOException {
        ShareLink link = shareRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Link not found"));

        if (!link.isActive()) throw new IllegalStateException("Link revoked");
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(Instant.now()))
            throw new IllegalStateException("Link expired");

        FileObject file = fileRepo.findById(link.getFileId())
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        link.setDownloadCount(link.getDownloadCount() + 1);
        shareRepo.save(link);

        return storage.read(file.getStoragePath());
    }

    public Dto.ShareResponse getLinkInfo(String token) {
        ShareLink link = shareRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Link not found"));
        FileObject file = fileRepo.findById(link.getFileId()).orElse(null);
        return toResponse(link, file);
    }

    private Dto.ShareResponse toResponse(ShareLink l, FileObject f) {
        return Dto.ShareResponse.builder()
                .id(l.getId()).token(l.getToken()).fileId(l.getFileId())
                .filename(f != null ? f.getFilename() : "unknown")
                .expiresAt(l.getExpiresAt()).downloadCount(l.getDownloadCount())
                .active(l.isActive()).createdAt(l.getCreatedAt())
                .build();
    }
}
