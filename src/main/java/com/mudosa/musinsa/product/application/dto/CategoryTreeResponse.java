package com.mudosa.musinsa.product.application.dto;

import lombok.Value;

import java.util.List;

@Value
public class CategoryTreeResponse {
    List<CategoryNode> categories;

    @Value
    public static class CategoryNode {
        Long categoryId;
        String categoryName;
        String categoryPath;
        String imageUrl;
        List<CategoryNode> children;
    }
}
