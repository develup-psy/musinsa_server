package com.mudosa.musinsa.config;

import com.mudosa.musinsa.chat.file.FileStore;
import com.mudosa.musinsa.chat.file.LocalFileStore;
import com.mudosa.musinsa.chat.file.S3AsyncFileStore;
import com.mudosa.musinsa.chat.file.S3SyncFileStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class FileStoreConfig {

  @Bean
  @Primary
  public FileStore fileStore(
      @Value("${app.file-store.provider:local}") String provider,
      @Value("${app.file-store.mode:async}") String mode,
      ObjectProvider<S3SyncFileStore> syncStoreProvider,
      ObjectProvider<S3AsyncFileStore> asyncStoreProvider,
      LocalFileStore localStore) {

    if (!"s3".equalsIgnoreCase(provider)) {
      return localStore;
    }

    if ("sync".equalsIgnoreCase(mode)) {
      S3SyncFileStore syncStore = syncStoreProvider.getIfAvailable();
      if (syncStore != null) {
        return syncStore;
      }
      log.warn("S3 sync file store bean is unavailable. Fallback to local file store.");
      return localStore;
    }

    S3AsyncFileStore asyncStore = asyncStoreProvider.getIfAvailable();
    if (asyncStore != null) {
      return asyncStore;
    }

    log.warn("S3 async file store bean is unavailable. Fallback to local file store.");
    return localStore;
  }
}
