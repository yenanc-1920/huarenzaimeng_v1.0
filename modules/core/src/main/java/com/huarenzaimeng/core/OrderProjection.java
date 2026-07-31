package com.huarenzaimeng.core;

public record OrderProjection(
        String orderRef,
        String quoteRef,
        OrderState orderState,
        String paymentState,
        String upstreamDebitState,
        String deliveryState,
        String refundState,
        long totalAmountMinor,
        String currency,
        long projectionVersion,
        String nextAction
) {}
