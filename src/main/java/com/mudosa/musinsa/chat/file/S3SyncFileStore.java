package com.mudosa.musinsa.chat.file;

import com.mudosa.musinsa.chat.event.TempUploadedFile;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class S3SyncFileStore extends AbstractS3Store implements FileStore {

  private final S3Client s3Client;

  public S3SyncFileStore(Tracer tracer, S3Client s3Client) {
    super(tracer);
    this.s3Client = s3Client;
  }

  /**
   * 채팅 메시지 첨부용 - TempUploadedFile 기반
   */
  @Override
  public CompletableFuture<String> storeMessageFile(Long chatId, Long messageId, TempUploadedFile file) {
    String key = generateMessageKey(chatId, messageId, file);
    String url = uploadInternal(key, file);

    return CompletableFuture.completedFuture(url);
  }

  /**
   * 브랜드 로고 업로드 - 기존 MultipartFile 그대로 사용
   */
  @Override
  public CompletableFuture<String> storeBrandLogo(Long brandId, MultipartFile file) {
    String key = generateBrandKey(brandId, file);
    String url = uploadInternal(key, file);
    return CompletableFuture.completedFuture(url);
  }

  /**
   * TempUploadedFile 기반 업로드 (비동기 처리용, 이미 메모리에 로딩된 데이터)
   */
  private String uploadInternal(String key, TempUploadedFile file) {
    long size = file.size();

    Span span = tracer.nextSpan().name("s3.upload")
        .tag("type", "sync")
        .tag("file.size", String.valueOf(size))
        .start();

    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {

      PutObjectRequest request = PutObjectRequest.builder()
          .bucket(bucketName)
          .key(key)
          .contentType(file.contentType())
          .contentLength(size)
          .build();

      // 메모리에 이미 올라온 byte[] 를 그대로 사용
      try {
        s3Client.putObject(request, RequestBody.fromFile(file.tempPath()));
      } catch (Exception e) {
        span.error(e);
        throw e;
      } finally {
        try {
          Files.deleteIfExists(file.tempPath());
        } catch (IOException ioe) {
          log.warn("임시 파일 삭제 실패. path={}", file.tempPath(), ioe);
        }
        span.end();
      }

      return s3Client.utilities().getUrl(
          GetUrlRequest.builder()
              .bucket(bucketName)
              .key(key)
              .build()
      ).toString();

    } catch (Exception e) {
      span.error(e);
      throw new RuntimeException("S3 upload failed (TempUploadedFile). key=" + key, e);
    } finally {
      span.end();
    }
  }

  /**
   * 기존 MultipartFile 기반 업로드 (브랜드 로고 등 동기 업로드용)
   */
  private String uploadInternal(String key, MultipartFile file) {
    long size = file.getSize();

    Span span = tracer.nextSpan().name("s3.upload")
        .tag("type", "sync")
        .tag("file.size", String.valueOf(size))
        .start();

    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
      try (InputStream inputStream = file.getInputStream()) {

        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(file.getContentType())
            .contentLength(size)
            .build();

        s3Client.putObject(request, RequestBody.fromInputStream(inputStream, size));

        return s3Client.utilities().getUrl(
            GetUrlRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
        ).toString();

      } catch (IOException e) {
        span.error(e);
        throw new RuntimeException("S3 upload failed (MultipartFile). key=" + key, e);
      }
    } finally {
      span.end();
    }
  }
}
