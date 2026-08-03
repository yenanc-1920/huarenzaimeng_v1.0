package com.huarenzaimeng.api;

import java.util.Optional;
import java.util.List;

import static com.huarenzaimeng.api.P021OrderDetailDomain.Fixture;

interface P021Store {
    Optional<Fixture> findAuthorized(String orderRef, String projectSubjectRef, String sessionRef);
    default Optional<Fixture> findAuthorized(String orderRef, SessionSnapshot session) {
        Optional<Fixture> found = findAuthorized(orderRef, session.projectSubjectRef(), session.sessionRef());
        return found.filter(value -> value.sessionVersion() == session.sessionVersion()
                && value.authorizationSetRef().equals(session.authorizationSetRef())
                && value.authorizationEvidenceVersion().equals(session.authorizationEvidenceVersion())
                && value.authorizedOrderRefs().equals(session.authorizedOrderRefs()));
    }
    default void installForTest(Fixture fixture, String sessionRef) { throw new UnsupportedOperationException(); }
    default void clearForTest() {}
}

record SessionSnapshot(String projectSubjectRef, String sessionRef, long sessionVersion,
                       String authorizationSetRef, String authorizationEvidenceVersion,
                       List<String> authorizedOrderRefs) {}
