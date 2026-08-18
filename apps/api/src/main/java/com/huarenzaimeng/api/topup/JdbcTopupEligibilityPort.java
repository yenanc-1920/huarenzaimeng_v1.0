package com.huarenzaimeng.api.topup;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @Profile("release-mysql")
class JdbcTopupEligibilityPort implements TopupEligibilityPort {
    private final JdbcTemplate jdbc;JdbcTopupEligibilityPort(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Snapshot requirePaidEntitlement(String ref){return jdbc.query("SELECT s.merchant_order_ref,s.buyer_subject_ref,s.provider_sku,s.recipient,s.face_value_minor,s.target_currency,s.entitlement_digest,ch.channel_ref,q.operator_code,p.supplier_cost FROM hz_order_fulfillment_snapshot s JOIN hz_order o ON o.order_ref=s.merchant_order_ref AND o.project_subject_ref=s.buyer_subject_ref JOIN hz_release_order_snapshot ro ON ro.order_ref=o.order_ref JOIN hz_release_quote_snapshot q ON q.quote_ref=ro.quote_ref JOIN hz_platform_product p ON p.platform_product_ref=q.platform_product_ref AND p.provider_sku=s.provider_sku JOIN hz_provider_channel ch ON ch.provider_code=p.provider_code AND ch.channel_state='ENABLED' WHERE s.merchant_order_ref=? AND s.entitlement_state='READY' AND o.payment_state='PAID' AND p.supplier_cost IS NOT NULL",
            (rs,n)->new Snapshot(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getBigDecimal(10)),ref).stream().findFirst().orElseThrow(()->new TopupCoordinator.Conflict("TOPUP_ORDER_NOT_ELIGIBLE"));}
}
