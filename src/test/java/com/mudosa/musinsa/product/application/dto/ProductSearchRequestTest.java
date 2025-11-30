package com.mudosa.musinsa.product.application.dto;

import com.mudosa.musinsa.product.domain.model.ProductGenderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductSearchRequest 파싱 테스트")
class ProductSearchRequestTest {

	@Test
	@DisplayName("정상 문자열 값을 enum으로 변환해 조건을 만든다.")
	void toCondition_parsesValidValues() {
		ProductSearchRequest request = new ProductSearchRequest();
		request.setKeyword("티셔츠");
		request.setCategoryPaths(List.of("상의>티셔츠", "상의>맨투맨"));
		request.setGender("men");
		request.setBrandId(5L);
		request.setPriceSort("lowest");
		request.setCursor("1000:10");
		request.setLimit(20);

		ProductSearchCondition condition = request.toCondition();

		assertThat(condition.getKeyword()).isEqualTo("티셔츠");
		assertThat(condition.getCategoryPaths()).containsExactly("상의>티셔츠", "상의>맨투맨");
		assertThat(condition.getGender()).isEqualTo(ProductGenderType.MEN);
		assertThat(condition.getBrandId()).isEqualTo(5L);
		assertThat(condition.getPriceSort()).isEqualTo(ProductSearchCondition.PriceSort.LOWEST);
		assertThat(condition.getCursor()).isEqualTo("1000:10");
		assertThat(condition.getLimit()).isEqualTo(20);
	}

	@Test
	@DisplayName("잘못된 enum 문자열은 null로 처리한다.")
	void toCondition_handlesInvalidEnumValues() {
		ProductSearchRequest request = new ProductSearchRequest();
		request.setGender("invalid");
		request.setPriceSort("wrong");

		ProductSearchCondition condition = request.toCondition();

		assertThat(condition.getGender()).isNull();
		assertThat(condition.getPriceSort()).isNull();
	}

	@Test
	@DisplayName("카테고리 리스트는 defensive copy 되어도 getter는 동일 순서로 반환한다.")
	void toCondition_categoryPathsDefensiveCopy() {
		ProductSearchRequest request = new ProductSearchRequest();
		List<String> original = List.of("상의>티셔츠");
		request.setCategoryPaths(original);

		ProductSearchCondition condition = request.toCondition();
		assertThat(condition.getCategoryPaths()).containsExactly("상의>티셔츠");
	}
}
