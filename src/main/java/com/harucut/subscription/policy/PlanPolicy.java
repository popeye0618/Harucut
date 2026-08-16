package com.harucut.subscription.policy;

public record PlanPolicy(FrameLimit frameRetentionLimit, Retention historyRetention) {
}