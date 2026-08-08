package com.huarenzaimeng.api;

import com.huarenzaimeng.core.AllowedAction;
import com.huarenzaimeng.core.OrderProjection;
import com.huarenzaimeng.core.PriceSnapshot;

import java.util.List;

record BuyerAuthorization(
        String environment,
        String projectSubjectRef,
        long sessionVersion,
        String authorizationSetRef,
        String authorizationEvidenceVersion,
        List<String> authorizedOrderRefs
) {}

record OrderCreateResult(OrderProjection order, boolean created) {}

record OrderCreationSideEffectSnapshot(
        long paymentIntents,
        long paymentAttempts,
        long wechatPrepayCalls,
        long requestPaymentCalls,
        long paymentNotifications,
        long dispatchIntents,
        long wechatPaymentFacts,
        long upstreamDebitFacts,
        long deliveryFacts,
        long refundFacts,
        long localLedgerFacts,
        long ledgerEntries,
        long externalCalls
) {
    long total() {
        return paymentIntents + paymentAttempts + wechatPrepayCalls + requestPaymentCalls + paymentNotifications
                + dispatchIntents + wechatPaymentFacts + upstreamDebitFacts
                + deliveryFacts + refundFacts + localLedgerFacts + ledgerEntries + externalCalls;
    }
}

record OrderCreationProjection(
        String orderRef,
        String quoteRef,
        UserOrderStateCode stateCode,
        PriceSnapshot priceSnapshot,
        long projectionVersion,
        long aggregateVersion,
        List<AllowedAction> allowedActions
) {}

record OrderCreationResponse(
        String requestRef,
        String outcome,
        String projectCode,
        String resourceRef,
        long aggregateVersion,
        OrderCreationProjection currentProjection,
        String retryClass
) {}
