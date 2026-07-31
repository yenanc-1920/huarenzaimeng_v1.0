package com.huarenzaimeng.core;

public record AllowedAction(
        String actionCode,
        long expectedProjectionVersion,
        Long expectedAggregateVersion
) {}
