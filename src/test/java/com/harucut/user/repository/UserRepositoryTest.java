package com.harucut.user.repository;

import com.harucut.config.JpaAuditingConfig;
import com.harucut.support.FixedClockConfig;
import com.harucut.user.entity.User;
import com.harucut.user.enums.Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@DataJpaTest
@Import({JpaAuditingConfig.class, FixedClockConfig.class})
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("같은 provider와 email로 두 번 저장하면 DB 제약에 걸린다")
    void rejectsDuplicateProviderAndEmail() {
        userRepository.save(User.localUser("user@harucut.com", "encoded", "하루컷"));
        userRepository.flush();

        User duplicate = User.localUser("user@harucut.com", "encoded2", "다른사람");

        assertThatThrownBy(() -> {
            userRepository.save(duplicate);
            userRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("existsByProviderAndEmail은 provider가 다르면 false다")
    void existsIsScopedToProvider() {
        userRepository.save(User.localUser("user@harucut.com", "encoded", "하루컷"));
        userRepository.flush();

        assertThat(userRepository.existsByProviderAndEmail(Provider.HARUCUT, "user@harucut.com")).isTrue();
        assertThat(userRepository.existsByProviderAndEmail(Provider.KAKAO, "user@harucut.com")).isFalse();
    }
}