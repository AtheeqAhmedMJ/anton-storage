package com.anton.storage.repository;

import com.anton.storage.model.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {
    Optional<ShareLink> findByToken(String token);
    List<ShareLink> findByFileIdAndActiveTrue(UUID fileId);
    List<ShareLink> findByCreatedByOrderByCreatedAtDesc(UUID userId);
}
