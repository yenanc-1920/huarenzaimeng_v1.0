package com.huarenzaimeng.core;

public record ProjectEnvelope<T>(String status, String projectCode, T data) {
    public static <T> ProjectEnvelope<T> accepted(T data) {
        return new ProjectEnvelope<>("ACCEPTED", "OK", data);
    }

    public static ProjectEnvelope<Void> rejected(String projectCode) {
        return new ProjectEnvelope<>("REJECTED", projectCode, null);
    }
}
