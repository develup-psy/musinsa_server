package com.mudosa.musinsa.product.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductCategory 플레이스홀더 테스트")
class ProductCategoryTest {

	@Test
	@DisplayName("Deprecated(forRemoval=true) 어노테이션이 유지된다.")
	void hasDeprecatedForRemovalAnnotation() {
		// given // when
		Deprecated annotation = ProductCategory.class.getAnnotation(Deprecated.class);

		// then
		assertThat(annotation).isNotNull();
		assertThat(annotation.forRemoval()).isTrue();
	}

	@Test
	@DisplayName("클래스는 final 로 선언되어 있다.")
	void isFinalClass() {
		// given // when
		int modifiers = ProductCategory.class.getModifiers();

		// then
		assertThat(Modifier.isFinal(modifiers)).isTrue();
	}

	@Test
	@DisplayName("생성자를 호출하면 IllegalStateException을 발생시킨다.")
	void constructorThrowsIllegalStateException() throws Exception {
		// given
		Constructor<ProductCategory> ctor = ProductCategory.class.getDeclaredConstructor();
		ctor.setAccessible(true);

		// when // then
		assertThatThrownBy(ctor::newInstance)
			.hasCauseInstanceOf(IllegalStateException.class)
			.satisfies(ex -> assertThat(ex.getCause()).hasMessageContaining("deprecated"));
	}
}
