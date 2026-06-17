package com.anton.storage.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks state for a multipart / chunked upload session.
 * Each chunk is written to a temp directory; on completion
 * the service assembles them into the final file.
 */
@Entity
@Table(name = "chunk_uploads")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChunkUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String filename;

    private String contentType;
    private long totalSizeBytes;
    private int totalChunks;

    /** Bitmask or comma-separated list of received chunk indices */
    @Column(length = 2000)
    private String receivedChunks;

    /** Temp directory where chunk files are written */
    @Column(nullable = false)
    private String tempDir;

    /** Destination virtual path */
    private String destinationPath;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UploadStatus status = UploadStatus.IN_PROGRESS;

    @CreationTimestamp
    private Instant createdAt;

    private Instant completedAt;

    public enum UploadStatus { IN_PROGRESS, COMPLETED, FAILED }
}
