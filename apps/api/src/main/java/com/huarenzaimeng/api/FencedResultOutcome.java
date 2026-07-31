package com.huarenzaimeng.api;

record FencedResultOutcome(String status, String projectCode) {
    static FencedResultOutcome applied() { return new FencedResultOutcome("ACCEPTED", "APPLIED"); }
    static FencedResultOutcome replayed() { return new FencedResultOutcome("ACCEPTED", "REPLAYED"); }
    static FencedResultOutcome review() { return new FencedResultOutcome("REVIEW", "RESULT_CONFLICT"); }
}
