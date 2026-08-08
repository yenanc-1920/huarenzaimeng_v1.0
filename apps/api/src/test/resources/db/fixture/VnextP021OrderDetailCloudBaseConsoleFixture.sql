-- EXPLICIT TEST-ONLY fixture for the CloudBase SQL console.
-- Every statement is connection-independent: no USE, TEMPORARY TABLE, or session variable.
-- Authorized target only: huarenzaimeng_it_vnext. Exactly seven synthetic P021 refs.

DELETE FROM huarenzaimeng_it_vnext.hz_order_detail_projection
WHERE order_ref IN ('IT-P021-AWAITING','IT-P021-PAYMENT','IT-P021-TOPUP','IT-P021-UNKNOWN',
                    'IT-P021-DELIVERED','IT-P021-REFUNDED','IT-P021-REVOKED');

DELETE FROM huarenzaimeng_it_vnext.hz_order
WHERE order_ref IN ('IT-P021-AWAITING','IT-P021-PAYMENT','IT-P021-TOPUP','IT-P021-UNKNOWN',
                    'IT-P021-DELIVERED','IT-P021-REFUNDED','IT-P021-REVOKED');

DELETE FROM huarenzaimeng_it_vnext.hz_quote
WHERE quote_ref IN ('IT-Q-P021-AWAITING','IT-Q-P021-PAYMENT','IT-Q-P021-TOPUP','IT-Q-P021-UNKNOWN',
                    'IT-Q-P021-DELIVERED','IT-Q-P021-REFUNDED','IT-Q-P021-REVOKED');

INSERT INTO huarenzaimeng_it_vnext.hz_quote
 (quote_ref,project_subject_ref,phone_masked,operator_code,product_code,denomination_ref,
  supported_operator_set_version,catalog_version,mnp_state,total_amount,total_amount_minor,total_currency,
  price_snapshot,expires_at,created_at)
VALUES
 ('IT-Q-P021-AWAITING','IT-SUBJECT-P021','******1234','IT-OPERATOR','IT-PRODUCT','IT-DENOMINATION',1,1,'CONFIRMED',1250.0000,125000,'BDT',JSON_OBJECT('amountMinor',125000,'currency','BDT','denominationRef','IT-DENOMINATION','supportedOperatorSetVersion',1,'catalogVersion',1),'2027-01-01 00:00:00.000','2026-08-03 00:00:00.000'),
 ('IT-Q-P021-PAYMENT','IT-SUBJECT-P021','******1234','IT-OPERATOR','IT-PRODUCT','IT-DENOMINATION',1,1,'CONFIRMED',1250.0000,125000,'BDT',JSON_OBJECT('amountMinor',125000,'currency','BDT','denominationRef','IT-DENOMINATION','supportedOperatorSetVersion',1,'catalogVersion',1),'2027-01-01 00:00:00.000','2026-08-03 00:00:00.000'),
 ('IT-Q-P021-TOPUP','IT-SUBJECT-P021','******1234','IT-OPERATOR','IT-PRODUCT','IT-DENOMINATION',1,1,'CONFIRMED',1250.0000,125000,'BDT',JSON_OBJECT('amountMinor',125000,'currency','BDT','denominationRef','IT-DENOMINATION','supportedOperatorSetVersion',1,'catalogVersion',1),'2027-01-01 00:00:00.000','2026-08-03 00:00:00.000'),
 ('IT-Q-P021-UNKNOWN','IT-SUBJECT-P021','******1234','IT-OPERATOR','IT-PRODUCT','IT-DENOMINATION',1,1,'CONFIRMED',1250.0000,125000,'BDT',JSON_OBJECT('amountMinor',125000,'currency','BDT','denominationRef','IT-DENOMINATION','supportedOperatorSetVersion',1,'catalogVersion',1),'2027-01-01 00:00:00.000','2026-08-03 00:00:00.000'),
 ('IT-Q-P021-DELIVERED','IT-SUBJECT-P021','******1234','IT-OPERATOR','IT-PRODUCT','IT-DENOMINATION',1,1,'CONFIRMED',1250.0000,125000,'BDT',JSON_OBJECT('amountMinor',125000,'currency','BDT','denominationRef','IT-DENOMINATION','supportedOperatorSetVersion',1,'catalogVersion',1),'2027-01-01 00:00:00.000','2026-08-03 00:00:00.000'),
 ('IT-Q-P021-REFUNDED','IT-SUBJECT-P021','******1234','IT-OPERATOR','IT-PRODUCT','IT-DENOMINATION',1,1,'CONFIRMED',1250.0000,125000,'BDT',JSON_OBJECT('amountMinor',125000,'currency','BDT','denominationRef','IT-DENOMINATION','supportedOperatorSetVersion',1,'catalogVersion',1),'2027-01-01 00:00:00.000','2026-08-03 00:00:00.000'),
 ('IT-Q-P021-REVOKED','IT-SUBJECT-P021','******1234','IT-OPERATOR','IT-PRODUCT','IT-DENOMINATION',1,1,'CONFIRMED',1250.0000,125000,'BDT',JSON_OBJECT('amountMinor',125000,'currency','BDT','denominationRef','IT-DENOMINATION','supportedOperatorSetVersion',1,'catalogVersion',1),'2027-01-01 00:00:00.000','2026-08-03 00:00:00.000');

