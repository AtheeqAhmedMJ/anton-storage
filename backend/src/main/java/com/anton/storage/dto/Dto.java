package com.anton.storage.dto;

import com.anton.storage.model.FileObject;
import com.anton.storage.model.FileVersion;
import com.anton.storage.model.ShareLink;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

// ─── Auth ─────────────────────────────────────────────────────────────────────
public class Dto {

    @Data
    public static class RegisterRequest {
        private String username;
        private String email;
        private String password;
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Builder
    @Data
    public static class AuthResponse {
        private String token;
        private String username;
        private String email;
        private UUID userId;
    }

    // ─── File ─────────────────────────────────────────────────────────────────

    @Builder
    @Data
    public static class FileResponse {
        private UUID id;
        private String filename;
        private String virtualPath;
        private String contentType;
        private long sizeBytes;
        private String sizeHuman;
        private int currentVersion;
        private FileObject.FileStatus status;
        private Instant createdAt;
        private Instant updatedAt;
        private String checksum;
    }

    @Builder
    @Data
    public static class VersionResponse {
        private UUID id;
        private int versionNumber;
        private long sizeBytes;
        private String checksum;
        private String comment;
        private Instant createdAt;
        private UUID uploadedBy;
    }

    // ─── Share ────────────────────────────────────────────────────────────────

    @Data
    public static class ShareRequest {
        private UUID fileId;
        private Instant expiresAt;
        private ShareLink.Permission permission;
    }

    @Builder
    @Data
    public static class ShareResponse {
        private UUID id;
        private String token;
        private UUID fileId;
        private String filename;
        private Instant expiresAt;
        private int downloadCount;
        private boolean active;
        private Instant createdAt;
    }

    // ─── Chunked Upload ───────────────────────────────────────────────────────

    @Data
    public static class InitChunkRequest {
        private String filename;
        private String contentType;
        private long totalSizeBytes;
        private int totalChunks;
        private String destinationPath;
    }

    @Builder
    @Data
    public static class ChunkUploadResponse {
        private UUID uploadId;
        private String filename;
        private int totalChunks;
        private String receivedChunks;
        private String status;
    }

    // ─── Storage Stats ────────────────────────────────────────────────────────

    @Builder
    @Data
    public static class StorageStats {
        private long usedBytes;
        private long limitBytes;
        private double percentUsed;
        private String usedHuman;
        private String limitHuman;
        private long fileCount;
    }
}
