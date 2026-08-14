package com.harucut.auth.service;

public sealed interface RotationResult {

    record Rotated(String refreshToken) implements RotationResult { }

    record Graced(String refreshToken) implements RotationResult { }

    record NoSession() implements RotationResult { }

    record ReuseDetected() implements RotationResult { }
}
