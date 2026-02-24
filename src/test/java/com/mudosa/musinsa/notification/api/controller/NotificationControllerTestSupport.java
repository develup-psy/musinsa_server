package com.mudosa.musinsa.notification.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mudosa.musinsa.notification.controller.NotificationController;
import com.mudosa.musinsa.notification.service.NotificationService;
import com.mudosa.musinsa.settlement.domain.repository.SettlementDailyMapper;
import com.mudosa.musinsa.settlement.domain.repository.SettlementMonthlyMapper;
import com.mudosa.musinsa.settlement.domain.repository.SettlementPerTransactionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Slf4j
@WebMvcTest(controllers = NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
public abstract class NotificationControllerTestSupport {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;


    @MockitoBean
    NotificationService notificationService;

    @MockitoBean
    SettlementDailyMapper settlementDailyMapper;
    @MockitoBean
    SettlementMonthlyMapper settlementMonthlyMapper;
    @MockitoBean
    SettlementPerTransactionMapper settlementPerTransactionMapper;

}
