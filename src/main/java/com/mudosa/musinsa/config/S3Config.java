package com.mudosa.musinsa.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class S3Config {

  @Value("${aws.s3.accessKey}")
  private String accessKey;
  @Value("${aws.s3.secretKey}")
  private String secretKey;
  @Value("${aws.s3.region:ap-southeast-2}")
  private String region;

  // 1. S3 처리를 위한 전용 스레드 풀 생성
  @Bean(name = "s3AsyncExecutor")
  public Executor s3AsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10); // 기본 스레드 수
    executor.setMaxPoolSize(50);  // 최대 스레드 수
    executor.setQueueCapacity(100); // 대기열 크기
    executor.setThreadNamePrefix("S3-Async-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }

  // 2. S3AsyncClient 생성 및 스레드 풀 주입
  @Bean
  public S3AsyncClient s3AsyncClient(Executor s3AsyncExecutor) {
    AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);

    return S3AsyncClient.builder()
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
        .overrideConfiguration(ClientOverrideConfiguration.builder()
            .retryPolicy(RetryPolicy.builder().numRetries(3).build())
            .build())
        // 여기서 스레드 풀을 설정합니다.
        .asyncConfiguration(b -> b.advancedOption(
            SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR,
            s3AsyncExecutor
        ))
        .build();
  }

  @Bean
  public S3Client s3Client() {
    AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);

    return S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
        .overrideConfiguration(ClientOverrideConfiguration.builder()
            .retryPolicy(RetryPolicy.builder().numRetries(3).build())
            .build())
        .build();
  }

}