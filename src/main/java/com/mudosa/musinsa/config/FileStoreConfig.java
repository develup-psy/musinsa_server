package com.mudosa.musinsa.config;

import com.mudosa.musinsa.chat.file.FileStore;
import com.mudosa.musinsa.chat.file.S3AsyncFileStore;
import com.mudosa.musinsa.chat.file.S3SyncFileStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class FileStoreConfig {

  @Bean
  @Primary // ★ 핵심: @Qualifier가 없을 때 이 빈을 최우선으로 주입합니다.
  public FileStore fileStore(
      @Value("${app.file-store.mode:async}") String mode,
      S3SyncFileStore syncStore,
      S3AsyncFileStore asyncStore) {

    if ("sync".equalsIgnoreCase(mode)) {
      return syncStore;
    }
    return asyncStore; // 기본값: 비동기 스토어
  }
}