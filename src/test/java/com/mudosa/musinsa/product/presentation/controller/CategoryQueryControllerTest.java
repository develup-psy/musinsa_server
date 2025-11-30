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
        CategoryTreeResponse.CategoryNode tops = CategoryTreeResponse.CategoryNode.builder()
            .categoryId(1L)
            .categoryName("상의")
            .categoryPath("상의")
            .imageUrl("tops.jpg")
            .children(List.of(CategoryTreeResponse.CategoryNode.builder()
                .categoryId(2L)
                .categoryName("티셔츠")
                .categoryPath("상의>티셔츠")
                .imageUrl("tee.jpg")
                .children(List.of())
                .build()))
            .build();
        CategoryTreeResponse.CategoryNode bottoms = CategoryTreeResponse.CategoryNode.builder()
            .categoryId(3L)
            .categoryName("하의")
            .categoryPath("하의")
            .imageUrl("bottoms.jpg")
            .children(List.of(CategoryTreeResponse.CategoryNode.builder()
                .categoryId(4L)
                .categoryName("바지")
                .categoryPath("하의>바지")
                .imageUrl("pants.jpg")
                .children(List.of())
                .build()))
            .build();
        given(productQueryService.getCategoryTree())
            .willReturn(CategoryTreeResponse.builder().categories(List.of(tops, bottoms)).build());

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
