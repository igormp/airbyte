/*
 * Copyright (c) 2021 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.gcs;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import io.airbyte.integrations.destination.gcs.credential.GcsCredentialConfig;
import io.airbyte.integrations.destination.gcs.credential.GcsCredentialConfigs;
import io.airbyte.integrations.destination.gcs.credential.GcsHmacKeyCredentialConfig;
import io.airbyte.integrations.destination.s3.S3DestinationConfig;
import io.airbyte.integrations.destination.s3.S3FormatConfig;
import io.airbyte.integrations.destination.s3.S3FormatConfigs;

public class GcsDestinationConfig extends S3DestinationConfig {

  private static final String GCS_ENDPOINT = "https://storage.googleapis.com";

  private final GcsCredentialConfig credentialConfig;

  public GcsDestinationConfig(final String bucketName,
                              final String bucketPath,
                              final String bucketRegion,
                              final GcsCredentialConfig credentialConfig,
                              final S3FormatConfig formatConfig) {
    super(null, bucketName, bucketPath, bucketRegion, null, null, formatConfig);
    this.credentialConfig = credentialConfig;
  }

  public static GcsDestinationConfig getGcsDestinationConfig(final JsonNode config) {
    return new GcsDestinationConfig(
        config.get("gcs_bucket_name").asText(),
        config.get("gcs_bucket_path").asText(),
        config.get("gcs_bucket_region").asText(),
        GcsCredentialConfigs.getCredentialConfig(config),
        S3FormatConfigs.getS3FormatConfig(config));
  }

  public GcsCredentialConfig getCredentialConfig() {
    return credentialConfig;
  }

  @Override
  protected AmazonS3 createS3Client() {
    final GcsHmacKeyCredentialConfig hmacKeyCredential = (GcsHmacKeyCredentialConfig) credentialConfig;
    final BasicAWSCredentials awsCreds = new BasicAWSCredentials(hmacKeyCredential.getHmacKeyAccessId(), hmacKeyCredential.getHmacKeySecret());

    return AmazonS3ClientBuilder.standard()
        .withEndpointConfiguration(
            new AwsClientBuilder.EndpointConfiguration(GCS_ENDPOINT, getBucketRegion()))
        .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
        .build();
  }

}
