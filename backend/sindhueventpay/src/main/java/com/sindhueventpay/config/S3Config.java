package com.sindhueventpay.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * AWS S3 client configuration.
 *
 * <p><strong>Credential resolution order:</strong>
 * <ol>
 *   <li>If {@code AWS_ACCESS_KEY_ID} and {@code AWS_SECRET_ACCESS_KEY} are
 *       provided (local dev / CI), they are used via
 *       {@link StaticCredentialsProvider}.</li>
 *   <li>Otherwise, {@link DefaultCredentialsProvider} is used which checks
 *       environment variables → AWS config files → IAM role (EC2/ECS). This
 *       is the preferred production setup.</li>
 * </ol>
 *
 * <p>The S3 bucket is private. Public access is blocked. All object keys are
 * server-generated UUIDs; user names and Aadhaar numbers are never part of any key.
 */
@Configuration
public class S3Config {

    @Autowired
    private AppProperties appProperties;

    @Bean
    public S3Client s3Client() {
        return buildBuilder().build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        AppProperties.Aws awsProps = appProperties.getAws();
        var builder = S3Presigner.builder()
                .region(Region.of(awsProps.getRegion()));

        if (hasExplicitCredentials(awsProps)) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    awsProps.getAccessKeyId(),
                                    awsProps.getSecretAccessKey())));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private software.amazon.awssdk.services.s3.S3ClientBuilder buildBuilder() {
        AppProperties.Aws awsProps = appProperties.getAws();
        var builder = S3Client.builder()
                .region(Region.of(awsProps.getRegion()));

        if (hasExplicitCredentials(awsProps)) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    awsProps.getAccessKeyId(),
                                    awsProps.getSecretAccessKey())));
        } else {
            // Production: IAM role / instance profile / ECS task role
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder;
    }

    private boolean hasExplicitCredentials(AppProperties.Aws awsProps) {
        return awsProps.getAccessKeyId() != null
                && !awsProps.getAccessKeyId().isBlank()
                && awsProps.getSecretAccessKey() != null
                && !awsProps.getSecretAccessKey().isBlank();
    }
}
