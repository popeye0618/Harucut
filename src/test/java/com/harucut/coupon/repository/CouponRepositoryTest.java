package com.harucut.coupon.repository;

import com.harucut.config.JpaAuditingConfig;
import com.harucut.coupon.entity.Coupon;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.support.FixedClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @AutoConfigureJson: frame 엔티티의 AttributeConverter가 앱 ObjectMapper를 주입받는데,
// Hibernate는 어떤 엔티티를 쓰든 메타모델 전체를 만들므로 모든 @DataJpaTest에 Jackson이 필요하다
@DataJpaTest
@AutoConfigureJson
@Import({JpaAuditingConfig.class, FixedClockConfig.class})
@ActiveProfiles("test")
class CouponRepositoryTest {

    @Autowired
    private CouponRepository couponRepository;

    @Test
    @DisplayName("상한 미만이면 +1이 성공하고 1을 반환한다")
    void incrementsBelowMax() {
        Coupon coupon = couponRepository.saveAndFlush(coupon("CODE-1", 2));

        int updated = couponRepository.tryIncrementRedeemedCount(coupon.getId());

        assertThat(updated).isEqualTo(1);
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getRedeemedCount())
                .isEqualTo(1);
    }

    /*
     * 상한 검사와 +1이 DB 한 문장이라 그 사이에 다른 요청이 끼어들 틈이 없다.
     * 여기서는 단일 스레드로 "0이 돌아오면 카운트가 안 변한다"까지 증명하고,
     * 진짜 동시성(10스레드)은 통합 테스트에서 증명한다.
     */
    @Test
    @DisplayName("상한에 도달하면 0을 반환하고 카운트가 변하지 않는다")
    void stopsAtMax() {
        Coupon coupon = couponRepository.saveAndFlush(coupon("CODE-1", 1));
        couponRepository.tryIncrementRedeemedCount(coupon.getId());

        int updated = couponRepository.tryIncrementRedeemedCount(coupon.getId());

        assertThat(updated).isZero();
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getRedeemedCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("상한이 null이면 제한 없이 계속 증가한다")
    void unlimitedWhenMaxIsNull() {
        Coupon coupon = couponRepository.saveAndFlush(coupon("CODE-1", null));

        for (int i = 0; i < 5; i++) {
            couponRepository.tryIncrementRedeemedCount(coupon.getId());
        }

        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getRedeemedCount())
                .isEqualTo(5);
    }

    // 소문자 조회가 빈손인 건 버그가 아니라 전제다 — DB 비교는 대소문자를 구분하므로
    // 서비스가 입력을 normalizeCode로 대문자화해서 조회해야 한다
    @Test
    @DisplayName("findByCode는 정확히 일치하는 코드만 찾는다")
    void findByCodeIsExactMatch() {
        couponRepository.saveAndFlush(coupon("WELCOME-PRO", null));

        assertThat(couponRepository.findByCode("WELCOME-PRO")).isPresent();
        assertThat(couponRepository.findByCode("welcome-pro")).isEmpty();
    }

    @Test
    @DisplayName("같은 코드로 두 번 저장하면 DB 제약에 걸린다")
    void rejectsDuplicateCode() {
        couponRepository.saveAndFlush(coupon("CODE-1", null));

        assertThatThrownBy(() -> couponRepository.saveAndFlush(coupon("CODE-1", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Coupon coupon(String code, Integer maxRedemptions) {
        return Coupon.create("가입 축하 PRO", code, PlanTier.PRO, maxRedemptions, null);
    }
}
