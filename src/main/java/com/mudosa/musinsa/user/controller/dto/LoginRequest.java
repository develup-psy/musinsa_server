package com.mudosa.musinsa.user.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Getter
@Schema(description = "로그인 요청 DTO")
public class LoginRequest {
    @Schema(description="이메일", example = "test@test.gmail", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "이메일을 입력해주세요")
    @Email(message = "이메일 형식으로 작성해주세요")
    private String email;

    @Schema(description="비밀번호", example = "StrongPwd123!", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "비밀번호를 필수적으로 입력하셔야 합니다. ")
    private String password;
}
