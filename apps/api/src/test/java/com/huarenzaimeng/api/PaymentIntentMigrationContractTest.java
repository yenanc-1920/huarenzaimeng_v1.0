package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentIntentMigrationContractTest {
    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration",
            "V6__add_local_synthetic_payment_intent.sql");
    private static final Path RUNBOOK = Path.of("src", "main", "resources", "db", "migration",
            "V6_PAYMENT_INTENT_FORWARD_RUNBOOK.md");

    @Test void v6DefinesIndependentIntentAndBusinessSemanticUniqueness() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertThat(sql).contains("CREATE TABLE hz_payment_intent")
                .contains("payment_intent_ref")
                .contains("project_subject_ref")
                .contains("order_ref")
                .contains("business_key")
                .contains("semantic_action_key")
                .contains("request_fingerprint")
                .contains("price_snapshot_digest")
                .contains("payment_eligibility_decision_ref")
                .contains("intent_scope")
                .contains("UNIQUE KEY uk_hz_payment_intent_business")
                .contains("UNIQUE KEY uk_hz_payment_intent_business_key")
                .contains("UNIQUE KEY uk_hz_payment_intent_semantic")
                .contains("fk_hz_payment_intent_order")
                .contains("fk_hz_payment_intent_semantic")
                .doesNotContain("prepay_id", "transaction_id", "openid", "wechat");
    }

    @Test void v6DoesNotMutateLegacyDirectoryMigrationAndRunbookKeepsMysqlNotRun() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        String runbook = Files.readString(RUNBOOK, StandardCharsets.UTF_8);
        assertThat(sql).doesNotContain("hz_content_item", "DROP TABLE", "DROP COLUMN");
        assertThat(runbook).contains("真实MySQL执行`NOT_RUN`")
                .contains("独立测试库")
                .contains("不得手工跳号")
                .contains("隐式提交")
                .contains("Evidence字段");
    }

    @Test void mybatisCandidateKeepsIntentOrderCommandAndOutboxInOneTransactionBoundary() throws Exception {
        String store = Files.readString(Path.of("src", "main", "java", "com", "huarenzaimeng", "api",
                "MyBatisFlowStore.java"), StandardCharsets.UTF_8);
        assertThat(store).contains("transactions.execute")
                .contains("selectOrderForUpdate")
                .contains("insertSemanticAction")
                .contains("insertPaymentIntent")
                .contains("updateOrder")
                .contains("insertCommand")
                .contains("insertOutbox")
                .contains("recoverPaymentIntentUniqueConflict")
                .contains("IDEMPOTENCY_CONFLICT")
                .doesNotContain("insertOrderAlias(subject, command, draft.paymentIntentRef())");
        int parentLock = store.indexOf("selectOrderForUpdate(subject, draft.orderRef())");
        int recheck = store.indexOf("findCommand(subject, command, true)", parentLock);
        int versionCheck = store.indexOf("current.projectionVersion() != expectedProjectionVersion", parentLock);
        assertThat(parentLock).isGreaterThanOrEqualTo(0);
        assertThat(recheck).isGreaterThan(parentLock).isLessThan(versionCheck);
    }

    @Test void paymentIntentUniqueConstraintNamesAndUnknownConstraintFailClosedClassificationAreStable() {
        assertThat(MyBatisFlowStore.paymentIntentUniqueConstraintCategory(
                "Duplicate entry for key 'uk_hz_payment_intent_business'"))
                .isEqualTo("uk_hz_payment_intent_business");
        assertThat(MyBatisFlowStore.paymentIntentUniqueConstraintCategory(
                "Duplicate entry for key 'uk_hz_payment_intent_business_key'"))
                .isEqualTo("uk_hz_payment_intent_business_key");
        assertThat(MyBatisFlowStore.paymentIntentUniqueConstraintCategory(
                "Duplicate entry for key 'uk_hz_payment_intent_semantic'"))
                .isEqualTo("uk_hz_payment_intent_semantic");
        assertThat(MyBatisFlowStore.paymentIntentUniqueConstraintCategory(
                "Duplicate entry for key 'uk_hz_command_subject_endpoint_idempotency'"))
                .isEqualTo("uk_hz_command_subject_endpoint_idempotency");
        assertThat(MyBatisFlowStore.paymentIntentUniqueConstraintCategory("unmapped unique constraint"))
                .isEqualTo("UNKNOWN_UNIQUE_CONSTRAINT");
        assertThat(MyBatisFlowStore.paymentIntentUniqueConstraintCategory(null))
                .isEqualTo("UNKNOWN_UNIQUE_CONSTRAINT");
    }
}
