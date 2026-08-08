package com.huarenzaimeng.api;

final class ContentRejectedException extends RuntimeException {
    ContentRejectedException(String projectCode) { super(projectCode); }
}
