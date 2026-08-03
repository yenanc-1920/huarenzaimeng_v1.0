package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.huarenzaimeng.api.P021OrderDetailDomain.Fixture;

@Repository
@ConditionalOnProperty(name = "hz.persistence.mode", havingValue = "in-memory", matchIfMissing = true)
class InMemoryP021Store implements P021Store {
    private record Entry(Fixture fixture, String sessionRef) {}
    private final Map<String, Entry> values = new ConcurrentHashMap<>();

    public Optional<Fixture> findAuthorized(String orderRef, String subjectRef, String sessionRef) {
        Entry entry = values.get(orderRef);
        return entry != null && entry.fixture().projectSubjectRef().equals(subjectRef)
                && entry.sessionRef().equals(sessionRef) ? Optional.of(entry.fixture()) : Optional.empty();
    }
    public void installForTest(Fixture fixture, String sessionRef) {
        values.put(fixture.projection().orderRef(), new Entry(fixture, sessionRef));
    }
    public void clearForTest() { values.clear(); }
    int sizeForTest() { return values.size(); }
}
