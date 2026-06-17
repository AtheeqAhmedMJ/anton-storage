package com.anton.storage.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;

/**
 * StorageEngine abstracts the physical file I/O.
 * Today: local disk.  Tomorrow: swap for S3Client / MinIO.
 */
@Slf4j
@Component
public class StorageEngine {

    private final Path rootDir;

    public StorageEngine(@Value("${storage.root-dir}") String rootDir) throws IOException {
        this.rootDir = Path.of(rootDir);
        Files.createDirectories(this.rootDir);
        log.info("Storage root: {}", this.rootDir.toAbsolutePath());
    }

    /** Write bytes to a logical key; returns the physical path and SHA-256 checksum. */
    public WriteResult write(String key, InputStream data) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());

        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (Exception e) { throw new IOException("SHA-256 unavailable", e); }

        long size;
        try (DigestInputStream dis = new DigestInputStream(data, digest);
             OutputStream out = Files.newOutputStream(target)) {
            size = dis.transferTo(out);
        }

        String checksum = HexFormat.of().formatHex(digest.digest());
        return new WriteResult(target.toString(), size, checksum);
    }

    public InputStream read(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    public long size(String key) throws IOException {
        return Files.size(resolve(key));
    }

    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    public Path createTempDir(String prefix) throws IOException {
        Path tmp = rootDir.resolve("_chunks").resolve(prefix);
        Files.createDirectories(tmp);
        return tmp;
    }

    public void writeChunk(Path tempDir, int index, InputStream data) throws IOException {
        Path chunkFile = tempDir.resolve(String.format("chunk_%05d", index));
        try (OutputStream out = Files.newOutputStream(chunkFile)) {
            data.transferTo(out);
        }
    }

    /** Assemble ordered chunks into target key. */
    public WriteResult assembleChunks(Path tempDir, int totalChunks, String targetKey) throws IOException {
        Path target = resolve(targetKey);
        Files.createDirectories(target.getParent());

        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (Exception e) { throw new IOException(e); }

        long size = 0;
        try (OutputStream out = Files.newOutputStream(target)) {
            for (int i = 0; i < totalChunks; i++) {
                Path chunk = tempDir.resolve(String.format("chunk_%05d", i));
                byte[] bytes = Files.readAllBytes(chunk);
                digest.update(bytes);
                out.write(bytes);
                size += bytes.length;
            }
        }

        // Cleanup temp chunks
        try (var s = Files.walk(tempDir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }

        return new WriteResult(target.toString(), size, HexFormat.of().formatHex(digest.digest()));
    }

    private Path resolve(String key) {
        // Prevent path traversal
        String safe = key.replaceAll("\\.\\.", "").replaceAll("^/+", "");
        return rootDir.resolve(safe).normalize();
    }

    public record WriteResult(String path, long sizeBytes, String checksum) {}
}
