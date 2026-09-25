/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : FileSystemDocumentStorage.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Stores uploaded documents as files under a configured root
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * Files on disk under a configured root, with the database holding only the metadata.
 *
 * <p>Chosen over the two alternatives deliberately. Putting the bytes in a database column would
 * bloat every backup of the books with scanned paper and make ordinary queries heavier for no gain.
 * MinIO is already on the classpath and configured in {@code application.yml}, but nothing starts
 * it or checks it, and a document library that returns errors whenever a second service is down is
 * worse than one that writes to a directory. {@link DocumentStorage} keeps that door open: a MinIO
 * implementation is a drop-in, with no schema change, because the row only ever holds a key.
 *
 * <p>The cost is stated rather than hidden. Files live on <strong>one machine</strong>: there is no
 * redundancy, a second application instance would not see them, and a backup that copies the
 * database without this directory restores rows pointing at files that are not there.
 */
@Component
public class FileSystemDocumentStorage implements DocumentStorage {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemDocumentStorage.class);

    private final Path root;

    public FileSystemDocumentStorage(@Value("${documents.storage.root:./data/documents}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    @Override
    public Stored store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a file to upload");
        }
        String key = nextKey(file.getOriginalFilename());
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = file.getInputStream();
                 DigestInputStream hashing = new DigestInputStream(in, digest)) {
                Files.copy(hashing, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new Stored(key, Files.size(target), hex(digest.digest()));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available on this machine", ex);
        } catch (IOException ex) {
            LOG.error("Could not write {} to {}", key, root, ex);
            throw new IllegalStateException(
                    "The file could not be saved to " + root + " — " + ex.getMessage(), ex);
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isReadable(path)) {
            throw new IllegalStateException("That file is recorded but is not in the store at "
                    + root + ". It may have been moved or removed outside the application.");
        }
        return new FileSystemResource(path);
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.isReadable(resolve(storageKey));
    }

    @Override
    public boolean delete(String storageKey) {
        try {
            return Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ex) {
            LOG.warn("Could not delete {} under {}", storageKey, root, ex);
            return false;
        }
    }

    @Override
    public String describeLocation() {
        return root.toString();
    }

    /**
     * Keys are generated, and foldered by month so one directory does not end up holding every
     * file the business has ever scanned. The extension is carried over only after being stripped
     * of anything that is not a letter or a digit.
     */
    private static String nextKey(String originalName) {
        String extension = "";
        int dot = originalName == null ? -1 : originalName.lastIndexOf('.');
        if (dot >= 0 && dot < originalName.length() - 1) {
            String raw = originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
            String cleaned = raw.replaceAll("[^a-z0-9]", "");
            if (!cleaned.isEmpty() && cleaned.length() <= 10) {
                extension = "." + cleaned;
            }
        }
        LocalDate today = LocalDate.now();
        return String.format("%d/%02d/%s%s", today.getYear(), today.getMonthValue(),
                UUID.randomUUID(), extension);
    }

    /**
     * Every path is rebuilt from the root and checked to be inside it. The keys this class writes
     * are safe by construction, but a key read back from a row is data, and data that reaches
     * {@code Path.resolve} unchecked is how {@code ../../} escapes the folder it was meant for.
     */
    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("That document has no file recorded against it");
        }
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("That document points outside the document store");
        }
        return path;
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            text.append(Character.forDigit((b >> 4) & 0xF, 16));
            text.append(Character.forDigit(b & 0xF, 16));
        }
        return text.toString();
    }
}
