package com.huarenzaimeng.api;

import com.huarenzaimeng.core.OrderProjection;
import com.huarenzaimeng.core.Quote;

import java.util.function.UnaryOperator;

interface FlowStore {
    void saveQuote(String projectSubjectRef, Quote quote);

    Quote requireQuote(String projectSubjectRef, String quoteRef);

    OrderProjection createOrder(String projectSubjectRef, Quote quote, CommandIdentity command);

    OrderProjection requireOrder(String projectSubjectRef, String orderRef);

    OrderProjection transitionOrder(String projectSubjectRef, String orderRef, CommandIdentity command,
                                    long expectedProjectionVersion, long expectedAggregateVersion,
                                    UnaryOperator<OrderProjection> transition);
}
