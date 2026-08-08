-- Read-only postcheck. Expected counts are exactly 7 / 7 / 7.
SELECT
 (SELECT COUNT(*) FROM huarenzaimeng_it_vnext.hz_quote WHERE quote_ref IN
   ('IT-Q-P021-AWAITING','IT-Q-P021-PAYMENT','IT-Q-P021-TOPUP','IT-Q-P021-UNKNOWN','IT-Q-P021-DELIVERED','IT-Q-P021-REFUNDED','IT-Q-P021-REVOKED')) AS quote_count,
 (SELECT COUNT(*) FROM huarenzaimeng_it_vnext.hz_order WHERE order_ref IN
   ('IT-P021-AWAITING','IT-P021-PAYMENT','IT-P021-TOPUP','IT-P021-UNKNOWN','IT-P021-DELIVERED','IT-P021-REFUNDED','IT-P021-REVOKED')) AS order_count,
 (SELECT COUNT(*) FROM huarenzaimeng_it_vnext.hz_order_detail_projection WHERE order_ref IN
   ('IT-P021-AWAITING','IT-P021-PAYMENT','IT-P021-TOPUP','IT-P021-UNKNOWN','IT-P021-DELIVERED','IT-P021-REFUNDED','IT-P021-REVOKED')) AS projection_count;

SELECT p.order_ref,p.quote_ref,o.order_state,p.revoked,p.session_ref,p.session_version,
       p.authorization_set_ref,p.authorization_evidence_version,p.projection_version
FROM huarenzaimeng_it_vnext.hz_order_detail_projection p
JOIN huarenzaimeng_it_vnext.hz_order o ON o.order_ref=p.order_ref AND o.quote_ref=p.quote_ref
WHERE p.order_ref IN ('IT-P021-AWAITING','IT-P021-PAYMENT','IT-P021-TOPUP','IT-P021-UNKNOWN',
                      'IT-P021-DELIVERED','IT-P021-REFUNDED','IT-P021-REVOKED')
ORDER BY p.order_ref;
