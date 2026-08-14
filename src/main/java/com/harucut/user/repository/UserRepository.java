package com.harucut.user.repository;

import com.harucut.user.entity.User;
import com.harucut.user.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndEmail(Provider provider, String email);

    Optional<User> findByPublicId(String publicId);

    boolean existsByProviderAndEmail(Provider provider, String email);

    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);
}
