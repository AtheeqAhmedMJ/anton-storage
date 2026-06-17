package com.anton.storage.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_versions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FileVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID fileId;

    @Column(nullable = false)
    private int versionNumber;

    @Column(nullable = false)
    private String storagePath;

    private long sizeBytes;
    private String checksum;
    private String comment;

    @CreationTimestamp
    private Instant createdAt;

    private UUID uploadedBy;
}
