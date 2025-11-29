package com.mudosa.musinsa.product.presentation.controller;

import com.mudosa.musinsa.product.application.dto.ProductDetailResponse;
import com.mudosa.musinsa.product.application.dto.ProductManagerResponse;
import com.mudosa.musinsa.product.application.dto.ProductOptionStockResponse;
import com.mudosa.musinsa.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ProductCommandController 권한 테스트")
class ProductCommandControllerTest extends ControllerTestSupport {

    private CustomUserDetails sellerUser;
    private CustomUserDetails normalUser;
    private CustomUserDetails adminUser;

    @BeforeEach
    void setUp() {
        sellerUser = new CustomUserDetails(1L, "SELLER");
        normalUser = new CustomUserDetails(2L, "USER");
        adminUser = new CustomUserDetails(3L, "ADMIN");
    }

    @Test
    @DisplayName("인증 없이 상품 생성 시 401을 반환한다.")
    void createProduct_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/brands/{brandId}/products", 1L)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SELLER/ADMIN 외 권한으로 상품 생성 시 403을 반환한다.")
    void createProduct_ForbiddenForNonSeller() throws Exception {
        mockMvc.perform(post("/api/brands/{brandId}/products", 1L)
                .with(user(normalUser))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SELLER 권한으로 상품을 생성하면 201을 반환한다.")
    void createProduct_AsSeller_Succeeds() throws Exception {
        // given
        given(productCommandService.createProduct(any(), eq(1L), eq(1L)))
            .willReturn(100L);

        // when // then
        mockMvc.perform(post("/api/brands/{brandId}/products", 1L)
                .with(user(sellerUser))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.productId").value(100L));
    }

    @Test
    @DisplayName("SELLER 권한으로 상품 정보를 수정한다.")
    void updateProduct_AsSeller() throws Exception {
        // given
        ProductDetailResponse response = ProductDetailResponse.builder()
            .productId(200L)
            .productName("업데이트 상품")
            .build();
        given(productCommandService.updateProduct(eq(1L), eq(200L), any(), eq(1L)))
            .willReturn(response);

        // when // then
        mockMvc.perform(put("/api/brands/{brandId}/products/{productId}", 1L, 200L)
                .with(user(sellerUser))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validUpdateBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(200L))
            .andExpect(jsonPath("$.productName").value("업데이트 상품"));
    }

    @Test
    @DisplayName("브랜드 상품 상세를 조회한다.")
    void getProductDetailForManager() throws Exception {
        // given
        ProductManagerResponse response = ProductManagerResponse.builder()
            .productId(300L)
            .productName("상세 상품")
            .build();
        given(productCommandService.getProductDetailForManager(eq(1L), eq(300L), eq(3L)))
            .willReturn(response);

        // when // then
        mockMvc.perform(get("/api/brands/{brandId}/products/{productId}", 1L, 300L)
                .with(user(adminUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(300L))
            .andExpect(jsonPath("$.productName").value("상세 상품"));
    }

    @Test
    @DisplayName("브랜드 상품 목록을 조회한다.")
    void getBrandProductsForManager() throws Exception {
        // given
        ProductManagerResponse item = ProductManagerResponse.builder()
            .productId(10L)
            .productName("리스트 상품")
            .build();
        given(productCommandService.getBrandProductsForManager(eq(1L), eq(1L)))
            .willReturn(List.of(item));

        // when // then
        mockMvc.perform(get("/api/brands/{brandId}/products", 1L)
                .with(user(sellerUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].productId").value(10L));
    }

    @Test
    @DisplayName("상품 옵션을 추가한다.")
    void addProductOption() throws Exception {
        // given
        ProductDetailResponse.OptionDetail optionDetail = ProductDetailResponse.OptionDetail.builder()
            .optionId(55L)
            .productPrice(BigDecimal.valueOf(15000))
            .stockQuantity(10)
            .hasStock(true)
            .build();
        given(productCommandService.addProductOption(eq(1L), eq(200L), any(), eq(1L)))
            .willReturn(optionDetail);

        // when // then
        mockMvc.perform(post("/api/brands/{brandId}/products/{productId}/options", 1L, 200L)
                .with(user(sellerUser))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOptionBody()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.optionId").value(55L))
            .andExpect(jsonPath("$.productPrice").value(15000));
    }

    @Test
    @DisplayName("상품 옵션 재고를 입고 처리한다.")
    void increaseStock() throws Exception {
        // given
        ProductOptionStockResponse response = ProductOptionStockResponse.builder()
            .productOptionId(77L)
            .stockQuantity(20)
            .build();
        given(productInventoryService.addStock(eq(1L), eq(200L), any(), eq(1L)))
            .willReturn(response);

        // when // then
        mockMvc.perform(post("/api/brands/{brandId}/products/{productId}/inventory/increase", 1L, 200L)
                .with(user(sellerUser))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validStockBody(5)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productOptionId").value(77L))
            .andExpect(jsonPath("$.stockQuantity").value(20));
    }

    @Test
    @DisplayName("상품 옵션 재고를 출고 처리한다.")
    void decreaseStock() throws Exception {
        // given
        ProductOptionStockResponse response = ProductOptionStockResponse.builder()
            .productOptionId(77L)
            .stockQuantity(10)
            .build();
        given(productInventoryService.subtractStock(eq(1L), eq(200L), any(), eq(adminUser.getUserId())))
            .willReturn(response);

        // when // then
        mockMvc.perform(post("/api/brands/{brandId}/products/{productId}/inventory/decrease", 1L, 200L)
                .with(user(adminUser))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validStockBody(3)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productOptionId").value(77L))
            .andExpect(jsonPath("$.stockQuantity").value(10));
    }

    @Test
    @DisplayName("상품 옵션 재고 목록을 조회한다.")
    void getProductOptionStocks() throws Exception {
        // given
        ProductOptionStockResponse option = ProductOptionStockResponse.builder()
            .productOptionId(88L)
            .stockQuantity(7)
            .productPrice(BigDecimal.valueOf(12000))
            .build();
        given(productInventoryService.getProductOptionStocks(eq(1L), eq(200L), anyLong()))
            .willReturn(List.of(option));

        // when // then
        mockMvc.perform(get("/api/brands/{brandId}/products/{productId}/inventory", 1L, 200L)
                .with(user(sellerUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].productOptionId").value(88L))
            .andExpect(jsonPath("$[0].stockQuantity").value(7));
    }

    private String validCreateBody() {
        return """
            {
              "productName": "블랙 티셔츠",
              "productInfo": "설명",
              "productGenderType": "ALL",
              "categoryPath": "상의>티셔츠",
              "isAvailable": true,
              "images": [
                {"imageUrl": "thumb.jpg", "isThumbnail": true}
              ],
              "options": [
                {"productPrice": 10000, "stockQuantity": 5, "optionValueIds": [1]}
              ]
            }
            """;
    }

    private String validUpdateBody() {
        return """
            {
              "productName": "업데이트 상품",
              "productInfo": "업데이트 설명",
              "productGenderType": "ALL",
              "categoryPath": "상의>티셔츠",
              "isAvailable": true,
              "images": [
                {"imageId": 1, "imageUrl": "thumb.jpg", "isThumbnail": true}
              ]
            }
            """;
    }

    private String validOptionBody() {
        return """
            {
              "productPrice": 15000,
              "stockQuantity": 10,
              "optionValueIds": [1,2]
            }
            """;
    }

    private String validStockBody(int quantity) {
        return """
            {
              "productOptionId": 77,
              "quantity": %d
            }
            """.formatted(quantity);
    }
}
