package com.huarenzaimeng.api;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

import static com.huarenzaimeng.api.P021OrderDetailSideEffectProbe.Counter;
import static com.huarenzaimeng.api.P021OrderDetailSideEffectProbe.LocalBoundaryMutation;

/** Local controlled adapters used to prove each observer is wired to a concrete write boundary. */
@Component
final class P021OrderDetailBoundaryAdapters {
    private final P021OrderDetailSideEffectProbe probe;
    private final Map<Counter, LocalBoundaryPort> ports;

    P021OrderDetailBoundaryAdapters(P021OrderDetailSideEffectProbe probe) {
        this.probe = probe;
        EnumMap<Counter, LocalBoundaryPort> configured = new EnumMap<>(Counter.class);
        for (Counter counter : Counter.values()) {
            if (counter != Counter.QueryCall) configured.put(counter,
                    new LocalBoundaryPort(counter, "P021-LOCAL-" + counter.name(), probe.observerFor(counter)));
        }
        this.ports = Map.copyOf(configured);
    }

    void exerciseControlledBoundary(Counter counter) {
        switch (counter) {
            case Command -> commandAccepted(); case CommandAlias -> commandAliasAccepted();
            case TopupBusinessKey -> topupBusinessKeyWritten(); case TopupSemanticAction -> topupActionWritten();
            case TopupIntent -> topupIntentWritten(); case DispatchSemanticAction -> dispatchActionWritten();
            case DispatchIntent -> dispatchIntentWritten(); case OrderVersion -> orderVersionWritten();
            case ProjectionVersion -> projectionVersionWritten(); case SyntheticObservation -> observationWritten();
            case PaymentAttempt -> paymentAttemptWritten(); case SendAttempt -> sendAttemptWritten();
            case RemoteAcceptance -> remoteAcceptanceWritten(); case WechatPrepay -> wechatPrepayWritten();
            case RequestPayment -> requestPaymentWritten(); case Notification -> notificationWritten();
            case ExternalFact -> externalFactWritten(); case W -> wFactWritten(); case U -> uFactWritten();
            case D -> dFactWritten(); case L -> lFactWritten(); case LedgerEntry -> ledgerEntryWritten();
            case ExternalCall -> externalCallSent(); case FileWrite -> fileWritten(); case QueueWrite -> queueWritten();
            case NotificationSend -> notificationSent();
            case QueryCall -> throw new IllegalArgumentException("QueryCall is exercised only by the read adapter");
        }
    }

    private void commandAccepted() { write(Counter.Command); }
    private void commandAliasAccepted() { write(Counter.CommandAlias); }
    private void topupBusinessKeyWritten() { write(Counter.TopupBusinessKey); }
    private void topupActionWritten() { write(Counter.TopupSemanticAction); }
    private void topupIntentWritten() { write(Counter.TopupIntent); }
    private void dispatchActionWritten() { write(Counter.DispatchSemanticAction); }
    private void dispatchIntentWritten() { write(Counter.DispatchIntent); }
    private void orderVersionWritten() { write(Counter.OrderVersion); }
    private void projectionVersionWritten() { write(Counter.ProjectionVersion); }
    private void observationWritten() { write(Counter.SyntheticObservation); }
    private void paymentAttemptWritten() { write(Counter.PaymentAttempt); }
    private void sendAttemptWritten() { write(Counter.SendAttempt); }
    private void remoteAcceptanceWritten() { write(Counter.RemoteAcceptance); }
    private void wechatPrepayWritten() { write(Counter.WechatPrepay); }
    private void requestPaymentWritten() { write(Counter.RequestPayment); }
    private void notificationWritten() { write(Counter.Notification); }
    private void externalFactWritten() { write(Counter.ExternalFact); }
    private void wFactWritten() { write(Counter.W); }
    private void uFactWritten() { write(Counter.U); }
    private void dFactWritten() { write(Counter.D); }
    private void lFactWritten() { write(Counter.L); }
    private void ledgerEntryWritten() { write(Counter.LedgerEntry); }
    private void externalCallSent() { write(Counter.ExternalCall); }
    private void fileWritten() { write(Counter.FileWrite); }
    private void queueWritten() { write(Counter.QueueWrite); }
    private void notificationSent() { write(Counter.NotificationSend); }

    private void write(Counter counter) { ports.get(counter).write(); }

    void assertDisconnectedBoundaryIsDetected(Counter counter) {
        long before = probe.snapshot().get(counter.name());
        LocalBoundaryPort disconnected = new LocalBoundaryPort(counter, "P021-DISCONNECTED-" + counter.name(), null);
        disconnected.write();
        long delta = probe.snapshot().get(counter.name()) - before;
        if (disconnected.localWriteCount() != 1 || delta != 1) {
            throw new AssertionError("local boundary write was not observed: " + counter);
        }
    }

    private static final class LocalBoundaryPort {
        private final Counter counter;
        private final String boundaryRef;
        private final P021OrderDetailSideEffectProbe.BoundaryObserver observer;
        private long localWriteCount;

        private LocalBoundaryPort(Counter counter, String boundaryRef,
                                  P021OrderDetailSideEffectProbe.BoundaryObserver observer) {
            this.counter = counter; this.boundaryRef = boundaryRef; this.observer = observer;
        }

        private synchronized void write() {
            localWriteCount++;
            LocalBoundaryMutation mutation = new LocalBoundaryMutation(counter, boundaryRef, localWriteCount);
            if (observer != null) observer.observed(mutation);
        }

        private synchronized long localWriteCount() { return localWriteCount; }
    }
}
