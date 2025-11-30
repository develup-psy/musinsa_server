package com.mudosa.musinsa.product.application;

import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.common.vo.Money;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.product.application.dto.ProductCreateRequest;
import com.mudosa.musinsa.product.application.dto.ProductDetailResponse;
import com.mudosa.musinsa.product.application.dto.ProductManagerResponse;
import com.mudosa.musinsa.product.application.dto.ProductOptionCreateRequest;
import com.mudosa.musinsa.product.application.dto.ProductUpdateRequest;
import com.mudosa.musinsa.product.application.mapper.ProductCommandMapper;
import com.mudosa.musinsa.product.domain.model.Category;
import com.mudosa.musinsa.product.domain.model.Inventory;
import com.mudosa.musinsa.product.domain.model.OptionValue;
import com.mudosa.musinsa.product.domain.model.Image;
import com.mudosa.musinsa.product.domain.model.Product;
import com.mudosa.musinsa.product.domain.model.ProductOption;
import com.mudosa.musinsa.product.domain.model.ProductOptionValue;
import com.mudosa.musinsa.brand.domain.repository.BrandMemberRepository;
import com.mudosa.musinsa.brand.domain.repository.BrandRepository;
import com.mudosa.musinsa.product.domain.repository.OptionValueRepository;
import com.mudosa.musinsa.product.domain.repository.CategoryRepository;
import com.mudosa.musinsa.product.domain.repository.ImageRepository;
import com.mudosa.musinsa.product.domain.repository.ProductRepository;
import com.mudosa.musinsa.product.domain.repository.ProductOptionRepository;
import com.mudosa.musinsa.product.domain.vo.StockQuantity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관리자용 상품 관리 및 조회 등을 담당하는 서비스.
 */
@Service
@RequiredArgsConstructor
public class ProductCommandService {
	
	private final ProductRepository productRepository;
	private final OptionValueRepository optionValueRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
	private final BrandMemberRepository brandMemberRepository;
	private final ProductOptionRepository productOptionRepository;
	private final ImageRepository imageRepository;

