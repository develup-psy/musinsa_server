package com.mudosa.musinsa.coupon.domain.repository;

import com.mudosa.musinsa.ServiceConfig;
import com.mudosa.musinsa.coupon.model.Coupon;
import com.mudosa.musinsa.coupon.model.DiscountType;
import com.mudosa.musinsa.coupon.model.MemberCoupon;
import com.mudosa.musinsa.coupon.repository.CouponRepository;
import com.mudosa.musinsa.coupon.repository.MemberCouponRepository;
import com.mudosa.musinsa.order.application.dto.OrderMemberCoupon;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.model.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class MemberCouponRepositoryImplTest extends ServiceConfig {

    @Autowired
    private MemberCouponRepository memberCouponRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CouponRepository couponRepository;

    @DisplayName("")
    @Test
    void findOrderMemberCouponsByUserId(){
        //given
        User user = createAndSaveUser();

        Coupon coupon1 = createCoupon("새로운 쿠폰1");
        Coupon coupon2 = createCoupon("새로운 쿠폰2");
        Coupon coupon3 = createCoupon("새로운 쿠폰3");

        couponRepository.saveAll(List.of(coupon1, coupon2, coupon3));

        MemberCoupon memberCoupon1 = MemberCoupon.issue(user.getId(), coupon1);
        MemberCoupon memberCoupon2 = MemberCoupon.issue(user.getId(), coupon2);
        MemberCoupon memberCoupon3 = MemberCoupon.issue(user.getId(), coupon3);

        memberCouponRepository.saveAll(List.of(memberCoupon1, memberCoupon2, memberCoupon3));

        //when
        List<OrderMemberCoupon> memberCoupons =  memberCouponRepository.findOrderMemberCouponsByUserId(user.getId());

        //then
        assertThat(memberCoupons)
                .hasSize(3)
                .extracting("couponName")
                .containsExactlyInAnyOrder(
                        "새로운 쿠폰1",
                        "새로운 쿠폰2",
                        "새로운 쿠폰3"
                );
    }

    private User createAndSaveUser() {
        User user = User.builder()
                .userName("testUser")
                .password("password123")
                .userEmail("test@example.com")
                .contactNumber("010-1234-5678")
                .role(UserRole.USER)
                .currentAddress("서울시 강남구")
                .avatarUrl("https://example.com/avatar.jpg")
                .isActive(true)
                .build();
        entityManager.persist(user);
        return user;
    }

    private Coupon createCoupon(String name){
        LocalDateTime baseDateTime = LocalDateTime.of(2025, 11, 12, 0, 0);
        return Coupon.builder()
                .couponName(name)
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal(10000))
                .startDate(baseDateTime)
                .endDate(baseDateTime.plusDays(10))
                .totalQuantity(10)
                .build();
    }

}
