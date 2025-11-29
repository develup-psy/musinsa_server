package com.mudosa.musinsa.product.presentation.controller;

import com.mudosa.musinsa.product.application.dto.CategoryTreeResponse;
import com.mudosa.musinsa.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CategoryQueryController 테스트")
class CategoryQueryControllerTest extends ControllerTestSupport {

    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        userDetails = new CustomUserDetails(1L, "USER");
    }

    @Test
    @DisplayName("카테고리 트리를 조회한다.")
    void getCategoryTree() throws Exception {
        // given
        CategoryTreeResponse.CategoryNode tops = new CategoryTreeResponse.CategoryNode(
            1L, "상의", "상의", "tops.jpg",
            List.of(new CategoryTreeResponse.CategoryNode(2L, "티셔츠", "상의>티셔츠", "tee.jpg", List.of()))
        );
        CategoryTreeResponse.CategoryNode bottoms = new CategoryTreeResponse.CategoryNode(
            3L, "하의", "하의", "bottoms.jpg",
            List.of(new CategoryTreeResponse.CategoryNode(4L, "바지", "하의>바지", "pants.jpg", List.of()))
        );
        given(productQueryService.getCategoryTree())
            .willReturn(new CategoryTreeResponse(List.of(tops, bottoms)));

        // when // then
        mockMvc.perform(get("/api/categories/tree")
                .with(user(userDetails)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories[0].categoryName").value("상의"))
            .andExpect(jsonPath("$.categories[0].children[0].categoryName").value("티셔츠"))
            .andExpect(jsonPath("$.categories[1].categoryName").value("하의"))
            .andExpect(jsonPath("$.categories[1].children[0].categoryName").value("바지"));
    }
}
