package com.mudosa.musinsa.chat.controller;

import com.mudosa.musinsa.chat.service.ChatService;
import com.mudosa.musinsa.settlement.domain.repository.SettlementDailyMapper;
import com.mudosa.musinsa.settlement.domain.repository.SettlementMonthlyMapper;
import com.mudosa.musinsa.settlement.domain.repository.SettlementPerTransactionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 컨트롤러 공통 테스트 설정 분리 (임시적으로 채팅 컨트롤러만)
@Slf4j
@WebMvcTest(controllers = ChatControllerImpl.class)
public abstract class ControllerTestSupport {
  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  ChatService chatService;

  /**
   * 실제로는 안 쓰지만, 프로젝트 전체에서 스캔돼서 요구하니까 임시 Mock
   */
  @MockitoBean
  SettlementDailyMapper settlementDailyMapper;
  @MockitoBean
  SettlementMonthlyMapper settlementMonthlyMapper;
  @MockitoBean
  SettlementPerTransactionMapper settlementPerTransactionMapper;
}