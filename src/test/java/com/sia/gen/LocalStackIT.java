package com.sia.gen;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.internal.http.loader.DefaultSdkHttpClientBuilder;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.utils.AttributeMap;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Slf4j
@Testcontainers
public class LocalStackIT {

    @Container
    public static LocalStackContainer localStackContainer = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
        .withServices(LocalStackContainer.Service.S3)
        .withEnv("SKIP_SSL_CERT_DOWNLOAD", "1")
        .withEnv("DEBUG", "1")
        .waitingFor(Wait.forLogMessage(".*Ready.*", 1))
        .withStartupTimeout(Duration.ofMinutes(5));

    private static S3Client s3Client;
    private static final AwsBasicCredentials awsCreds = AwsBasicCredentials.create("dev", "dev");

    @BeforeAll
    public static void setUpLocalStack() {
        log.info("Setting up LocalStack container...");
        localStackContainer.start();
        String localStackLogs = localStackContainer.getLogs();
        log.info("LocalStack logs:\n{}", localStackLogs);
        log.info("LocalStack container started.");

        final AttributeMap attributeMap = AttributeMap.builder()
            .put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true)
            .build();

        final SdkHttpClient sdkHttpClient = new DefaultSdkHttpClientBuilder().buildWithDefaults(attributeMap);
        log.info("setUpLocalStack AttributeMap {}", attributeMap);
        log.info("setUpLocalStack sdkHttpClient {}", sdkHttpClient);

        /*s3Client = S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
            .endpointOverride(localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3))
            .region(Region.US_EAST_1)
            .httpClient(sdkHttpClient)
            .build();*/

        /*s3Client = S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
            .endpointOverride(localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3))
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .build();*/

        s3Client = S3Client
            .builder()
            .endpointOverride(localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(localStackContainer.getAccessKey(), localStackContainer.getSecretKey())
                )
            )
            .region(Region.of(localStackContainer.getRegion()))
            .build();

        log.info("S3 Client setup completed with endpoint: {}", localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3));

    }

    @BeforeEach
    public void setup() throws Exception {
        try {
            log.info("Listing buckets...");
            s3Client.listBuckets().buckets().forEach(bucket -> log.info("Bucket: {}", bucket.name()));
            log.info("Buckets listed successfully.");

            // Create bucket
            log.info("Creating bucket 'sample-bucket'...");
            s3Client.createBucket(CreateBucketRequest.builder().bucket("sample-bucket").build());
            log.info("Bucket 'sample-bucket' created successfully.");

            // Verify bucket creation
            boolean bucketExists = s3Client.listBuckets().buckets().stream()
                .anyMatch(bucket -> bucket.name().equals("sample-bucket"));
            assertTrue(bucketExists, "Bucket 'sample-bucket' should exist");

            // Upload a test CSV file to S3
            log.info("Uploading file 'sample.csv' to bucket 'sample-bucket'...");
            byte[] content = Files.readAllBytes(Paths.get("src/test/resources/sample.csv"));
            s3Client.putObject(PutObjectRequest.builder()
                .bucket("sample-bucket")
                .key("sample.csv")
                .build(), Paths.get("src/test/resources/sample.csv"));
            log.info("File 'sample.csv' uploaded successfully to bucket 'sample-bucket'.");
        } catch(S3Exception e) {
            log.error("S3Exception: {}", e.awsErrorDetails().errorMessage());
            log.error("Error Code: {}", e.awsErrorDetails().errorCode());
            log.error("Service Name: {}", e.awsErrorDetails().serviceName());
        } catch (Exception e) {
            log.error("Exception during setup: ", e);
        }
    }

    private void logFileContentFromS3(String bucketName, String key) throws Exception {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();

        // Download the file from S3
        byte[] content = s3Client.getObject(getObjectRequest).readAllBytes();

        // Convert the byte array to a string and log it
        String fileContent = new String(content);
        log.info("Content of the file from S3 ({}):\n{}", key, fileContent);
    }

    @Test
    public void testLocalStackIntegration() throws Exception {
        // Log the content of the file from S3
        logFileContentFromS3("sample-bucket", "sample.csv");
    }
}
