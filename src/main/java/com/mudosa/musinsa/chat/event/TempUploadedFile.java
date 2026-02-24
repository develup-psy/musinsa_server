package com.mudosa.musinsa.chat.event;

import java.nio.file.Path;

public record TempUploadedFile(
    String originalFilename,
    String contentType,
    Path tempPath,
    long size
) {
}
