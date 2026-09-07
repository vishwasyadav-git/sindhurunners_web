package com.sindhueventpay.services;

import com.sindhueventpay.config.AppProperties;
import com.sindhueventpay.exceptions.S3UploadException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Service responsible for all AWS S3 interactions related to Aadhaar documents.
 *
 * <h3>Security invariants enforced by this class:</h3>
 * <ul>
 *   <li>The S3 bucket is always private. No public ACL is ever set.</li>
 *   <li>Object keys are UUID-based paths — no user name, email, or Aadhaar number
 *       appears in any key.</li>
 *   <li>MIME type is detected from file <em>content</em> (Apache Tika) not the
 *       file extension — prevents extension spoofing (e.g. {@code evil.pdf.exe}).</li>
 *   <li>File size is validated before the byte array is read into memory.</li>
 *   <li>Aadhaar document content is never logged.</li>
 *   <li>S3 object keys are logged only for debugging at DEBUG level.</li>
 * </ul>
 */
@Service
@Slf4j
public class S3Service {

    private static final Tika TIKA = new Tika();

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Presigner s3Presigner;

    @Autowired
    private AppProperties appProperties;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates and uploads an Aadhaar document to S3.
     *
     * @param file           the uploaded file from the multipart request
     * @param eventCode      e.g. {@code EVT2026}
     * @param registrationId UUID of the registration being created
     * @return S3 object key, e.g.
     *         {@code events/EVT2026/registrations/{uuid}/aadhaar/document.jpg}
     * @throws IllegalArgumentException if file validation fails
     * @throws S3UploadException        if the S3 API call fails
     */
    public String uploadAadhaarDocument(MultipartFile file,
                                        String eventCode,
                                        String registrationId) {
        validateFile(file);

        String mimeType = detectMimeType(file);
        String extension = extensionForMime(mimeType);
        String objectKey = buildObjectKey(eventCode, registrationId, extension);

        try {
            byte[] bytes = file.getBytes();

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(appProperties.getAws().getS3Bucket())
                    .key(objectKey)
                    .contentType(mimeType)
                    .contentLength((long) bytes.length)
                    // SSE-S3 server-side encryption (bucket-level default also recommended)
                    .serverSideEncryption(
                            software.amazon.awssdk.services.s3.model.ServerSideEncryption.AES256)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(bytes));

            log.debug("Aadhaar document uploaded. key=[{}]", objectKey);
            return objectKey;

        } catch (IOException e) {
            throw new S3UploadException("Failed to read uploaded file bytes", e);
        } catch (Exception e) {
            throw new S3UploadException("S3 upload failed", e);
        }
    }

    /**
     * Silently deletes an S3 object. Errors are logged but not rethrown
     * (used for cleanup of abandoned registration documents).
     *
     * @param objectKey the S3 key returned by {@link #uploadAadhaarDocument}
     */
    public void deleteDocument(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(appProperties.getAws().getS3Bucket())
                    .key(objectKey)
                    .build());
            log.debug("S3 object deleted. key=[{}]", objectKey);
        } catch (Exception e) {
            log.warn("Failed to delete S3 object key=[{}]: {}", objectKey, e.getMessage());
        }
    }

    /**
     * Generates a short-lived (15-minute) pre-signed GET URL for admin access.
     *
     * <p>This endpoint must only be called from an authorised admin API —
     * never expose it publicly.
     *
     * @param objectKey the S3 key of the document
     * @param expiry    how long the URL should be valid (max 7 days for S3)
     * @return a pre-signed HTTPS URL valid for {@code expiry}
     */
    public String generatePresignedUrl(String objectKey, Duration expiry) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(appProperties.getAws().getS3Bucket())
                        .key(objectKey)
                        .build())
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates file size and MIME type from file content (not extension).
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Aadhaar document is required and cannot be empty.");
        }

        long maxBytes = appProperties.getUpload().getMaxFileSizeBytes();
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "File size " + file.getSize() + " bytes exceeds the maximum of " + maxBytes + " bytes (5 MB).");
        }

        String detected = detectMimeType(file);
        List<String> allowed = appProperties.getUpload().getAllowedMimeTypes();
        if (!allowed.contains(detected)) {
            throw new IllegalArgumentException(
                    "File type '" + detected + "' is not allowed. Allowed types: " +
                    String.join(", ", allowed));
        }
    }

    /**
     * Detects MIME type from the actual file content bytes using Apache Tika.
     * This is resistant to extension spoofing.
     */
    private String detectMimeType(MultipartFile file) {
        try {
            return TIKA.detect(file.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read file content for MIME type detection.", e);
        }
    }

    /**
     * Maps MIME type to a safe file extension for the S3 key.
     */
    private String extensionForMime(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png"  -> "png";
            case "application/pdf" -> "pdf";
            default -> "bin";
        };
    }

    /**
     * Builds the S3 object key for an Aadhaar document.
     *
     * <p>Pattern: {@code events/{eventCode}/registrations/{uuid}/aadhaar/document.{ext}}
     * <p>No user name, email, or Aadhaar number ever appears in the key.
     */
    private String buildObjectKey(String eventCode, String registrationId, String extension) {
        return String.format("events/%s/registrations/%s/aadhaar/document.%s",
                eventCode, registrationId, extension);
    }
}
