package com.huarenzaimeng.api;

record FencedResultSnapshot(int domainResults, int ledgerMarkers, int outboxEvents) {}
