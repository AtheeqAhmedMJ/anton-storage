package com.anton.storage.repository;

import com.anton.storage.model.FileVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {
    List<FileVersion> findByFileIdOrderByVersionNumberDesc(UUID fileId);
    Optional<FileVersion> findByFileIdAndVersionNumber(UUID fileId, int versionNumber);
}
