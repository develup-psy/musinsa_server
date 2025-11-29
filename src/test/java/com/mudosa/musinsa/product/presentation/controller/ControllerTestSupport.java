package com.mudosa.musinsa.product.presentation.controller;

import com.mudosa.musinsa.product.application.CartService;
import com.mudosa.musinsa.product.application.ProductCommandService;
import com.mudosa.musinsa.product.application.ProductInventoryService;
import com.mudosa.musinsa.product.application.ProductQueryService;
import com.mudosa.musinsa.settlement.domain.repository.SettlementDailyMapper;
import com.mudosa.musinsa.settlement.domain.repository.SettlementMonthlyMapper;
import com.mudosa.musinsa.settlement.domain.repository.SettlementPerTransactionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// product 컨트롤러 공통 WebMvc 테스트 설정
@WebMvcTest(controllers = {CartController.class, CategoryQueryController.class, ProductQueryController.class, 
        ProductCommandController.class})
@Import(ControllerTestSupport.TestMethodSecurityConfig.class)
public abstract class ControllerTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected CartService cartService;
    @MockitoBean
    protected ProductQueryService productQueryService;
    @MockitoBean
    protected ProductCommandService productCommandService;
    @MockitoBean
    protected ProductInventoryService productInventoryService;

    /**
    * 실제로는 안 쓰지만, 프로젝트 전체에서 스캔돼서 요구하니까 임시 Mock
    */
    @MockitoBean
    protected SettlementDailyMapper settlementDailyMapper;
    @MockitoBean
    protected SettlementMonthlyMapper settlementMonthlyMapper;
    @MockitoBean
    protected SettlementPerTransactionMapper settlementPerTransactionMapper;

    @Configuration
    @EnableMethodSecurity
    static class TestMethodSecurityConfig {
    }
}
