package com.huarenzaimeng.api.payment;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @Profile("release-mysql")
class JdbcPaymentOrderSnapshotPort implements PaymentOrderSnapshotPort {
    private final JdbcTemplate jdbc;
    JdbcPaymentOrderSnapshotPort(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Snapshot requirePayable(String ref){return requirePayable(ref,null);}
    public Snapshot requirePayable(String ref,String subject){
        return jdbc.query("SELECT x.order_ref,x.project_subject_ref,x.quote_ref,x.price_snapshot_digest,x.final_amount_minor,x.currency FROM hz_release_order_snapshot x JOIN hz_order o ON o.order_ref=x.order_ref AND o.project_subject_ref=x.project_subject_ref WHERE x.order_ref=? AND (? IS NULL OR x.project_subject_ref=?) AND o.payment_state IN ('PENDING','UNPAID','CREATED')",
                (rs,n)->new Snapshot(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getString(6)),ref,subject,subject)
                .stream().findFirst().orElseThrow(()->new WeChatPayCoordinator.Conflict("PAYMENT_ORDER_NOT_PAYABLE"));
    }
}
