package com.anton.storage.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @CreationTimestamp
    private Instant createdAt;

    @Column(nullable = false)
    @Builder.Default
    private long storageUsedBytes = 0L;

    @Column(nullable = false)
    @Builder.Default
    private long storageLimitBytes = 10L * 1024 * 1024 * 1024; // 10 GB default
}
