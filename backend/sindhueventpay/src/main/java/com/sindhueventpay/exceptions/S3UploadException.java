package com.sindhueventpay.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when uploading the Aadhaar document to AWS S3 fails.
 *
 * <p>When this exception is raised, the registration creation is aborted
 * and no DB record is persisted. The user should retry.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class S3UploadException extends RuntimeException {
    public S3UploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