INSERT INTO huarenzaimeng_it_vnext.hz_order
 (order_ref,project_subject_ref,quote_ref,order_state,payment_state,upstream_debit_state,
  delivery_state,refund_state,projection_version,aggregate_version,allowed_action,created_at,updated_at)
VALUES
 ('IT-P021-AWAITING','IT-SUBJECT-P021','IT-Q-P021-AWAITING','AWAITING_PAYMENT','NOT_OBSERVED','NOT_OBSERVED','NOT_OBSERVED','NOT_OBSERVED',1,1,'READ_ONLY','2026-08-03 00:00:00.000','2026-08-03 00:00:00.000'),
 ('IT-P021-PAYMENT','IT-SUBJECT-P021','IT-Q-P021-PAYMENT','PAYMENT_PROCESSING','NOT_OBSERVED','NOT_OBSERVED','NOT_OBSERVED','NOT_OBSERVED',1,1,'READ_ONLY','2026-08-03 00:00:00.000','2026-08-03 00:00:00.000'),
 ('IT-P021-TOPUP','IT-SUBJECT-P021','IT-Q-P021-TOPUP','TOPUP_PROCESSING','NOT_OBSERVED','NOT_OBSERVED','NOT_OBSERVED','NOT_OBSERVED',1,1,'READ_ONLY','2026-08-03 00:00:00.000','2026-08-03 00:00:00.000'),
 ('IT-P021-UNKNOWN','IT-SUBJECT-P021','IT-Q-P021-UNKNOWN','TOPUP_RESULT_UNKNOWN','NOT_OBSERVED','NOT_OBSERVED','NOT_OBSERVED','NOT_OBSERVED',1,1,'READ_ONLY','2026-08-03 00:00:00.000','2026-08-03 00:00:00.000'),
 ('IT-P021-DELIVERED','IT-SUBJECT-P021','IT-Q-P021-DELIVERED','DELIVERED','NOT_OBSERVED','NOT_OBSERVED','NOT_OBSERVED','NOT_OBSERVED',1,1,'READ_ONLY','2026-08-03 00:00:00.000','2026-08-03 00:00:00.000'),
 ('IT-P021-REFUNDED','IT-SUBJECT-P021','IT-Q-P021-REFUNDED','REFUNDED','NOT_OBSERVED','NOT_OBSERVED','NOT_OBSERVED','NOT_OBSERVED',1,1,'READ_ONLY','2026-08-03 00:00:00.000','2026-08-03 00:00:00.000'),
 ('IT-P021-REVOKED','IT-SUBJECT-P021','IT-Q-P021-REVOKED','AWAITING_PAYMENT','NOT_OBSERVED','NOT_OBSERVED','NOT_OBSERVED','NOT_OBSERVED',1,1,'READ_ONLY','2026-08-03 00:00:00.000','2026-08-03 00:00:00.000');

INSERT INTO huarenzaimeng_it_vnext.hz_order_detail_projection
 (order_ref,quote_ref,project_subject_ref,session_ref,session_version,authorization_set_ref,
  authorization_evidence_version,authorized_order_refs,price_snapshot_digest,quote_snapshot_digest,
  projection_json,projection_version,updated_at,revoked)
