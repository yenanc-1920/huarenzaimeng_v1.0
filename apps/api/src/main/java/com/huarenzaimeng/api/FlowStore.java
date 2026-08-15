package com.huarenzaimeng.api;

import com.huarenzaimeng.core.OrderProjection;
import com.huarenzaimeng.core.Quote;

import java.util.function.UnaryOperator;
import java.util.List;

interface FlowStore {
    Quote createQuote(String projectSubjectRef, Quote quote, CommandIdentity command);

    Quote requireQuote(String projectSubjectRef, String quoteRef);

    long quoteCount(String projectSubjectRef);

    OrderCreateResult replayOrder(String projectSubjectRef, CommandIdentity command);

    OrderCreateResult createOrder(String projectSubjectRef, Quote quote, CommandIdentity command);

    OrderProjection requireOrder(String projectSubjectRef, String orderRef);

    List<OrderProjection> listOrders(String projectSubjectRef);

    PaymentIntentCreateResult createPaymentIntent(String projectSubjectRef, PaymentIntentDraft draft,
                                                  CommandIdentity command, long expectedProjectionVersion,
                                                  long expectedAggregateVersion,
                                                  Runnable firstCreationQualification);

    PaymentIntentRecord requirePaymentIntent(String projectSubjectRef, String paymentIntentRef);

    PaymentIntentResultRecord queryPaymentIntentResult(String projectSubjectRef, String environment,
                                                       String orderRef, String originalCommandId,
                                                       String originalIdempotencyKey);

    void recordPaymentIntentReviewSignal(PaymentIntentReviewSignalInput input);

    OrderProjection transitionOrderAuthorized(String projectSubjectRef, String orderRef, StateAdvanceCommand command,
                                               long expectedProjectionVersion, long expectedAggregateVersion,
                                               UnaryOperator<OrderProjection> transition);
}
