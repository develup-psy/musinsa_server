package com.mudosa.musinsa.event.presentation.dto.res;

import com.mudosa.musinsa.product.domain.model.Product;
import com.mudosa.musinsa.event.model.EventOption;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@NoArgsConstructor


public class EventOptionResDto {

    private Long optionId;
    private Long productOptionId;
    private String productName; //상품명
    private String OptionLabel; //옵션명 ( 아직 null 상태 )
    private BigDecimal productPrice; // 상품 옵션 가격
    private BigDecimal eventPrice; // 이벤트 가격
    private Integer eventStock;
    private Long productId; // 상품 상세 리스트를 위한 매핑 추가

    // private String description; 필요한가?

    public static EventOptionResDto from(EventOption eo, String productName, String optionLabel,Long productOptionId, Long productId) {
        //[static]: 클래스 레벨에 속한다. 인스턴스(객체)를 만들지 않아도 클래스 이름으로 직접 호출 가능
        BigDecimal productPrice = eo.getProductOption() != null && eo.getProductOption().getProductPrice() != null
                ? eo.getProductOption().getProductPrice().getAmount()
                : null;

        return new EventOptionResDto(
                eo.getId(),
                productOptionId,
                productName,
                optionLabel,
                productPrice,
                eo.getEventPrice(),
                eo.getEventStock(),
                productId
        );
    }





}
