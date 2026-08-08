package com.huarenzaimeng.api;

import com.huarenzaimeng.core.OrderProjection;
import com.huarenzaimeng.core.OrderState;
import com.huarenzaimeng.core.Quote;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCreationMigrationContractTest {
    private static final Path V1 = Path.of("src/main/resources/db/migration/V1__create_core_transaction_tables.sql");
    private static final Path V2 = Path.of("src/main/resources/db/migration/V2__add_subject_scoped_commands_versions_and_worker_tables.sql");
    private static final Path V4 = Path.of("src/main/resources/db/migration/V4__add_versioned_operator_and_preset_catalog.sql");
    private static final Path V5 = Path.of("src/main/resources/db/migration/V5__tighten_order_command_idempotency_scope.sql");
    private static final Path RUNBOOK = Path.of("src/main/resources/db/migration/V2_FORWARD_RUNBOOK.md");
    private static final Path V5_RUNBOOK = Path.of("src/main/resources/db/migration/V5_ORDER_COMMAND_ALIAS_FORWARD_RUNBOOK.md");

    @Test void existingMysql57MigrationSequenceHasStaticOrderAndQuoteGuardsButRemainsNotRun() throws Exception {
        String v1 = Files.readString(V1);
        String v2 = Files.readString(V2);
        String v4 = Files.readString(V4);
        assertThat(v1).contains("PRIMARY KEY (quote_ref)", "UNIQUE KEY uk_hz_order_quote_ref (quote_ref)",
                "CONSTRAINT fk_hz_order_quote");
        assertThat(v2).contains("ADD COLUMN project_subject_ref", "ADD COLUMN aggregate_version",
                "PRIMARY KEY (project_subject_ref, command_id)",
                "UNIQUE KEY uk_hz_command_subject_semantic", "canonical_fingerprint CHAR(64)");
        assertThat(v4).contains("denomination_ref", "supported_operator_set_version", "catalog_version");
        assertThat(Files.readString(RUNBOOK)).contains("NOT_RUN");
        assertThat(Files.readString(V5)).contains("uk_hz_command_subject_endpoint_idempotency",
                "project_subject_ref, endpoint_scope, idempotency_key");
        assertThat(Files.readString(V5_RUNBOOK)).contains("STATIC_CONTRACT_ONLY", "NOT_RUN", "ORDER_ALIAS");
        assertThat(v1 + v2 + v4).doesNotContain("INSERT INTO hz_order", "INSERT INTO hz_payment");

        String service = Files.readString(Path.of(
                "src/main/java/com/huarenzaimeng/api/MockFlowService.java"));
        String creationOnly = service.substring(service.indexOf("OrderCreationResponse createLocalSyntheticOrder"),
                service.indexOf("PaymentIntentResponse createLocalSyntheticPaymentIntent"));
        assertThat(creationOnly).doesNotContain("confirmMockPayment", "completeMockTopup", "transitionOrder",
                "insertOutbox", "PaymentIntent", "PaymentAttempt", "DispatchIntent", "ExternalCall",
                "LedgerEntry");
    }

    @Test void mysqlReplayRegistersNewAcceptedKeysAtomicallyAndAliasCannotMoveToAnotherQuote() {
        FlowMapper mapper = mock(FlowMapper.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        MyBatisFlowStore store = new MyBatisFlowStore(mapper, transactions);
        String subject = "SYN-SUBJECT-MYBATIS-ALIAS";
        String endpoint = "POST:/api/v1/orders";
        String business = "ORDER_CREATE:LOCAL_SYNTHETIC:" + subject + ":Q-ALIAS-1";
        String fingerprint = "A".repeat(64);
        CommandIdentity rekey = new CommandIdentity("CMD-ALIAS-NEW", "IDEM-ALIAS-NEW", endpoint,
                "Q-ALIAS-1", business, fingerprint);
        Map<String, Object> canonical = commandRow("CMD-ORIGINAL", "IDEM-ORIGINAL", endpoint,
                "Q-ALIAS-1", business, fingerprint, "O-ALIAS-1");
        Map<String, Object> alias = commandRow(rekey.commandId(), rekey.idempotencyKey(), endpoint,
                "Q-ALIAS-1", "ORDER_ALIAS:EXISTING", fingerprint, "O-ALIAS-1");
        when(mapper.selectOrderCommandsForUpdate(subject, rekey.commandId(), endpoint,
                rekey.idempotencyKey(), business)).thenReturn(List.of(canonical));
        when(mapper.selectOrder(subject, "O-ALIAS-1")).thenReturn(orderRow("O-ALIAS-1", "Q-ALIAS-1"));

        OrderCreateResult replay = store.replayOrder(subject, rekey);
        assertThat(replay.created()).isFalse();
        assertThat(replay.order().orderRef()).isEqualTo("O-ALIAS-1");
        verify(mapper).insertCommand(eq(subject), eq(rekey.commandId()), eq(rekey.idempotencyKey()), eq(endpoint),
                eq("Q-ALIAS-1"), startsWith("ORDER_ALIAS:"), eq(fingerprint), eq("O-ALIAS-1"),
                any(Timestamp.class));

        String changedBusiness = "ORDER_CREATE:LOCAL_SYNTHETIC:" + subject + ":Q-ALIAS-2";
        CommandIdentity moved = new CommandIdentity(rekey.commandId(), rekey.idempotencyKey(), endpoint,
                "Q-ALIAS-2", changedBusiness, "B".repeat(64));
        when(mapper.selectOrderCommandsForUpdate(subject, moved.commandId(), endpoint,
                moved.idempotencyKey(), changedBusiness)).thenReturn(List.of(alias));
        assertThatThrownBy(() -> store.replayOrder(subject, moved))
                .isInstanceOf(FlowRejectedException.class).hasMessage("IDEMPOTENCY_CONFLICT");
    }

    @Test void tenClassSnapshotReadsActualTransitionWriteBoundariesInsteadOfConstantZero() {
        InMemoryFlowStore store = new InMemoryFlowStore();
        String subject = "SYN-SUBJECT-EFFECT-SNAPSHOT";
        Quote quote = new Quote("Q-EFFECT", "880****000", "SYN-OP", "SYN-PRODUCT", "SYN-DENOM-1000",
                1L, 1L, 1000L, "CNY", Instant.parse("2026-08-03T00:00:00Z"));
        store.installQuoteForTest(subject, quote);
        CommandIdentity create = new CommandIdentity("CMD-EFFECT-CREATE", "IDEM-EFFECT-CREATE",
                "POST:/api/v1/orders", quote.quoteRef(), "ORDER_CREATE:LOCAL_SYNTHETIC:" + subject + ":Q-EFFECT",
                "C".repeat(64));
        OrderProjection order = store.createOrder(subject, quote, create).order();
        assertThat(store.sideEffectSnapshot(subject).total()).isZero();

        CommandIdentity payment = new CommandIdentity("CMD-EFFECT-PAY", "IDEM-EFFECT-PAY",
                "POST:/api/v1/orders/{orderRef}/mock-payment", order.orderRef(),
                "MOCK_PAYMENT:" + order.orderRef(), "D".repeat(64));
        OrderProjection paid = store.transitionOrder(subject, order.orderRef(), payment, 1L, 1L,
                current -> new OrderProjection(current.orderRef(), current.quoteRef(), OrderState.PAYMENT_CONFIRMED,
                        "CONFIRMED", current.upstreamDebitState(), current.deliveryState(), current.refundState(),
                        current.totalAmountMinor(), current.currency(), 2L, 2L, "REQUEST_MOCK_TOPUP"));
        OrderCreationSideEffectSnapshot afterPayment = store.sideEffectSnapshot(subject);
        assertThat(afterPayment.paymentAttempts()).isEqualTo(1);
        assertThat(afterPayment.wechatPaymentFacts()).isEqualTo(1);
        assertThat(afterPayment.total()).isEqualTo(2);

        CommandIdentity topup = new CommandIdentity("CMD-EFFECT-TOPUP", "IDEM-EFFECT-TOPUP",
                "POST:/api/v1/orders/{orderRef}/mock-topup", order.orderRef(),
                "MOCK_TOPUP:" + order.orderRef(), "E".repeat(64));
        store.transitionOrder(subject, order.orderRef(), topup, paid.projectionVersion(), paid.aggregateVersion(),
                current -> new OrderProjection(current.orderRef(), current.quoteRef(), OrderState.COMPLETED,
                        current.paymentState(), "CONFIRMED", "CONFIRMED", current.refundState(),
                        current.totalAmountMinor(), current.currency(), 3L, 3L, "NONE"));
        OrderCreationSideEffectSnapshot afterTopup = store.sideEffectSnapshot(subject);
        assertThat(afterTopup.dispatchIntents()).isEqualTo(1);
        assertThat(afterTopup.upstreamDebitFacts()).isEqualTo(1);
        assertThat(afterTopup.deliveryFacts()).isEqualTo(1);
        assertThat(afterTopup.paymentIntents()).isZero();
        assertThat(afterTopup.refundFacts()).isZero();
        assertThat(afterTopup.localLedgerFacts()).isZero();
        assertThat(afterTopup.ledgerEntries()).isZero();
        assertThat(afterTopup.externalCalls()).isZero();
        assertThat(afterTopup.total()).isEqualTo(5);
    }

    private static Map<String, Object> commandRow(String commandId, String idempotencyKey, String endpoint,
                                                   String resourceScope, String semantic, String fingerprint,
                                                   String resourceRef) {
        return Map.of("command_id", commandId, "idempotency_key", idempotencyKey,
                "endpoint_scope", endpoint, "resource_scope", resourceScope,
                "semantic_action_key", semantic, "canonical_fingerprint", fingerprint,
                "resource_ref", resourceRef);
    }

    private static Map<String, Object> orderRow(String orderRef, String quoteRef) {
        return Map.ofEntries(Map.entry("order_ref", orderRef), Map.entry("quote_ref", quoteRef),
                Map.entry("order_state", "AWAITING_PAYMENT"), Map.entry("payment_state", "ABSENT_CONFIRMED"),
                Map.entry("upstream_debit_state", "ABSENT_CONFIRMED"),
                Map.entry("delivery_state", "ABSENT_CONFIRMED"), Map.entry("refund_state", "ABSENT_CONFIRMED"),
                Map.entry("total_amount_minor", 1000L), Map.entry("total_currency", "CNY"),
                Map.entry("projection_version", 1L), Map.entry("aggregate_version", 1L),
                Map.entry("allowed_action", "CREATE_LOCAL_SYNTHETIC_PAYMENT_INTENT"));
    }
}
