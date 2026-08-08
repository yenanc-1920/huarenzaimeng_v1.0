-- Read-only precheck. All result rows must identify only the authorized empty test database.
SELECT DATABASE() AS selected_database,
       (SELECT COUNT(*) FROM huarenzaimeng_it_vnext.flyway_schema_history
         WHERE version='7' AND success=1) AS successful_v7_count;

SELECT
 (SELECT COUNT(*) FROM huarenzaimeng_it_vnext.hz_quote WHERE quote_ref IN
   ('IT-Q-P021-AWAITING','IT-Q-P021-PAYMENT','IT-Q-P021-TOPUP','IT-Q-P021-UNKNOWN','IT-Q-P021-DELIVERED','IT-Q-P021-REFUNDED','IT-Q-P021-REVOKED')) AS quote_count,
 (SELECT COUNT(*) FROM huarenzaimeng_it_vnext.hz_order WHERE order_ref IN
   ('IT-P021-AWAITING','IT-P021-PAYMENT','IT-P021-TOPUP','IT-P021-UNKNOWN','IT-P021-DELIVERED','IT-P021-REFUNDED','IT-P021-REVOKED')) AS order_count,
 (SELECT COUNT(*) FROM huarenzaimeng_it_vnext.hz_order_detail_projection WHERE order_ref IN
   ('IT-P021-AWAITING','IT-P021-PAYMENT','IT-P021-TOPUP','IT-P021-UNKNOWN','IT-P021-DELIVERED','IT-P021-REFUNDED','IT-P021-REVOKED')) AS projection_count;
