package com.anton.storage.repository;

import com.anton.storage.model.ChunkUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChunkUploadRepository extends JpaRepository<ChunkUpload, UUID> {
    List<ChunkUpload> findByOwnerIdAndStatus(UUID ownerId, ChunkUpload.UploadStatus status);
}
