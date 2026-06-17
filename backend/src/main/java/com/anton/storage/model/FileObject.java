package com.anton.storage.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_objects", indexes = {
        @Index(name = "idx_file_owner", columnList = "ownerId"),
        @Index(name = "idx_file_path", columnList = "ownerId,virtualPath")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FileObject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID ownerId;

    /** Logical path the user sees, e.g. /docs/report.pdf */
    @Column(nullable = false)
    private String virtualPath;

    /** Filename portion */
    @Column(nullable = false)
    private String filename;

    /** MIME type */
    private String contentType;

    /** Size in bytes */
    private long sizeBytes;

    /** Physical path on disk (or S3 key in future) */
    @Column(nullable = false)
    private String storagePath;

    /** Checksum for integrity */
    private String checksum;

    /** Current version number (1-based) */
    @Column(nullable = false)
    @Builder.Default
    private int currentVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FileStatus status = FileStatus.ACTIVE;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public enum FileStatus { ACTIVE, DELETED, UPLOADING }
}
