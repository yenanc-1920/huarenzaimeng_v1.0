package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionalOutboxWiringContractTest {
    @Test void coordinatorsNeverPublishAfterStoreCommitAndJdbcStoresOwnDrafts() throws Exception {
        String paymentCoordinator=read("src/main/java/com/huarenzaimeng/api/payment/WeChatPayCoordinator.java");
        String topupCoordinator=read("src/main/java/com/huarenzaimeng/api/topup/TopupCoordinator.java");
        assertThat(paymentCoordinator).doesNotContain("events.append(");
        assertThat(topupCoordinator).doesNotContain("events.append(");
        String paymentStore=read("src/main/java/com/huarenzaimeng/api/payment/JdbcWeChatPayStore.java");
        String topupStore=read("src/main/java/com/huarenzaimeng/api/topup/JdbcTopupStore.java");
        assertThat(paymentStore).contains("@Transactional public WeChatPayCoordinator.Begin begin", "PAYMENT_CONFIRMED", "REFUND_CONFIRMED");
        assertThat(topupStore).contains("@Transactional public TopupCoordinator.Begin beginAndReserve", "TOPUP_SUBMITTED", "TOPUP_DELIVERED", "RECONCILIATION_OPENED");
    }
    private static String read(String path)throws Exception{return Files.readString(Path.of(path));}
}
