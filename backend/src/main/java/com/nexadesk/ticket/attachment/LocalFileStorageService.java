package com.nexadesk.ticket.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Stores files under a root directory (a Docker volume in production). Keys are generated server-side;
 * every resolved path is checked to stay inside the root, so no key can escape it ("../").
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path root;

    public LocalFileStorageService(@Value("${nexadesk.storage.location}") Path location) throws IOException {
        this.root = location.toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public StoredFile store(String key, InputStream content) throws IOException {
        Path target = path(key);
        Files.createDirectories(target.getParent());
        MessageDigest digest = sha256();
        long size;
        try (OutputStream out = new DigestOutputStream(Files.newOutputStream(target), digest)) {
            size = content.transferTo(out);
        }
        return new StoredFile(size, HexFormat.of().formatHex(digest.digest()));
    }

    @Override
    public Path path(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Storage key escapes the storage root: " + key);
        }
        return resolved;
    }

    @Override
    public Resource load(String key) {
        return new PathResource(path(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(path(key));
        } catch (IOException e) {
            // The database row is already gone: an orphan file is harmless and logged for cleanup.
            log.warn("Could not delete stored file {}", key, e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new UncheckedIOException(new IOException("SHA-256 not available", e));
        }
    }
}
