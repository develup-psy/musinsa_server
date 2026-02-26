package com.mudosa.musinsa.chat.event;


import java.util.List;

// [내부용] 파일 업로드 요청 이벤트
public record AttachmentUploadEvent(
    Long messageId,
    List<TempUploadedFile> files,
    String clientMessageId
) {
}