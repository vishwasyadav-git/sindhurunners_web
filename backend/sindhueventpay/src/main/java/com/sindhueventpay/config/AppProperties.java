package com.sindhueventpay.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Typed configuration properties bound from {@code application.properties}
 * under the {@code app.*} prefix.
 *
 * <p>Registered via {@link org.springframework.boot.context.properties.EnableConfigurationProperties}
 * in {@link AppConfig}. Never inject {@link org.springframework.beans.factory.annotation.Value}
 * directly for secrets; use this class instead for testability and type safety.
 */
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private Razorpay razorpay = new Razorpay();
    private Aws aws = new Aws();
    private Upload upload = new Upload();
    private Cors cors = new Cors();

    @Getter
    @Setter
    public static class Razorpay {
        /** Razorpay Key ID (public – safe to send to frontend). */
        private String keyId;

        /** Razorpay Key Secret (private – server-side only, never logged). */
        private String keySecret;

        /** Razorpay Webhook Secret for HMAC verification. */
        private String webhookSecret;
    }

    @Getter
    @Setter
    public static class Aws {
        /** AWS region, e.g. ap-south-1 */
        private String region = "ap-south-1";

        /** Private S3 bucket name for Aadhaar documents. */
        private String s3Bucket;

        /**
         * Optional explicit credentials for local dev.
         * In production, leave blank and use IAM roles instead.
         */
        private String accessKeyId;

        /** Paired with accessKeyId; blank in production when using IAM roles. */
        private String secretAccessKey;
    }

    @Getter
    @Setter
    public static class Upload {
        /** Maximum allowed file size in bytes (default: 5 MB). */
        private long maxFileSizeBytes = 5_242_880L;

        /** Allowed MIME types detected from file content via Apache Tika. */
        private List<String> allowedMimeTypes =
                List.of("image/jpeg", "image/png", "application/pdf");
    }

    @Getter
    @Setter
    public static class Cors {
        /** Comma-separated list of allowed CORS origins. */
        private String allowedOrigins = "http://localhost:3000";
    }
}
