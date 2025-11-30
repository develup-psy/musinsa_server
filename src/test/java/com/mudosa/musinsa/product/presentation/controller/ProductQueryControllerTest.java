package com.mudosa.musinsa.product.presentation.controller;

import com.mudosa.musinsa.product.application.dto.ProductDetailResponse;
import com.mudosa.musinsa.product.application.dto.ProductSearchCondition;
import com.mudosa.musinsa.product.application.dto.ProductSearchResponse;
import com.mudosa.musinsa.security.CustomUserDetails;
import com.mudosa.musinsa.product.domain.model.ProductGenderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ProductQueryController 테스트")
class ProductQueryControllerTest extends ControllerTestSupport {

    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        userDetails = new CustomUserDetails(1L, "USER");
    }

    @Test
    @DisplayName("상품 검색을 수행한다.")
    void searchProducts() throws Exception {
        // given
        ProductSearchResponse response = ProductSearchResponse.builder()
            .products(List.of(
                ProductSearchResponse.ProductSummary.builder()
                    .productId(10L)
                    .brandId(1L)
                    .brandName("브랜드")
                    .productName("블랙 티셔츠")
                    .productInfo("설명")
                    .productGenderType("ALL")
                    .isAvailable(true)
                    .hasStock(true)
                    .lowestPrice(BigDecimal.valueOf(10000))
                    .thumbnailUrl("thumb.jpg")
                    .categoryPath("상의>티셔츠")
                    .build()
            ))
            .nextCursor("cursor-10")
            .hasNext(true)
            .build();

        given(productQueryService.searchProducts(any()))
            .willReturn(response);

        // when // then
        mockMvc.perform(get("/api/products")
                .param("keyword", "티셔츠")
                .with(user(userDetails)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.products[0].productId").value(10L))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.nextCursor").value("cursor-10"));
    }

    @Test
    @DisplayName("검색 요청 파라미터가 Service로 정상 전달된다.")
    void searchProductsRequestBinding() throws Exception {
        // given
        given(productQueryService.searchProducts(any()))
            .willReturn(ProductSearchResponse.builder().build());

        // when
        mockMvc.perform(get("/api/products")
                .with(user(userDetails))
                .param("keyword", "티셔츠")
                .param("categoryPaths", "상의>티셔츠", "상의>맨투맨")
                .param("gender", "MEN")
                .param("brandId", "5")
                .param("priceSort", "LOWEST")
                .param("cursor", "1000:10")
                .param("limit", "30"))
            .andExpect(status().isOk());

        // then
        ArgumentCaptor<ProductSearchCondition> captor = ArgumentCaptor.forClass(ProductSearchCondition.class);
        verify(productQueryService).searchProducts(captor.capture());
        ProductSearchCondition condition = captor.getValue();

        assertThat(condition.getKeyword()).isEqualTo("티셔츠");
        assertThat(condition.getCategoryPaths()).containsExactly("상의>티셔츠", "상의>맨투맨");
        assertThat(condition.getGender()).isEqualTo(ProductGenderType.MEN);
        assertThat(condition.getBrandId()).isEqualTo(5L);
        assertThat(condition.getPriceSort()).isEqualTo(ProductSearchCondition.PriceSort.LOWEST);
        assertThat(condition.getCursor()).isEqualTo("1000:10");
        assertThat(condition.getLimit()).isEqualTo(30);
    }

    @Test
    @DisplayName("상품 상세를 조회한다.")
    void getProductDetail() throws Exception {
        // given
        ProductDetailResponse response = ProductDetailResponse.builder()
            .productId(20L)
            .brandId(1L)
            .brandName("브랜드")
            .productName("화이트 바지")
            .productInfo("설명")
            .productGenderType("ALL")
            .isAvailable(true)
            .categoryPath("하의>바지")
            .build();

        given(productQueryService.getProductDetail(20L))
            .willReturn(response);

        // when // then
        mockMvc.perform(get("/api/products/{productId}", 20L)
                .with(user(userDetails)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(20L))
            .andExpect(jsonPath("$.productName").value("화이트 바지"));
    }
}
