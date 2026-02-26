package com.mudosa.musinsa.chat.file;

import com.mudosa.musinsa.chat.event.TempUploadedFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

public interface FileStore {
  // 동기든 비동기든 모두 Future를 반환하도록 약속
  CompletableFuture<String> storeMessageFile(Long chatId, Long messageId, TempUploadedFile file);

  CompletableFuture<String> storeBrandLogo(Long brandId, MultipartFile file);
}