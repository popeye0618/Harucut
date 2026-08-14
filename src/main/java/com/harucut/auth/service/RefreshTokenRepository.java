package com.harucut.auth.service;

import com.harucut.auth.jwt.IssuedToken;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String SESSION_PREFIX = "REFRESH_TOKEN:USER:";
    private static final String GRACE_PREFIX = "REFRESH_GRACE:";

    private static final Duration GRACE_TTL = Duration.ofSeconds(10);

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> rotateScript;

    public void save(String publicId, IssuedToken refreshToken) {
        redisTemplate.opsForValue()
                .set(sessionKey(publicId), refreshToken.value(), refreshToken.ttl());
    }

    public void delete(String publicId) {
        redisTemplate.delete(sessionKey(publicId));
    }

    @SuppressWarnings("unchecked")
    public RotationResult rotate(String publicId, String presented, IssuedToken candidate) {
        List<String> result = redisTemplate.execute(
                rotateScript,
                List.of(sessionKey(publicId), GRACE_PREFIX + presented),
                presented,
                candidate.value(),
                String.valueOf(candidate.ttl().toSeconds()),
                String.valueOf(GRACE_TTL.toSeconds()));

        return toRotationResult(result);
    }

    private RotationResult toRotationResult(List<String> result) {
        return switch (result.get(0)) {
            case "ROTATED" -> new RotationResult.Rotated(result.get(1));
            case "GRACE" -> new RotationResult.Graced(result.get(1));
            case "NO_SESSION" -> new RotationResult.NoSession();
            case "REUSE" -> new RotationResult.ReuseDetected();
            default -> throw new IllegalStateException("알 수 없는 회전 결과: " + result.get(0));
        };
    }

    private String sessionKey(String publicId) {
        return SESSION_PREFIX + publicId;
    }
}
