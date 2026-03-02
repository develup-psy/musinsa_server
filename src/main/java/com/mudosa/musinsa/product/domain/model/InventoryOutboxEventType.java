package com.mudosa.musinsa.product.domain.model;

public enum InventoryOutboxEventType {
    DECREASE(-1),
    INCREASE(1);

    private final int direction;

    InventoryOutboxEventType(int direction) {
        this.direction = direction;
    }

    public int toDelta(int quantity) {
        return direction * quantity;
    }
}
