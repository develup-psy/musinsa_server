package com.mudosa.musinsa.product.domain.repository;

import com.mudosa.musinsa.product.domain.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long>{

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from ProductOption po join po.inventory i where po.productOptionId = :productOptionId")
	Optional<Inventory> findByProductOptionIdWithLock(@Param("productOptionId") Long productOptionId);

	@Query("select i from ProductOption po join po.inventory i where po.productOptionId = :productOptionId")
	Optional<Inventory> findByProductOptionId(@Param("productOptionId") Long productOptionId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			UPDATE inventory i
			JOIN product_option po ON po.inventory_id = i.inventory_id
			SET i.stock_quantity = i.stock_quantity + :delta
			WHERE po.product_option_id = :productOptionId
			  AND i.stock_quantity + :delta >= 0
			""", nativeQuery = true)
	int applyStockChangeByProductOptionId(
			@Param("productOptionId") Long productOptionId,
			@Param("delta") int delta
	);
}
