package com.mudosa.musinsa.product.domain.repository;

import com.mudosa.musinsa.product.domain.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long>, CartItemRepositoryCustom{

    List<CartItem> findAllByUserId(Long userId);

    @Query("""
        select distinct c from CartItem c
            join fetch c.productOption po
            join fetch po.product p
            left join fetch po.inventory inv
            left join fetch po.productOptionValues pov
            left join fetch pov.optionValue ov
        where c.user.id = :userId
    """)
    List<CartItem> findAllWithDetailsByUserId(@Param("userId") Long userId);

    @Query("""
                select c from CartItem c
                where c.user.id = :userId
                    and c.productOption.productOptionId = :productOptionId
        """)
    Optional<CartItem> findByUserIdAndProductOptionId(
                    @Param("userId") Long userId,
                    @Param("productOptionId") Long productOptionId
    );
}
