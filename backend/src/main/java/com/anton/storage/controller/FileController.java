package com.anton.storage.controller;

import com.anton.storage.dto.Dto;
import com.anton.storage.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Dto.FileResponse> upload(
            Authentication auth,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "") String path) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fileService.upload(userId(auth), file, path));
    }

    @GetMapping
    public ResponseEntity<List<Dto.FileResponse>> list(Authentication auth) {
        return ResponseEntity.ok(fileService.list(userId(auth)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Dto.FileResponse> metadata(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(fileService.getMetadata(userId(auth), id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(
            Authentication auth,
            @PathVariable UUID id,
            @RequestParam(required = false) Integer version) throws IOException {
        var stream = fileService.download(userId(auth), id, version);
        Dto.FileResponse meta = fileService.getMetadata(userId(auth), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        meta.getContentType() != null ? meta.getContentType() : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + meta.getFilename() + "\"")
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<Dto.VersionResponse>> versions(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(fileService.listVersions(userId(auth), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
        fileService.delete(userId(auth), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<Dto.StorageStats> stats(Authentication auth) {
        return ResponseEntity.ok(fileService.stats(userId(auth)));
    }

    private UUID userId(Authentication auth) {
        return (UUID) auth.getPrincipal();
    }
}
