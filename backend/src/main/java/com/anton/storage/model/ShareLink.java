package com.anton.storage.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "share_links")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String token;

    @Column(nullable = false)
    private UUID fileId;

    @Column(nullable = false)
    private UUID createdBy;

    private Instant expiresAt;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private int downloadCount = 0;

    @CreationTimestamp
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Permission permission = Permission.READ;

    public enum Permission { READ, WRITE }
}
