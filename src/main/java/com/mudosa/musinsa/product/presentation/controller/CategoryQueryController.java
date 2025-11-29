package com.mudosa.musinsa.product.presentation.controller;

import com.mudosa.musinsa.product.application.ProductQueryService;
import com.mudosa.musinsa.product.application.dto.CategoryTreeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryQueryController {

    private final ProductQueryService productQueryService;

    @GetMapping("/tree")
    public ResponseEntity<CategoryTreeResponse> getCategoryTree() {
        return ResponseEntity.ok(productQueryService.getCategoryTree());
    }
}
