package com.huarenzaimeng.api;

import com.huarenzaimeng.core.BusinessEventLinker;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class BusinessEventLinkerTest {
    private final BusinessEventLinker linker=new BusinessEventLinker();private final Instant t=Instant.parse("2026-08-18T00:00:00Z");
    @Test void acceptsOrderPaymentTopupDeliveryAndReconciliationCausalChain(){var order=e("E1",BusinessEventLinker.Type.ORDER_CREATED,t);var payment=e("E2",BusinessEventLinker.Type.PAYMENT_CONFIRMED,t.plusSeconds(1));var topup=e("E3",BusinessEventLinker.Type.TOPUP_SUBMITTED,t.plusSeconds(2));var delivered=e("E4",BusinessEventLinker.Type.TOPUP_DELIVERED,t.plusSeconds(3));var reconciliation=e("E5",BusinessEventLinker.Type.RECONCILIATION_OPENED,t.plusSeconds(4));assertThat(linker.link(order,null).parentEventRef()).isNull();assertThat(linker.link(payment,order).parentEventRef()).isEqualTo("E1");assertThat(linker.link(topup,payment).parentEventRef()).isEqualTo("E2");assertThat(linker.link(delivered,topup).parentEventRef()).isEqualTo("E3");assertThat(linker.link(reconciliation,delivered).parentEventRef()).isEqualTo("E4");}
    @Test void rejectsCrossOrderWrongParentAndTimeTravel(){var order=e("E1",BusinessEventLinker.Type.ORDER_CREATED,t);var payment=e("E2",BusinessEventLinker.Type.PAYMENT_CONFIRMED,t.plusSeconds(1));assertThatThrownBy(()->linker.link(new BusinessEventLinker.Event("E3","OTHER",BusinessEventLinker.Type.TOPUP_SUBMITTED,t.plusSeconds(2)),payment)).hasMessage("CROSS_ORDER_EVENT_LINK");assertThatThrownBy(()->linker.link(e("E4",BusinessEventLinker.Type.TOPUP_DELIVERED,t.plusSeconds(2)),payment)).hasMessage("EVENT_CAUSALITY_INVALID");assertThatThrownBy(()->linker.link(e("E5",BusinessEventLinker.Type.PAYMENT_CONFIRMED,t.minusSeconds(1)),order)).hasMessage("EVENT_TIME_ORDER_INVALID");}
    private BusinessEventLinker.Event e(String r,BusinessEventLinker.Type type,Instant at){return new BusinessEventLinker.Event(r,"ORDER-1",type,at);}
}
