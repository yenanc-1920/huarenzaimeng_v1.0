package com.huarenzaimeng.api;

final class TaskLeaseRejectedException extends RuntimeException {
    TaskLeaseRejectedException(String code) { super(code); }
}
