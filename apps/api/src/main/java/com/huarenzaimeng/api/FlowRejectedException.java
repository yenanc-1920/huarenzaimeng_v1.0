package com.huarenzaimeng.api;

final class FlowRejectedException extends RuntimeException {
    FlowRejectedException(String code) {
        super(code);
    }
}
