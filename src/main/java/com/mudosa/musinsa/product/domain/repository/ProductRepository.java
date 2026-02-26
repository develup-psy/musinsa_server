package com.mudosa.musinsa.product.domain.repository;

import com.mudosa.musinsa.brand.domain.model.Brand;
import com.mudosa.musinsa.product.domain.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// 상품 애그리거트 기본 접근을 담당하며 복잡한 조회는 커스텀 리포지토리로 위임한다.
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

  // 브랜드 매니저용: 특정 브랜드의 모든 상품 조회 (isAvailable 상관없이)
  @Query("""
  select distinct p
  from Product p
  left join fetch p.brand
  left join fetch p.images
  where p.brand.brandId = :brandId
  order by p.productId asc
  """)
  List<Product> findAllByBrandForManager(Long brandId);

  // 브랜드 매니저용: 특정 브랜드의 상품 상세 조회 (isAvailable 상관없이)
  @Query("""
  select distinct p
  from Product p
  left join fetch p.brand
  left join fetch p.images
  where p.productId = :productId
  and p.brand.brandId = :brandId
  """)
  Optional<Product> findDetailByIdForManager(Long productId, Long brandId);

  // 옵션 추가 등 동시성 제어가 필요한 경우 비관적 락으로 조회한다.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
  select distinct p
  from Product p
  left join fetch p.brand
  left join fetch p.images
  where p.productId = :productId
  and p.brand.brandId = :brandId
  """)
  Optional<Product> findDetailByIdForManagerWithLock(Long productId, Long
  brandId);

  List<Product> findTop6ByBrandOrderByCreatedAtDesc(Brand brand);
}