SELECT fixture.order_ref,fixture.quote_ref,'IT-SUBJECT-P021','IT-SESSION-P021',1,'IT-AUTHSET-P021',
 'IT-AUTH-EVIDENCE-P021-V1',
 JSON_ARRAY('IT-P021-AWAITING','IT-P021-PAYMENT','IT-P021-TOPUP','IT-P021-UNKNOWN',
            'IT-P021-DELIVERED','IT-P021-REFUNDED','IT-P021-REVOKED'),
 'D63B5B00F47775240F6F33096153147E75E92315ED3909B2E32F747DFA73C2A5',
 'C5F4EBF8CE0EE01350EAD721E4B2D2F274C87C7282086E8E9915AB839F1C7638',
 JSON_OBJECT(
   'orderRef',fixture.order_ref,'aggregateVersion',1,'projectionVersion',1,'stateCode',fixture.state_code,
   'priceSnapshotSummary',JSON_OBJECT('priceSnapshotRef','IT-PRICE-P021','totalMinor',125000,'currency','BDT',
     'displayVersion','DISPLAY-V1','maskedTarget','******1234','brandDisplayName','测试运营商',
     'productDisplayName','测试套餐','targetValueDisplay','1000 BDT','targetCurrency','BDT',
     'validUntil','2026-12-31T18:00:00Z'),
   'confirmedItems',CASE fixture.state_code
     WHEN 'DELIVERED' THEN JSON_ARRAY('PAYMENT_CONFIRMATION','TOPUP_RESULT','DELIVERY_RESULT')
     WHEN 'REFUNDED' THEN JSON_ARRAY('REFUND_RESULT') ELSE JSON_ARRAY() END,
   'unknownItems',CASE fixture.state_code
     WHEN 'PAYMENT_PROCESSING' THEN JSON_ARRAY('PAYMENT_CONFIRMATION')
     WHEN 'TOPUP_PROCESSING' THEN JSON_ARRAY('TOPUP_RESULT')
     WHEN 'TOPUP_RESULT_UNKNOWN' THEN JSON_ARRAY('TOPUP_RESULT') ELSE JSON_ARRAY() END,
   'responsibilityCode',CASE fixture.state_code
     WHEN 'AWAITING_PAYMENT' THEN 'USER_PAYMENT'
     WHEN 'TOPUP_RESULT_UNKNOWN' THEN 'SUPPORT_REVIEW'
     WHEN 'DELIVERED' THEN 'NONE' WHEN 'REFUNDED' THEN 'NONE' ELSE 'SYSTEM_RECHECK' END,
   'updatedAt','2026-08-03T00:00:00Z','nextReviewPoint',NULL,
   'timeline',JSON_ARRAY(JSON_OBJECT('timelineItemRef',fixture.timeline_ref,'sequence',1,
     'projectionVersion',1,'stateCode',fixture.state_code,'occurredAt','2026-08-03T00:00:00Z',
     'userMessageCode',CONCAT('ORDER_STATUS_',fixture.state_code))),
   'allowedActions',CASE WHEN fixture.state_code='TOPUP_RESULT_UNKNOWN' THEN JSON_ARRAY(
     JSON_OBJECT('actionCode','REFRESH_ORDER_DETAIL','enabled',TRUE,'actionBindingVersion',1,'supportRef',NULL),
     JSON_OBJECT('actionCode','OPEN_SUPPORT','enabled',TRUE,'actionBindingVersion',1,'supportRef','IT-SUPPORT-P021'),
     JSON_OBJECT('actionCode','SAFE_BACK','enabled',TRUE,'actionBindingVersion',1,'supportRef',NULL))
   ELSE JSON_ARRAY(
     JSON_OBJECT('actionCode','REFRESH_ORDER_DETAIL','enabled',TRUE,'actionBindingVersion',1,'supportRef',NULL),
     JSON_OBJECT('actionCode','SAFE_BACK','enabled',TRUE,'actionBindingVersion',1,'supportRef',NULL)) END,
   'supportRef',CASE WHEN fixture.state_code='TOPUP_RESULT_UNKNOWN' THEN 'IT-SUPPORT-P021' ELSE NULL END),
 1,'2026-08-03 00:00:00.000',fixture.revoked
FROM (
 SELECT 'IT-P021-AWAITING' order_ref,'IT-Q-P021-AWAITING' quote_ref,'AWAITING_PAYMENT' state_code,'IT-TL-1' timeline_ref,0 revoked
 UNION ALL SELECT 'IT-P021-PAYMENT','IT-Q-P021-PAYMENT','PAYMENT_PROCESSING','IT-TL-2',0
 UNION ALL SELECT 'IT-P021-TOPUP','IT-Q-P021-TOPUP','TOPUP_PROCESSING','IT-TL-3',0
 UNION ALL SELECT 'IT-P021-UNKNOWN','IT-Q-P021-UNKNOWN','TOPUP_RESULT_UNKNOWN','IT-TL-4',0
 UNION ALL SELECT 'IT-P021-DELIVERED','IT-Q-P021-DELIVERED','DELIVERED','IT-TL-5',0
 UNION ALL SELECT 'IT-P021-REFUNDED','IT-Q-P021-REFUNDED','REFUNDED','IT-TL-6',0
 UNION ALL SELECT 'IT-P021-REVOKED','IT-Q-P021-REVOKED','AWAITING_PAYMENT','IT-TL-7',1
) fixture;
