package com.mudosa.musinsa.product.domain.model;

import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.common.domain.model.BaseEntity;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// 상품 애그리거트 루트 엔티티로 연관된 하위 요소를 함께 관리한다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
//크롤링을 위한 임시
@Table(name = "product")
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    // 이미지·옵션은 상품 생명주기와 동일하게 관리하기 위해 orphanRemoval 적용 (조회용)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private java.util.List<Image> images = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private java.util.List<ProductOption> productOptions = new java.util.ArrayList<>();

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "product_info", nullable = false, length = 600)
    private String productInfo;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable;

    @Column(name = "default_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal defaultPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_gender_type", nullable = false)
    private ProductGenderType productGenderType;

    // 역정규화 브랜드이름 (조회)
    @Column(name = "brand_name", nullable = false, length = 100)
    private String brandName;

    // 역정규화: "상의/티셔츠"
    @Column(name = "category_path", nullable = false, length = 100)
    private String categoryPath;

    // 외부 노출 생성 메서드 + 필수 값 검증
    public static Product create(Brand brand,
                                 String productName,
                                 String productInfo,
                                 ProductGenderType productGenderType,
                                 String brandName,
                                 String categoryPath,
                                 Boolean isAvailable,
                                 BigDecimal defaultPrice,
                                 java.util.List<Image> images,
                                 java.util.List<ProductOption> productOptions) {
        if (productName == null || productName.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_NAME_REQUIRED);
        }
        if (productInfo == null || productInfo.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_INFO_REQUIRED);
        }
        if (productGenderType == null) {
            throw new BusinessException(ErrorCode.PRODUCT_GENDER_TYPE_REQUIRED);
        }

        return new Product(brand, productName, productInfo, productGenderType, brandName, categoryPath,
                isAvailable, images, productOptions, defaultPrice);
    }
 
    @Builder
    private Product(Brand brand, String productName, String productInfo,
                   ProductGenderType productGenderType, String brandName, String categoryPath, Boolean isAvailable,
                   java.util.List<Image> images,
                   java.util.List<ProductOption> productOptions,
                   BigDecimal defaultPrice) {

        this.brand = brand;
        this.productName = productName;
        this.productInfo = productInfo;
        this.productGenderType = productGenderType;
        this.brandName = brandName;
        this.categoryPath = categoryPath;
        this.isAvailable = isAvailable != null ? isAvailable : true;
        this.defaultPrice = defaultPrice != null ? defaultPrice : BigDecimal.ZERO;

        if (images != null) {
            images.forEach(this::addImage);
        }

        if (productOptions != null) {
            productOptions.forEach(this::addProductOption);
        }
    }

    // 이미지 엔티티를 상품과 연결하고 컬렉션에 추가한다.
    public void addImage(Image image) {
        image.setProduct(this);
        this.images.add(image);
    }

    // 옵션 엔티티를 상품과 연결하고 컬렉션에 추가한다.
    public void addProductOption(ProductOption productOption) {
        productOption.setProduct(this);
        this.productOptions.add(productOption);
    }

    // 새로운 옵션 가격이 더 낮으면 대표 가격을 갱신한다.
    public void applyLowerDefaultPrice(BigDecimal candidatePrice) {
        if (candidatePrice == null) {
            return;
        }
        if (this.defaultPrice == null || candidatePrice.compareTo(this.defaultPrice) < 0) {
            this.defaultPrice = candidatePrice;
        }
    }

    // 상품 판매 가능 여부를 직접 전환한다.
    public void changeAvailability(boolean available) {
        this.isAvailable = available;
    }

    // 전달된 값이 존재할 때만 갱신하고, 값이 달라졌을 때 true를 반환한다.
    public boolean updateBasicInfo(String productName,
                                   String productInfo) {
        boolean updated = false;

        if (productName != null) {
            if (productName.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PRODUCT_NAME_REQUIRED);
            }
            if (!productName.equals(this.productName)) {
                this.productName = productName;
                updated = true;
            }
        }

        if (productInfo != null) {
            if (productInfo.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PRODUCT_INFO_REQUIRED);
            }
            if (!productInfo.equals(this.productInfo)) {
                this.productInfo = productInfo;
                updated = true;
            }
        }
        return updated;
    }
}
