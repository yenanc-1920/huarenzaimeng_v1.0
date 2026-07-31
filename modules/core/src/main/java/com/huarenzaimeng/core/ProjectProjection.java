package com.huarenzaimeng.core;

import java.util.List;

public record ProjectProjection(
        String orderRef,
        String quoteRef,
        OrderState orderState,
        PriceSnapshot priceSnapshot,
        ProjectionFacts facts,
        long projectionVersion,
        long aggregateVersion,
        List<AllowedAction> allowedActions
) {}
