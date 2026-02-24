package com.mudosa.musinsa.chat.file;

import com.mudosa.musinsa.chat.event.TempUploadedFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.file-store.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStore implements FileStore {

  private final Path rootDir;

  public LocalFileStore(@Value("${app.file-store.local-root:./tmp/uploads}") String localRoot) {
    this.rootDir = Path.of(localRoot).toAbsolutePath().normalize();
  }

  @Override
  public CompletableFuture<String> storeMessageFile(Long chatId, Long messageId, TempUploadedFile file) {
    try {
      String fileName = UUID.randomUUID() + "_" + sanitize(file.originalFilename());
      Path target = rootDir.resolve(Path.of("chat", String.valueOf(chatId), String.valueOf(messageId), fileName));
      Files.createDirectories(target.getParent());
      Files.move(file.tempPath(), target, StandardCopyOption.REPLACE_EXISTING);
      return CompletableFuture.completedFuture(target.toUri().toString());
    } catch (IOException e) {
      log.error("Local message file store failed", e);
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<String> storeBrandLogo(Long brandId, MultipartFile file) {
    try {
      String fileName = UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());
      Path target = rootDir.resolve(Path.of("brand", String.valueOf(brandId), fileName));
      Files.createDirectories(target.getParent());
      file.transferTo(target);
      return CompletableFuture.completedFuture(target.toUri().toString());
    } catch (IOException e) {
      log.error("Local brand logo file store failed", e);
      return CompletableFuture.failedFuture(e);
    }
  }

  private String sanitize(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "unknown";
    }
    return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
  }
}
