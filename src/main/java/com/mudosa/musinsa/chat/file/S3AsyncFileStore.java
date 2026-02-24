package com.mudosa.musinsa.chat.file;

import com.mudosa.musinsa.chat.event.TempUploadedFile;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class S3AsyncFileStore extends AbstractS3Store implements FileStore {

  private final S3AsyncClient s3AsyncClient;

  public S3AsyncFileStore(Tracer tracer, S3AsyncClient s3AsyncClient) {
    super(tracer);
    this.s3AsyncClient = s3AsyncClient;
  }

  /**
   * 채팅 메시지 첨부용 - TempUploadedFile 기반
   */
  @Override
  public CompletableFuture<String> storeMessageFile(Long chatId, Long messageId, TempUploadedFile file) {
    String key = generateMessageKey(chatId, messageId, file);
    return uploadInternal(key, file);
  }

  /**
   * 브랜드 로고 업로드 - 기존 MultipartFile 기반 유지
   */
  @Override
  public CompletableFuture<String> storeBrandLogo(Long brandId, MultipartFile file) {
    String key = generateBrandKey(brandId, file);
    return uploadInternal(key, file);
  }

  /**
   * TempUploadedFile 기반 비동기 업로드
   * - 이미 메모리에 로딩된 byte[] 사용
   */
  private CompletableFuture<String> uploadInternal(String key, TempUploadedFile file) {
    long size = file.size();

    Span span = tracer.nextSpan().name("s3.upload")
        .tag("type", "async")
        .tag("file.size", String.valueOf(size))
        .start();

    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .contentType(file.contentType())
        .contentLength(size)
        .build();

    return s3AsyncClient.putObject(request, AsyncRequestBody.fromFile(file.tempPath()))
        .thenApply(resp ->
            s3AsyncClient.utilities()
                .getUrl(GetUrlRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build())
                .toString()
        )
        .whenComplete((url, ex) -> {
          try {
            log.debug("임시 파일 삭제. path={}", file.tempPath());
            Files.deleteIfExists(file.tempPath());
          } catch (IOException ioe) {
            log.warn("임시 파일 삭제 실패. path={}", file.tempPath(), ioe);
          }

          if (ex != null) {
            span.error(ex);
            log.error("S3 async upload failed (TempUploadedFile). key={}", key, ex);
          }
          span.end();
        });
  }

  /**
   * 기존 MultipartFile 기반 비동기 업로드 (브랜드 로고 등)
   */
  private CompletableFuture<String> uploadInternal(String key, MultipartFile file) {
    Span span = tracer.nextSpan().name("s3.upload")
        .tag("type", "async")
        .tag("file.size", String.valueOf(file.getSize()))
        .start();

    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      span.error(e);
      span.end();
      return CompletableFuture.failedFuture(e);
    }

    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .contentType(file.getContentType())
        .contentLength((long) bytes.length)
        .build();

    return s3AsyncClient.putObject(request, AsyncRequestBody.fromBytes(bytes))
        .thenApply(resp ->
            s3AsyncClient.utilities()
                .getUrl(GetUrlRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build())
                .toString()
        )
        .whenComplete((url, ex) -> {
          if (ex != null) {
            span.error(ex);
            log.error("S3 async upload failed (MultipartFile). key={}", key, ex);
          }
          span.end();
        });
  }
}