	/**
	 * 커맨드 객체를 받아 상품과 하위 옵션을 생성한다.
	 */
	@Transactional
	public Long createProduct(ProductCreateRequest request,
						  Long brandId,
						  Long currentUserId) {

		// 1. 브랜드 조회 및 검증
		Brand brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BRAND_NOT_MATCHED));
		
		// 2. 브랜드 멤버 및 요청 검증
		validateBrandMember(brand.getBrandId(), currentUserId);
	
		// 3. 카테고리 조회 및 검증
		Category category = categoryRepository.findByPath(request.getCategoryPath());
        if (category == null) {
            throw new BusinessException(ErrorCode.PRODUCT_CATEGORY_REQUIRED);
        }

		// 4. 옵션 값 요청 목록 로드
		List<ProductCreateRequest.OptionCreateRequest> optionRequests = request.getOptions();

		// 5. 옵션 값 ID 목록 로드
		Set<Long> optionValueIds = optionRequests.stream()
			.filter(Objects::nonNull)
			.flatMap(spec -> spec.getOptionValueIds().stream())
			.collect(Collectors.toSet());
		
		// 6. 옵션 값 엔티티 매핑
		Map<Long, OptionValue> optionValueMap = loadOptionValuesByIds(new ArrayList<>(optionValueIds));

		// 7. 옵션 목록에서 대표 가격(default_price)을 결정한다. 우선순위: 최저 옵션가
		BigDecimal defaultPrice = optionRequests.stream()
			.map(ProductCreateRequest.OptionCreateRequest::getProductPrice)
			.filter(Objects::nonNull)
			.min(BigDecimal::compareTo)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_PRICE_REQUIRED));
		
		// 8. 썸네일 이미지 결정 (명시적 썸네일이 없으면 첫 번째 이미지 사용)
		String thumbnailUrl = resolveThumbnailUrl(request.getImages());
		
		// 9. 상품 및 하위 엔티티 생성
		Product product = Product.create(
			brand,
			request.getProductName(),
			request.getProductInfo(),
			request.getProductGenderType(),
			brand.getNameKo(),
			category.buildPath(),
			request.getIsAvailable(),
			defaultPrice,
			thumbnailUrl,
			Collections.emptyList(),
			Collections.emptyList()
		);

		// 10. 이미지 및 옵션 추가
		request.getImages().forEach(image -> {
			Image img = Image.create(product, image.getImageUrl(), image.getIsThumbnail());
			product.addImage(img);
		});

		// 11. 옵션 생성 및 추가
		Set<OptionCombination> optionCombinations = new HashSet<>();
		optionRequests.forEach(option -> {
			OptionCombination combination = resolveCombination(option.getOptionValueIds(), optionValueMap);
			if (!optionCombinations.add(combination)) {
				throw new BusinessException(ErrorCode.DUPLICATE_PRODUCT_OPTION_COMBINATION, "동일한 사이즈/색상 조합의 옵션이 이미 존재합니다.");
			}
			Money price = option.getProductPrice() != null ? new Money(option.getProductPrice()) : null;
			ProductOption productOption = createProductOption(
				product,
				price,
				option.getStockQuantity(),
				option.getOptionValueIds(),
				optionValueMap
			);
			product.addProductOption(productOption);
		});

		// 12. 상품 저장 및 ID 반환
		Product saved = productRepository.save(product);
		return saved.getProductId();
	}

	/**
	 * 상품 기본 정보를 갱신한다.
	 */
	@Transactional
	public ProductDetailResponse updateProduct(Long brandId,
											   Long productId,
											   ProductUpdateRequest request,
											   Long currentUserId) {
		// 1. 브랜드 멤버 권한 검증
		validateBrandMember(brandId, currentUserId);

		// 2. 상품 조회 및 브랜드 소유권 검증
		Product product = findProductForBrandOrThrow(productId, brandId);

		// 3. 수정 가능한 필드가 없으면 예외 처리
		if (!request.hasUpdatableField()) {
			throw new BusinessException(ErrorCode.PRODUCT_NOTHING_TO_UPDATE);
		}

		// 4. 상품 정보 갱신 검증
		boolean changed = product.updateBasicInfo(
			request.getProductName(),
			request.getProductInfo()
		);

		// 5. 판매 가능 여부 갱신 검증
		if (request.getIsAvailable() != null) {
			product.changeAvailability(request.getIsAvailable());
			changed = true;
		}

		// 6. 이미지 갱신 검증
		if (request.getImages() != null) {
			if (request.getImages().isEmpty()) {
				throw new BusinessException(ErrorCode.IMAGE_REQUIRED, "상품 이미지는 최소 1장 이상 등록해야 합니다.");
			}

			// 이미지는 레포로 delete 한 후 재등록
			imageRepository.deleteByProductId(productId);
			product.getImages().clear();
			request.getImages().forEach(image -> {
				Image img = Image.create(product, image.getImageUrl(), Boolean.TRUE.equals(image.getIsThumbnail()));
				product.addImage(img);
			});
			product.changeThumbnailImage(resolveThumbnailUrlForUpdate(request.getImages()));
			changed = true;
		}

		// 7. 변경된 항목이 없으면 예외 처리
		if (!changed) {
			throw new BusinessException(ErrorCode.PRODUCT_NO_CHANGES_DETECTED);
		}

		// 8. 결과 상세 정보 반환
		return ProductCommandMapper.toProductDetail(product);
	}

	/**
	 * 상품 옵션을 추가하고 결과 상세 정보를 반환한다.
	 */
	@Transactional
	public ProductDetailResponse.OptionDetail addProductOption(Long brandId,
												       Long productId,
												       ProductOptionCreateRequest request,
												       Long currentUserId) {
		// 1. 브랜드 멤버 권한 검증
		validateBrandMember(brandId, currentUserId);
		
		// 2. 상품 조회 및 브랜드 소유권 검증 (옵션 추가는 동시성 제어 필요)
		Product product = findProductForBrandOrThrow(productId, brandId, true);

		// 3. 옵션 값 엔티티 매핑
		Map<Long, OptionValue> optionValueMap = loadOptionValuesByIds(request.getOptionValueIds());

		// 4. 동일 옵션 조합 중복 검증
		OptionCombination newCombination = resolveCombination(request.getOptionValueIds(), optionValueMap);
		if (productOptionRepository.existsByProductIdAndOptionValueIds(
			product.getProductId(),
			newCombination.getSizeOptionValueId(),
			newCombination.getColorOptionValueId())) {
			throw new BusinessException(ErrorCode.DUPLICATE_PRODUCT_OPTION_COMBINATION, "동일한 사이즈/색상 조합의 옵션이 이미 존재합니다.");
		}

		// 5. 옵션 생성
		Money optionPrice = request.getProductPrice() != null ? new Money(request.getProductPrice()) : null;
		ProductOption productOption = createProductOption(
			product,
			optionPrice,
			request.getStockQuantity(),
			request.getOptionValueIds(),
			optionValueMap
		);
		
		// 6. 상품에 옵션 추가 및 대표 가격 갱신
		product.addProductOption(productOption);
		if (request.getProductPrice() != null) {
			product.applyLowerDefaultPrice(request.getProductPrice());
		}
		return ProductCommandMapper.toOptionDetail(productOption);
	}
	
	/**
	 * 브랜드 매니저용: 해당 브랜드의 모든 상품 목록을 조회한다 (isAvailable=false 포함)
	 */
	@Transactional(readOnly = true)
	public List<ProductManagerResponse> getBrandProductsForManager(Long brandId, Long currentUserId) {

		// 1. 브랜드 멤버 권한 검증
		validateBrandMember(brandId, currentUserId);

		// 2. 브랜드 소유의 모든 상품 조회
		List<Product> products = productRepository.findAllByBrandForManager(brandId);
		
		// 3. 매핑 후 반환
		return products.stream()
			.map(ProductCommandMapper::toManagerResponse)
			.collect(Collectors.toList());
	}

	/**
	 * 브랜드 매니저용: 특정 상품 상세 정보를 조회한다 (isAvailable=false 포함)
	 */
	@Transactional(readOnly = true)
	public ProductManagerResponse getProductDetailForManager(Long brandId, Long productId, Long currentUserId) {
		
		// 1. 브랜드 멤버 권한 검증
		validateBrandMember(brandId, currentUserId);
		
		// 2. 브랜드 소유의 특정 상품 조회
		Product product = findProductForBrandOrThrow(productId, brandId);
		
		// 3. 매핑 후 반환
		return ProductCommandMapper.toManagerResponse(product);
	}

	// 브랜드 멤버 권한을 검증한다.
	private void validateBrandMember(Long brandId, Long userId) {
		if (!brandMemberRepository.existsByBrand_BrandIdAndUserId(brandId, userId)) {
			throw new BusinessException(ErrorCode.NOT_BRAND_MEMBER);
		}
	}

	// 브랜드 소유의 상품을 조회하고 소유권까지 한 번에 검증한다.
	private Product findProductForBrandOrThrow(Long productId, Long brandId) {
		return findProductForBrandOrThrow(productId, brandId, false);
	}

	// 락 옵션을 포함한 브랜드 소유의 상품 조회 및 소유권 검증
	private Product findProductForBrandOrThrow(Long productId, Long brandId, boolean lock) {
		Product product = (lock
			? productRepository.findDetailByIdForManagerWithLock(productId, brandId)
			: productRepository.findDetailByIdForManager(productId, brandId))
			.orElseThrow(() -> new BusinessException(
				ErrorCode.PRODUCT_NOT_FOUND));
		return product;
	}

	// 옵션 값 ID 목록에 해당하는 옵션 값 엔티티들을 로드한다.
	private Map<Long, OptionValue> loadOptionValuesByIds(List<Long> optionValueIds) {
		if (optionValueIds == null || optionValueIds.isEmpty()) {
			return Collections.emptyMap();
		}

		// 1. 중복 제거
		Set<Long> uniqueOptionValueIds = new HashSet<>(optionValueIds);

		// 2. 옵션 값 엔티티 로드 및 매핑
		Map<Long, OptionValue> optionValueMap = optionValueRepository.findAllByOptionValueIdIn(new ArrayList<>(uniqueOptionValueIds))
			.stream()
			.collect(Collectors.toMap(OptionValue::getOptionValueId, Function.identity()));

		// 3. 누락된 옵션 값 ID 검증
		if (optionValueMap.size() != uniqueOptionValueIds.size()) {
			Set<Long> missingIds = new HashSet<>(uniqueOptionValueIds);
			missingIds.removeAll(optionValueMap.keySet());
			throw new BusinessException(ErrorCode.INVALID_PRODUCT_OPTION_VALUE_IDS, missingIds.toString());
		}

		return optionValueMap;
	}

	// 상품 옵션 및 관련 엔티티를 생성한다.
	private ProductOption createProductOption(Product product, 
											  Money productPrice, 
											  Integer stockQuantity, 
											  List<Long> optionValueIds,
											  Map<Long, OptionValue> optionValueMap) {
		// 1. 재고 엔티티 생성										
		Inventory inventory = Inventory.create(new StockQuantity(stockQuantity));

		// 2. OptionValue 엔티티 매핑
		List<OptionValue> optionValues = optionValueIds.stream()
			.map(optionValueMap::get)
			.collect(Collectors.toList());
		
		// 3. 상품 옵션 및 옵션 값 엔티티 생성
		ProductOption productOption = ProductOption.create(product, productPrice, inventory);
		
		// 4. 옵션 값 엔티티 생성 및 추가
		optionValues.forEach(optionValue -> {
			ProductOptionValue productOptionValue = ProductOptionValue.create(productOption, optionValue);
			productOption.addOptionValue(productOptionValue);
		});
		
		return productOption;
	}

	// 옵션 값 ID 목록으로부터 옵션 조합을 해석한다.
	private OptionCombination resolveCombination(List<Long> optionValueIds, Map<Long, OptionValue> optionValueMap) {
		if (optionValueIds == null) {
			throw new BusinessException(ErrorCode.PRODUCT_OPTION_REQUIRED_SIZE_AND_VALUE);
		}
		if (optionValueIds.size() < OptionCombination.REQUIRED_OPTION_COUNT) {
			throw new BusinessException(ErrorCode.PRODUCT_OPTION_REQUIRED_SIZE_AND_VALUE);
		}
		if (optionValueIds.size() > OptionCombination.REQUIRED_OPTION_COUNT) {
			throw new BusinessException(ErrorCode.PRODUCT_OPTION_REQUIRED_ONE_SIZE_AND_VALUE);
		}
		Long sizeId = null;
		Long colorId = null;
		for (Long optionValueId : optionValueIds) {
			OptionValue optionValue = optionValueMap.get(optionValueId);
			String optionName = normalizeOptionName(optionValue.getOptionName());
			if (OptionCombination.SIZE_OPTION_NAME.equals(optionName)) {
				if (sizeId != null) {
					throw new BusinessException(ErrorCode.PRODUCT_OPTION_REQUIRED_ONE_SIZE_AND_VALUE);
				}
				sizeId = optionValue.getOptionValueId();
				continue;
			}
			if (OptionCombination.COLOR_OPTION_NAME.equals(optionName)) {
				if (colorId != null) {
					throw new BusinessException(ErrorCode.PRODUCT_OPTION_REQUIRED_ONE_SIZE_AND_VALUE);
				}
				colorId = optionValue.getOptionValueId();
				continue;
			}
			throw new BusinessException(ErrorCode.INVALID_PRODUCT_OPTION_VALUE);
		}
		if (sizeId == null || colorId == null) {
			throw new BusinessException(ErrorCode.PRODUCT_OPTION_REQUIRED_SIZE_AND_VALUE);
		}
		return new OptionCombination(sizeId, colorId);
	}

	// 옵션 이름을 정규화한다.
	private String normalizeOptionName(String optionName) {
		return optionName == null ? null : optionName.trim();
	}

	// 옵션 조합을 나타내는 내부 클래스
	private static final class OptionCombination {
		private static final String SIZE_OPTION_NAME = "사이즈";
		private static final String COLOR_OPTION_NAME = "색상";
		private static final int REQUIRED_OPTION_COUNT = 2;

		private final Long sizeOptionValueId;
		private final Long colorOptionValueId;

		private OptionCombination(Long sizeOptionValueId, Long colorOptionValueId) {
			this.sizeOptionValueId = sizeOptionValueId;
			this.colorOptionValueId = colorOptionValueId;
		}

		Long getSizeOptionValueId() {
			return sizeOptionValueId;
		}

		Long getColorOptionValueId() {
			return colorOptionValueId;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			OptionCombination that = (OptionCombination) o;
			return Objects.equals(sizeOptionValueId, that.sizeOptionValueId)
				&& Objects.equals(colorOptionValueId, that.colorOptionValueId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(sizeOptionValueId, colorOptionValueId);
		}
	}

	// 썸네일 URL을 결정한다. 명시적 썸네일이 없으면 첫 번째 이미지를 사용한다.
	private String resolveThumbnailUrl(List<ProductCreateRequest.ImageCreateRequest> images) {
		return resolveThumbnailUrl(images, ProductCreateRequest.ImageCreateRequest::getIsThumbnail,
			ProductCreateRequest.ImageCreateRequest::getImageUrl);
	}

	// 상품 수정 시 썸네일 URL을 결정한다. 명시적 썸네일이 없으면 첫 번째 이미지를 사용한다.
	private String resolveThumbnailUrlForUpdate(List<ProductUpdateRequest.ImageUpdateRequest> images) {
		return resolveThumbnailUrl(images, ProductUpdateRequest.ImageUpdateRequest::getIsThumbnail,
			ProductUpdateRequest.ImageUpdateRequest::getImageUrl);
	}

	private <T> String resolveThumbnailUrl(List<T> images, Function<T, Boolean> isThumbnailGetter,
										   Function<T, String> imageUrlGetter) {
		if (images == null || images.isEmpty()) {
			throw new BusinessException(ErrorCode.IMAGE_REQUIRED);
		}
		return images.stream()
			.filter(img -> Boolean.TRUE.equals(isThumbnailGetter.apply(img)))
			.map(imageUrlGetter)
			.findFirst()
			.orElseGet(() -> imageUrlGetter.apply(images.get(0)));
	}

}
