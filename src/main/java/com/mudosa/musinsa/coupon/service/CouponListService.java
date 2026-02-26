package com.mudosa.musinsa.coupon.service;

import com.mudosa.musinsa.coupon.model.MemberCoupon;
import com.mudosa.musinsa.coupon.repository.MemberCouponRepository;
import com.mudosa.musinsa.coupon.presentation.dto.res.MemberCouponResDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 지연로딩 적용
@Slf4j
public class CouponListService {

    private final MemberCouponRepository memberCouponRepository;

    /**
     * 사용자가 발급받은 모든 쿠폰 목록 조회
     *
     * @param userId 사용자 ID
     * @return 발급된 쿠폰 목록
     */
    public List<MemberCouponResDto> getMemberCoupons(Long userId) {

        log.info("사용자 쿠폰 목록 조회 - userId: {}", userId);

        List<MemberCoupon> memberCoupons = memberCouponRepository.findAllByUserId(userId);

        return memberCoupons.stream()
                .map(MemberCouponResDto::from)
                .collect(Collectors.toList());


    }

    /**
     * 사용자가 발급받은 사용 가능한 쿠폰 목록만 조회
     *
     * @param userId 사용자 ID
     * @return 사용 가능한 쿠폰 목록
     */
    public List<MemberCouponResDto> getAvailableMemberCoupons(Long userId) {

        log.info("사용자 사용 가능 쿠폰 목록 조회 - userId: {}", userId);
        List<MemberCoupon> allCoupons = memberCouponRepository.findAllByUserId(userId);

        return allCoupons.stream()
                .filter(MemberCoupon::isUsuable)
                .map(MemberCouponResDto::from)
                .collect(Collectors.toList());
    }
}
