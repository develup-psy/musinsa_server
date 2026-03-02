package com.mudosa.musinsa.product.domain.repository;

import com.mudosa.musinsa.product.domain.model.InventoryOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryOutboxEventRepository
        extends JpaRepository<InventoryOutboxEvent, Long>, InventoryOutboxEventRepositoryCustom {
}
