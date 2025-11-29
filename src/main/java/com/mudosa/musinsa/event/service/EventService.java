package com.mudosa.musinsa.event.service;

import com.mudosa.musinsa.event.model.Event;
import com.mudosa.musinsa.event.model.EventImage;
import com.mudosa.musinsa.event.model.EventOption;
import com.mudosa.musinsa.event.presentation.dto.res.EventListResDto;
import com.mudosa.musinsa.event.presentation.dto.res.EventOptionResDto;
import com.mudosa.musinsa.event.repository.EventImageRepository;
import com.mudosa.musinsa.event.repository.EventOptionRepository;
import com.mudosa.musinsa.event.repository.EventRepository;
import com.mudosa.musinsa.product.domain.model.ProductOption;
import com.mudosa.musinsa.event.model.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final EventOptionRepository eventOptionRepository;
    private final EventImageRepository eventImageRepository;


    //이벤트 목록 조회( 타입별 ) GET - resDto를 통해서 반환

    /*
    * 이벤트 타입에 맞는 이벤트 목록을 반환하는 메서드
    * ✅ N+1 문제 해결: Fetch Join을 사용하여 한 번의 쿼리로 모든 연관 데이터 로드
    *  */
    public List<EventListResDto> getEventListByType(Event.EventType eventType) {

        LocalDateTime now = LocalDateTime.now();

        // ✅ Fetch Join 사용 (201회 쿼리 → 1회 쿼리)
        List<Event> events = eventRepository.findAllByEventTypeWithRelations(eventType);
        return events.stream()
                .map(event -> mapEventToDto(event,now))
                .collect(Collectors.toList());//이벤트 상태 계산하고 DTO로 변환, 메서드 참조는 기존에 정의된 메서드를 직접 참조
    }

    //이벤트 목록 조회 ( 날짜와 상태에 맞춰서 필터링 ) GET - resDto를 통해서 반환

    public List<EventListResDto> getFilteredEventList(LocalDateTime currentTime) {
        List<Event> events = eventRepository.findAll();
        return events.stream()
                //필터링 해주는게 이 stream에서 filter() 연산을 통해서 !
                .filter(event ->event.getStartedAt().isAfter(currentTime)) //LocalDateTime 클래스 내장 메서드
                .map(event -> mapEventToDto(event,currentTime)) // event라는 매개변수를 받아서 메서드를 호출하는 익명함수
                .collect(Collectors.toList());
    }

    // 이벤트 객체를 DTO 로 변환한다.
    // ✅ N+1 문제 해결: 이미 Fetch Join으로 로드된 데이터를 사용 (추가 쿼리 없음)

    private EventListResDto mapEventToDto(Event event, LocalDateTime currentTime) {
        EventStatus status = EventStatus.calculateStatus(event, currentTime);

        // ✅ 이미 로드된 eventImages에서 썸네일 찾기 (추가 쿼리 없음)
        String thumbnailUrl = event.getEventImages().stream()
                .filter(EventImage::getIsThumbnail)
                .findFirst()
                .map(EventImage::getImageUrl)
                .orElse(null);

        // ✅ 이미 로드된 eventOptions와 productOption, product 사용 (추가 쿼리 없음)
        List<EventOptionResDto> optionDtos = event.getEventOptions().stream()
                .map(eo -> new EventOptionResDto(
                        eo.getId(),                                      // optionId
                        eo.getProductOption().getProductOptionId(),      // productOptionId
                        eo.getProductOption().getProduct().getProductName(),  // productName
                        null,                                            // optionLabel (미사용)
                        eo.getProductOption().getProductPrice() != null
                                ? eo.getProductOption().getProductPrice().getAmount()
                                : null,
                        eo.getEventPrice(),                              // eventPrice
                        eo.getEventStock(),                              // eventStock
                        eo.getProductOption().getProduct().getProductId()     // productId
                ))
                .toList();


        return EventListResDto.from(event, optionDtos, thumbnailUrl, status);

    }





}
