package com.mudosa.musinsa.exception;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public enum ErrorCode {

  INVALID_PARAMETER("00001","파라미터가 유효하지 않습니다",HttpStatus.BAD_REQUEST ),

  // auth
  VALIDATION_ERROR("10001", "입력 값 검증 오류입니다.", HttpStatus.BAD_REQUEST),
  INTERNAL_SERVER_ERROR("10002", "내부 서버 오류입니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  UNAUTHORIZED_USER("10003", "인증되지 않은 사용자입니다.", HttpStatus.UNAUTHORIZED),
  EXPIRED_JWT("10004", "JWT 토큰이 만료되었습니다.", HttpStatus.UNAUTHORIZED),
  INVALID_JWT("10005", "잘못된 JWT 토큰입니다.", HttpStatus.UNAUTHORIZED),
  UNSUPPORTED_JWT("10006", "지원하지 않는 JWT 토큰입니다.", HttpStatus.UNAUTHORIZED),
  EMPTY_JWT("10007", "JWT 클레임이 비어있습니다.", HttpStatus.UNAUTHORIZED),
  FORBIDDEN("10008", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
  RESOURCE_NOT_FOUND("10009", "요청한 리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND), USER_NOT_FOUND("10010", "사용자가 없습니다", HttpStatus.NOT_FOUND),
  INVALID_CREDENTIALS("10011", "아이디 혹은 비밀번호가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
  INVALID_PHONE_NUMBER_FORMAT(
      "20005", "휴대폰 번호는 하이픈(-) 없이 10자리 또는 11자리 숫자로 입력해주세요. 예: 01012345678", HttpStatus.BAD_REQUEST),
  INVALID_EMAIL_FORMAT(
      "20006", "이메일 형식이 올바르지 않습니다. 예: example@example.com", HttpStatus.BAD_REQUEST),
  INVALID_PASSWORD_FORMAT("20004", "최소 8자, 영문자, 숫자, 특수문자 포함해야합니다.", HttpStatus.BAD_REQUEST),
  ALREADY_REGISTERED_EMAIL("20003", "이미 가입한 이메일입니다.", HttpStatus.CONFLICT),

  //payment
  PAYMENT_CREATE_FAILED("30001", "결제 생성에 실패했습니다", HttpStatus.BAD_REQUEST),
  PAYMENT_APPROVAL_FAILED("30011", "결제 승인에 실패했습니다", HttpStatus.BAD_REQUEST),
  PAYMENT_PG_NOT_FOUND("30002", "존재하지 않는 PG사 입니다", HttpStatus.BAD_REQUEST),
  PAYMENT_NOT_FOUND("30003", "존재하지 않는 결제입니다", HttpStatus.NOT_FOUND),
  PAYMENT_ALREADY_APPROVED("30004", "이미 승인된 결제입니다", HttpStatus.CONFLICT),
  PAYMENT_AMOUNT_MISMATCH("30005", "결제 금액이 일치하지 않습니다", HttpStatus.BAD_REQUEST),
  INVALID_PAYMENT_STATUS("30006", "결제 상태가 유효하지 않습니다", HttpStatus.BAD_REQUEST),
  INVALID_PG_TRANSACTION_ID("30007", "결제 PG 트랜잭션 ID가 유효하지 않습니다", HttpStatus.BAD_REQUEST),
  INVALID_PAYMENT_METHOD("30008", "결제수단이 유효하지 않습니다", HttpStatus.BAD_REQUEST),PAYMENT_STRATEGY_NOT_FOUND("30009","결제전략을 찾을 수 없습니다",HttpStatus.BAD_REQUEST ),
  PAYMENT_TIMEOUT("30010","결제 처리 시간 초과", HttpStatus.BAD_REQUEST),
  PAYMENT_SYSTEM_ERROR("30012","결제는 승인되었으나 후속 처리 중 오류가 발생했습니다.",HttpStatus.CONFLICT),
  PAYMENT_FAILED_BEFORE_PG_CONFIRM("30013","PG사 결제 승인 전 오류가 발생했습니다", HttpStatus.BAD_REQUEST ),
  PAYMENT_CANCEL_TIMEOUT("30014","PG사 결제 취소 타임아웃 오류가 발생했습니다.",HttpStatus.BAD_REQUEST ),
  PAYMENT_CANCEL_FAILED("30015","PG사 결제 취소 오류가 발생했습니다",HttpStatus.BAD_REQUEST),

  //order
  ORDER_NOT_FOUND("40001", "존재하지 않는 주문입니다", HttpStatus.NOT_FOUND),
  ORDER_ALREADY_COMPLETED("40002", "이미 완료된 주문입니다", HttpStatus.CONFLICT),
  ORDER_ITEM_NOT_FOUND("40003", "주문 상품을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
  INVALID_ORDER_STATUS("40004", "유효하지 않은 주문 상태 입니다.", HttpStatus.NOT_FOUND),
  INVALID_ORDER_STATUS_TRANSITION("40005", "허용되지 않는 주문 상태입니다.", HttpStatus.BAD_REQUEST),
  INVALID_DISCOUNT_AMOUNT("40006", "할인 적용이 유효하지 않습니다", HttpStatus.BAD_REQUEST),
  ORDER_INVALID_AMOUNT("40007", "주문 상품 가격이 유효하지 않습니다", HttpStatus.BAD_REQUEST),
  PRODUCT_OPTION_NOT_FOUND("40008", "상품 옵션을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
  ORDER_INSUFFICIENT_STOCK("40009", "재고가 부족한 상품이 있습니다", HttpStatus.BAD_REQUEST),
  ORDER_CREATE_FAIL("40010","주문 생성에 실패했습니다", HttpStatus.BAD_REQUEST),
  INVALID_PRODUCT_ORDER("40011","현재 판매 불가능한 상품이 포함되어 있습니다",HttpStatus.BAD_REQUEST ),
  CANNOT_CANCEL_ORDER("40012","취소할 수 없는 주문입니다",HttpStatus.BAD_REQUEST ),

  //inventory
  INVENTORY_NOT_FOUND("50001", "재고 정보를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
  INSUFFICIENT_STOCK("50002", "재고가 부족한 상품이 있습니다", HttpStatus.BAD_REQUEST),
  INVENTORY_REQUIRED("50003", "재고 정보는 필수입니다.", HttpStatus.BAD_REQUEST),

    //event
    EVENT_NOT_FOUND("50003", "이벤트를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    EVENT_NOT_OPEN("50004", "현재 진행 중인 이벤트가 아닙니다", HttpStatus.BAD_REQUEST),
    EVENT_USER_LIMIT_EXCEEDED("50005", "이벤트별 발급 한도를 초과했습니다", HttpStatus.CONFLICT),
    EVENT_STOCK_EMPTY("50006", "이벤트 재고가 모두 소진되었습니다", HttpStatus.CONFLICT),
    EVENT_ENTRY_CONFLICT("50007", "이벤트 참여 대기열에서 거절되었습니다", HttpStatus.TOO_MANY_REQUESTS),
    EVENT_PRODUCT_MISMATCH("50008", "이벤트에 매핑되지 않은 상품입니다", HttpStatus.BAD_REQUEST),
    EVENT_COUPON_NOT_ASSIGNED("50009", "이벤트에 쿠폰이 연결되어 있지 않습니다", HttpStatus.BAD_REQUEST),
  //coupon
    COUPON_NOT_FOUND("60001", "쿠폰을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    COUPON_ALREADY_USED("60002", "이미 사용된 쿠폰입니다", HttpStatus.BAD_REQUEST),
    COUPON_EXPIRED("60003", "만료된 쿠폰입니다", HttpStatus.BAD_REQUEST),
    COUPON_APPLIED_FALIED("60004", "쿠폰 적용에 실패했습니다", HttpStatus.BAD_REQUEST),
    INVALID_COUPON_TYPE("60005", "지원하지 않은 쿠폰 타입입니다", HttpStatus.BAD_REQUEST),
    COUPON_NOT_USED("60006", "사용되지 않은 쿠폰은 복구할 수 없습니다", HttpStatus.BAD_REQUEST),
    COUPON_ROLLBACK_INVALID("60007", "쿠폰이 다른 주무에서 사용되어 복구할 수 없습니다", HttpStatus.BAD_REQUEST),
    COUPON_OUT_OF_STOCK("60008", "쿠폰 재고가 모두 소진되었습니다", HttpStatus.CONFLICT),

  //brand
  BRAND_NOT_FOUND("70001", "브랜드를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
  BRAND_NOT_MATCHED("70002", "브랜드 정보가 일치하지 않습니다", HttpStatus.BAD_REQUEST),
  NOT_BRAND_PRODUCT("70003", "해당 브랜드의 상품이 아닙니다", HttpStatus.BAD_REQUEST),
  NOT_BRAND_MEMBER("70004", "해당 브랜드의 멤버가 아닙니다", HttpStatus.FORBIDDEN),
  BRAND_ID_REQUIRED("70005", "브랜드 아이디는 필수입니다.", HttpStatus.BAD_REQUEST),


  //product
  PRODUCT_OPTION_NOT_VALID("80001", "상품 옵션이 유효하지 않습니다", HttpStatus.BAD_REQUEST),
  DUPLICATE_PRODUCT_OPTION_COMBINATION("80002", "상품 옵션조합은 중복될 수 없습니다.", HttpStatus.BAD_REQUEST),
  DUPLICATE_PRODUCT_IMAGE("80003", "중복된 상품 이미지가 존재합니다", HttpStatus.BAD_REQUEST),
  PRODUCT_INFO_REQUIRED("80004", "상품 정보들은 비워둘 수 없습니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_OPTION_REQUIRED("80005", "상품 옵션은 필수입니다.", HttpStatus.BAD_REQUEST),
  CATEGORY_NOT_FOUND("80006", "카테고리를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
  PRODUCT_NOT_FOUND("80007", "상품을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
  PRODUCT_NAME_REQUIRED("80008", "상품 이름은 필수입니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_BRAND_REQUIRED("80009", "상품 브랜드는 필수입니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_GENDER_TYPE_REQUIRED("80010", "상품 성별 타입은 필수입니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_BRAND_NAME_REQUIRED("80011", "역정규화 브랜드 이름은 필수입니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_CATEGORY_PATH_REQUIRED("80012", "역정규화 카테고리 경로는 필수입니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_REQUIRED("80013", "상품은 필수입니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_OPTION_NOT_AVAILABLE("80014", "상품 옵션이 판매 가능한 상태가 아닙니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_OPTION_NOT_EXIST("80015", "요청한 상품 옵션이 존재하지 않습니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_CATEGORY_REQUIRED("80016", "상품 카테고리는 필수입니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_NOTHING_TO_UPDATE("80017", "변경할 항목이 없습니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_NO_CHANGES_DETECTED("80018", "상품 정보에 변경된 내용이 없습니다.", HttpStatus.BAD_REQUEST),

  //chat
  MESSAGE_OR_FILE_REQUIRED("110001", "메시지 또는 파일 중 하나는 반드시 포함되어야 합니다.", HttpStatus.BAD_REQUEST),
  MESSAGE_PARENT_NOT_FOUND("110002", "답장하고자 하는 메시지가 존재하지 않습니다.", HttpStatus.NOT_FOUND),
  FILE_SAVE_FAILED("111001", "파일 저장에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  FILE_UPLOAD_FAILED("111002", "파일 업로드 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

  CHAT_NOT_FOUND("100001", "해당 채팅방이 존재하지 않습니다.", HttpStatus.NOT_FOUND),
  CHAT_PARTICIPANT_NOT_FOUND("100001", "해당 채팅방에는 해당 참여자가 존재하지 않습니다.", HttpStatus.FORBIDDEN),
  CHAT_PARTICIPANT_ALREADY_EXISTS("100002", "이미 해당 채팅방에 참여 중인 사용자입니다.", HttpStatus.CONFLICT),

  //inventory
  INVENTORY_NOT_AVAILABLE("90001", "재고가 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  INVENTORY_STOCK_QUANTITY_REQUIRED("90002", "재고 수량은 필수입니다.", HttpStatus.BAD_REQUEST),
  INVENTORY_INSUFFICIENT_STOCK("90003", "재고가 부족합니다.", HttpStatus.BAD_REQUEST),
  INVALID_INVENTORY_UPDATE_VALUE("90004", "재고 변경 값은 0이 될 수 없습니다.", HttpStatus.BAD_REQUEST),
  INVENTORY_STOCK_QUANTITY_INVALID("90005", "재고 수량이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),

  //image
  IMAGE_REQUIRED("100001", "이미지는 필수입니다.", HttpStatus.BAD_REQUEST),
  THUMBNAIL_REQUIRED("10002","썸네일은 필수입니다.", HttpStatus.BAD_REQUEST),
  THUMBNAIL_ONLY_ONE("100003", "썸네일 이미지는 반드시 하나여야 합니다.", HttpStatus.BAD_REQUEST),
  URL_REQUIRED("100004", "이미지 URL은 필수입니다.", HttpStatus.BAD_REQUEST),

  //settlement
  SETTLEMENT_NOT_FOUND("A0001", "정산 정보를 찾을 수 없습니다", HttpStatus.NOT_FOUND),

  // cartitem
  CART_ITEM_USER_REQUIRED("B0001", "사용자는 필수입니다.", HttpStatus.BAD_REQUEST),
  CART_ITEM_PRODUCT_OPTION_REQUIRED("B0002", "상품 옵션은 필수입니다.", HttpStatus.BAD_REQUEST),
  CART_ITEM_QUANTITY_INVALID("B0003", "수량은 1개 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
  CART_ITEM_QUANTITY_REQUIRED("B0004", "수량은 필수입니다.", HttpStatus.BAD_REQUEST),
  CART_ITEM_NOT_FOUND("B0005", "장바구니 항목을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  INVALID_CART_ITEM_UPDATE_VALUE("B0006", "장바구니 항목 업데이트 값이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  CART_ITEM_ONLY_ACCESS_PERSONAL("B0007", "본인 장바구니 항목만 접근할 수 있습니다.", HttpStatus.FORBIDDEN),
  CART_ITEM_STOCK_QUANTITY_REQUIRED("B0008", "장바구니 항목의 재고 수량은 필수입니다.", HttpStatus.BAD_REQUEST),
  CART_ITEM_INSUFFICIENT_STOCK("B0009", "장바구니 항목의 재고가 부족합니다.", HttpStatus.BAD_REQUEST),

  //category
  CATEGORY_NAME_REQUIRED("C0001", "카테고리 이름은 필수입니다.", HttpStatus.BAD_REQUEST),


  // Option
  OPTION_NAME_REQUIRED("D0001", "옵션명은 필수입니다.", HttpStatus.BAD_REQUEST),
  OPTION_VALUE_REQUIRED("D0002", "옵션 값은 필수입니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_PRICE_REQUIRED("D0003", "상품 옵션 가격은 필수입니다.", HttpStatus.BAD_REQUEST),
  OPTION_VALUE_ID_REQUIRED("D0004", "옵션 값 식별자는 필수입니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_OPTION_ID_REQUIRED("D0005", "상품 옵션 식별자는 필수입니다.", HttpStatus.BAD_REQUEST),
  RECOVER_VALUE_INVALID("D0006", "복구 값이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_STOCK_QUANTITY_REQUIRED("D0008", "상품 옵션 재고 수량은 필수입니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_OPTION_VALUE_ID_REQUIRED("D0009", "상품 옵션 값 식별자는 최소 1개 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
  INVALID_PRODUCT_OPTION_VALUE_IDS("D0010", "상품 옵션 값 식별자가 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_OPTION_REQUIRED_SIZE_AND_VALUE("D0011", "상품 옵션은 각 색상과 사이즈의 값이 필요합니다.", HttpStatus.BAD_REQUEST),
  INVALID_PRODUCT_OPTION_VALUE("D0012", "상품 옵션 값이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  REQUIRED_TWO_DIFFERENT_OPTION_NAMES("D0013", "서로 다른 두 가지 옵션명이 필요합니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_OPTION_REQUIRED_ONE_SIZE_AND_VALUE("D0014", "상품 옵션은 각 색상과 사이즈의 값이 1개여야 합니다.", HttpStatus.BAD_REQUEST),
  PRODUCT_OPTION_OUT_OF_STOCK("D0015", "상품 옵션의 재고가 부족합니다.", HttpStatus.BAD_REQUEST),
  
  // user
  USER_ID_REQUIRED("E0001", "사용자 ID는 필수입니다.", HttpStatus.BAD_REQUEST),

  //stock
  STOCK_QUANTITY_INVALID("F0001", "재고 수량이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  STOCK_QUANTITY_REQUIRED("F0002", "재고 수량은 필수입니다.", HttpStatus.BAD_REQUEST),
  STOCK_QUANTITY_CANNOT_BE_NEGATIVE("F0003", "재고 수량은 음수가 될 수 없습니다.", HttpStatus.BAD_REQUEST),
  STOCK_QUANTITY_CANNOT_BE_NULL("F0004", "재고 수량은 null일 수 없습니다.", HttpStatus.BAD_REQUEST),
  STOCK_QUANTITY_OUT_OF_STOCK("F0005", "재고 수량이 부족합니다.", HttpStatus.BAD_REQUEST),
  STOCK_QUANTITY_CANNOT_BE_LESS_THAN_ONE("F0006", "재고 감소 수량은 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST);
  
  

  private final String code;
  private final String message;
  private final HttpStatus httpStatus;
}
