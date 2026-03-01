//package com.mudosa.musinsa.chat.file;
//
//import com.mudosa.musinsa.ServiceConfig;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Nested;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.mock.web.MockMultipartFile;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@DisplayName("LocalFileStore 테스트")
//class LocalFileStoreTest extends ServiceConfig {
//  @Autowired
//  private LocalFileStore fileStore;
//
//  //파일 생성
//  private MultipartFile createFilePart(String filename, String content) {
//    return new MockMultipartFile("files", filename, "image/png", content.getBytes());
//  }
//
//  @Nested
//  @DisplayName("로컬에 파일 저장")
//  class storeMessageFile {
//    @DisplayName("로컬에 파일을 저장 후 저장 경로를 반환합니다")
//    @Test
//    void storeMessageFile_Success() throws IOException {
//      // given
//      Long chatId = 1L;
//      Long messageId = 2L;
//
//      MultipartFile file = createFilePart("a.png", "hello");
//
//      // when
//      String url = fileStore.storeMessageFile(chatId, messageId, file);
//
//      // then
//      assertThat(url)
//          .isNotBlank()
//          .startsWith("/chat/" + chatId + "/message/" + messageId + "/")
//          .endsWith(file.getOriginalFilename());
//
//      Path storedPath = Paths.get(new ClassPathResource("static/").getFile().getAbsolutePath(), url);
//      assertThat(Files.exists(storedPath)).isTrue();
//    }
//  }
//}