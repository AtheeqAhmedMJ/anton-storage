package com.anton.storage.controller;

import com.anton.storage.dto.Dto;
import com.anton.storage.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    // ─── Authenticated ────────────────────────────────────────────────────────

    @PostMapping("/api/share")
    public ResponseEntity<Dto.ShareResponse> createLink(
            Authentication auth, @RequestBody Dto.ShareRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shareService.createLink(userId(auth), req));
    }

    @GetMapping("/api/share")
    public ResponseEntity<List<Dto.ShareResponse>> myLinks(Authentication auth) {
        return ResponseEntity.ok(shareService.myLinks(userId(auth)));
    }

    @DeleteMapping("/api/share/{id}")
    public ResponseEntity<Void> revoke(Authentication auth, @PathVariable UUID id) {
        shareService.revokeLink(userId(auth), id);
        return ResponseEntity.noContent().build();
    }

    // ─── Public (no auth) ─────────────────────────────────────────────────────

    @GetMapping("/api/share/public/{token}/info")
    public ResponseEntity<Dto.ShareResponse> linkInfo(@PathVariable String token) {
        return ResponseEntity.ok(shareService.getLinkInfo(token));
    }

    @GetMapping("/api/share/public/{token}/download")
    public ResponseEntity<InputStreamResource> downloadShared(@PathVariable String token) throws IOException {
        Dto.ShareResponse info = shareService.getLinkInfo(token);
        var stream = shareService.downloadShared(token);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + info.getFilename() + "\"")
                .body(new InputStreamResource(stream));
    }

    private UUID userId(Authentication auth) { return (UUID) auth.getPrincipal(); }
}
