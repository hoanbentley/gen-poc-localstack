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
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.core.internal.http.loader.DefaultSdkHttpClientBuilder;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.utils.AttributeMap;

import java.net.http.HttpClient;
import java.time.Duration;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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

        s3Client = S3Client
            .builder()
            .endpointOverride(localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(localStackContainer.getAccessKey(), localStackContainer.getSecretKey())
                )
            )
            .region(Region.of(localStackContainer.getRegion()))
            .httpClient(ApacheHttpClient.builder()
                .useIdleConnectionReaper(false)
                .build())
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .retryPolicy(r -> r.numRetries(0))
                    //.putAdvancedOption(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true)
                    .build()
            )
            .forcePathStyle(true)
            .build();

        log.info("S3 Client setup completed with endpoint: {}", localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3));

        // Verify LocalStack S3 service using HttpClient (similar to curl)
        verifyLocalStackS3();

    }

    @BeforeEach
    public void setup() throws Exception {
        try {
            // Create bucket
            log.info("Creating bucket 'sample-bucket'...");
            s3Client.createBucket(CreateBucketRequest.builder().bucket("sample-bucket").build());
            log.info("Bucket 'sample-bucket' created successfully.");

        } catch(S3Exception e) {
            log.error("S3Exception: {}", e.awsErrorDetails().errorMessage());
            log.error("HTTP Status Code: {}", e.statusCode());
            log.error("AWS Error Code: {}", e.awsErrorDetails().errorCode());
            log.error("Request ID: {}", e.requestId());
        } catch (Exception e) {
            log.error("Exception during setup: ", e);
        }
    }

    @Test
    public void testLocalStackIntegration() throws Exception {

    }

    private static void verifyLocalStackS3() {
        try {
            String endpoint = localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3).toString();
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(endpoint + "/"))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("HTTP Status Code: {}", response.statusCode());
            log.info("Response Body: {}", response.body());
        } catch (Exception e) {
            log.error("Exception during HttpClient request: ", e);
        }
    }
}
