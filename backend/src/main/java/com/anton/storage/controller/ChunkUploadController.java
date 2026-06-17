package com.anton.storage.controller;

import com.anton.storage.dto.Dto;
import com.anton.storage.service.ChunkUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class ChunkUploadController {

    private final ChunkUploadService chunkService;

    /** Step 1: initialise session */
    @PostMapping("/init")
    public ResponseEntity<Dto.ChunkUploadResponse> init(
            Authentication auth, @RequestBody Dto.InitChunkRequest req) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chunkService.initUpload(userId(auth), req));
    }

    /** Step 2: upload one chunk */
    @PutMapping("/{uploadId}/chunks/{index}")
    public ResponseEntity<Dto.ChunkUploadResponse> uploadChunk(
            Authentication auth,
            @PathVariable UUID uploadId,
            @PathVariable int index,
            @RequestPart("chunk") MultipartFile chunk) throws IOException {
        return ResponseEntity.ok(chunkService.uploadChunk(userId(auth), uploadId, index, chunk.getInputStream()));
    }

    /** Step 3: finalise */
    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<Dto.FileResponse> complete(
            Authentication auth, @PathVariable UUID uploadId) throws IOException {
        return ResponseEntity.ok(chunkService.completeUpload(userId(auth), uploadId));
    }

    /** Resume: get already-received chunks */
    @GetMapping("/{uploadId}/status")
    public ResponseEntity<Dto.ChunkUploadResponse> status(
            Authentication auth, @PathVariable UUID uploadId) {
        return ResponseEntity.ok(chunkService.getStatus(userId(auth), uploadId));
    }

    private UUID userId(Authentication auth) { return (UUID) auth.getPrincipal(); }
}
