package com.anton.storage.repository;

import com.anton.storage.model.FileObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileObjectRepository extends JpaRepository<FileObject, UUID> {
    List<FileObject> findByOwnerIdAndStatusOrderByVirtualPathAsc(UUID ownerId, FileObject.FileStatus status);
    Optional<FileObject> findByOwnerIdAndVirtualPath(UUID ownerId, String virtualPath);

    @Query("SELECT f FROM FileObject f WHERE f.ownerId = :ownerId AND f.virtualPath LIKE :prefix% AND f.status = 'ACTIVE'")
    List<FileObject> findByOwnerIdAndPathPrefix(UUID ownerId, String prefix);

    @Query("SELECT COALESCE(SUM(f.sizeBytes), 0) FROM FileObject f WHERE f.ownerId = :ownerId AND f.status = 'ACTIVE'")
    long sumStorageByOwner(UUID ownerId);
}
