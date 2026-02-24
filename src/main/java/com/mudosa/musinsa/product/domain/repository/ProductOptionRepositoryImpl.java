package com.mudosa.musinsa.product.domain.repository;

import com.mudosa.musinsa.product.domain.model.ProductOption;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.micrometer.observation.annotation.Observed;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.mudosa.musinsa.product.domain.model.QInventory.inventory;
import static com.mudosa.musinsa.product.domain.model.QProduct.product;
import static com.mudosa.musinsa.product.domain.model.QProductOption.productOption;

@Repository
@RequiredArgsConstructor
public class ProductOptionRepositoryImpl implements ProductOptionRepositoryCustom {
    private final JPAQueryFactory jpaQueryFactory;

    @Observed(name = "repository.findWithPessimisticLock", contextualName = "상품옵션-비관락-조회")
    @Override
    public List<ProductOption> findByProductOptionIdInWithPessimisticLock(List<Long> productOptionIds) {
        return jpaQueryFactory
                .selectFrom(productOption)
                .join(productOption.inventory, inventory).fetchJoin()
                .where(productOption.productOptionId.in(productOptionIds))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetch();
    }

    @Observed(name = "repository.findProductOptionByIds", contextualName = "상품옵션-조회-QueryDSL")
    @Override
    public List<ProductOption> findByProductOptionIdIn(List<Long> productOptionIds) {
        return jpaQueryFactory
                .selectFrom(productOption)
                .join(productOption.product, product).fetchJoin()
                .join(productOption.inventory, inventory).fetchJoin()
                .where(productOption.productOptionId.in(productOptionIds))
                .fetch();
    }


}
