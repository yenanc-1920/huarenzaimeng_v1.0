package com.huarenzaimeng.core;

public record ProjectionFacts(
        String semantics,
        String payment,
        String upstreamDebit,
        String delivery,
        String refund
) {
    public static final String MOCK_ONLY = "MOCK_PROJECTION_ONLY_NO_EXTERNAL_FACTS";
}
