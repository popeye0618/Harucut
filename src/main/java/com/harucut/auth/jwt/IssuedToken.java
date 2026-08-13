package com.harucut.auth.jwt;

import java.time.Duration;

public record IssuedToken(String value, Duration ttl) {
}
