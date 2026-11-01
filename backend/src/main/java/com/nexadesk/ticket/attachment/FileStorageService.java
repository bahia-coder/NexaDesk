package com.nexadesk.ticket.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.springframework.core.io.Resource;

/** Binary storage abstraction: the local implementation can be swapped for S3 without touching the services. */
public interface FileStorageService {

    record StoredFile(long sizeBytes, String sha256) {
    }

    StoredFile store(String key, InputStream content) throws IOException;

    /** Local path of a stored file, for content inspection right after upload. */
    Path path(String key);

    Resource load(String key);

    void delete(String key);
}
