-- EXPLICIT TEST-ONLY fixture. Not loaded by Flyway/release/prod.
USE huarenzaimeng_it_vnext;
CREATE TEMPORARY TABLE p021_database_guard (guard_key INT PRIMARY KEY);
INSERT INTO p021_database_guard VALUES (1);
INSERT INTO p021_database_guard SELECT IF(DATABASE()='huarenzaimeng_it_vnext', 2, 1);

SET @p021_authorized_order_refs = JSON_ARRAY('IT-P021-AWAITING','IT-P021-PAYMENT','IT-P021-TOPUP',
  'IT-P021-UNKNOWN','IT-P021-DELIVERED','IT-P021-REFUNDED','IT-P021-REVOKED');
SET @p021_price = JSON_OBJECT('priceSnapshotRef','IT-PRICE-P021','totalMinor',125000,'currency','BDT',
  'displayVersion','DISPLAY-V1','maskedTarget','******1234','brandDisplayName','测试运营商',
  'productDisplayName','测试套餐','targetValueDisplay','1000 BDT','targetCurrency','BDT',
  'validUntil','2026-12-31T18:00:00Z');
SET @p021_actions = JSON_ARRAY(
  JSON_OBJECT('actionCode','REFRESH_ORDER_DETAIL','enabled',TRUE,'actionBindingVersion',1,'supportRef',NULL),
  JSON_OBJECT('actionCode','SAFE_BACK','enabled',TRUE,'actionBindingVersion',1,'supportRef',NULL));
SET @p021_base = JSON_OBJECT('orderRef','PENDING','aggregateVersion',1,'projectionVersion',1,
  'stateCode','AWAITING_PAYMENT','priceSnapshotSummary',@p021_price,'confirmedItems',JSON_ARRAY(),
  'unknownItems',JSON_ARRAY(),'responsibilityCode','USER_PAYMENT','updatedAt','2026-08-03T00:00:00Z',
  'nextReviewPoint',NULL,'timeline',JSON_ARRAY(),'allowedActions',@p021_actions,'supportRef',NULL);

CREATE TEMPORARY TABLE p021_fixture_rows (
  order_ref VARCHAR(64) PRIMARY KEY, quote_ref VARCHAR(64) NOT NULL,
  state_code VARCHAR(32) NOT NULL, projection_json JSON NOT NULL, revoked TINYINT(1) NOT NULL);
INSERT INTO p021_fixture_rows VALUES
('IT-P021-AWAITING','IT-Q-P021-AWAITING','AWAITING_PAYMENT',JSON_SET(@p021_base,'$.orderRef','IT-P021-AWAITING','$.timeline',JSON_ARRAY(JSON_OBJECT('timelineItemRef','IT-TL-1','sequence',1,'projectionVersion',1,'stateCode','AWAITING_PAYMENT','occurredAt','2026-08-03T00:00:00Z','userMessageCode','ORDER_STATUS_AWAITING_PAYMENT'))),0),
('IT-P021-PAYMENT','IT-Q-P021-PAYMENT','PAYMENT_PROCESSING',JSON_SET(@p021_base,'$.orderRef','IT-P021-PAYMENT','$.stateCode','PAYMENT_PROCESSING','$.responsibilityCode','SYSTEM_RECHECK','$.unknownItems',JSON_ARRAY('PAYMENT_CONFIRMATION'),'$.timeline',JSON_ARRAY(JSON_OBJECT('timelineItemRef','IT-TL-2','sequence',1,'projectionVersion',1,'stateCode','PAYMENT_PROCESSING','occurredAt','2026-08-03T00:00:00Z','userMessageCode','ORDER_STATUS_PAYMENT_PROCESSING'))),0),
('IT-P021-TOPUP','IT-Q-P021-TOPUP','TOPUP_PROCESSING',JSON_SET(@p021_base,'$.orderRef','IT-P021-TOPUP','$.stateCode','TOPUP_PROCESSING','$.responsibilityCode','SYSTEM_RECHECK','$.unknownItems',JSON_ARRAY('TOPUP_RESULT'),'$.timeline',JSON_ARRAY(JSON_OBJECT('timelineItemRef','IT-TL-3','sequence',1,'projectionVersion',1,'stateCode','TOPUP_PROCESSING','occurredAt','2026-08-03T00:00:00Z','userMessageCode','ORDER_STATUS_TOPUP_PROCESSING'))),0),
('IT-P021-UNKNOWN','IT-Q-P021-UNKNOWN','TOPUP_RESULT_UNKNOWN',JSON_SET(@p021_base,'$.orderRef','IT-P021-UNKNOWN','$.stateCode','TOPUP_RESULT_UNKNOWN','$.responsibilityCode','SUPPORT_REVIEW','$.unknownItems',JSON_ARRAY('TOPUP_RESULT'),'$.supportRef','IT-SUPPORT-P021','$.allowedActions',JSON_ARRAY(JSON_OBJECT('actionCode','REFRESH_ORDER_DETAIL','enabled',TRUE,'actionBindingVersion',1,'supportRef',NULL),JSON_OBJECT('actionCode','OPEN_SUPPORT','enabled',TRUE,'actionBindingVersion',1,'supportRef','IT-SUPPORT-P021'),JSON_OBJECT('actionCode','SAFE_BACK','enabled',TRUE,'actionBindingVersion',1,'supportRef',NULL)),'$.timeline',JSON_ARRAY(JSON_OBJECT('timelineItemRef','IT-TL-4','sequence',1,'projectionVersion',1,'stateCode','TOPUP_RESULT_UNKNOWN','occurredAt','2026-08-03T00:00:00Z','userMessageCode','ORDER_STATUS_TOPUP_RESULT_UNKNOWN'))),0),
('IT-P021-DELIVERED','IT-Q-P021-DELIVERED','DELIVERED',JSON_SET(@p021_base,'$.orderRef','IT-P021-DELIVERED','$.stateCode','DELIVERED','$.responsibilityCode','NONE','$.confirmedItems',JSON_ARRAY('PAYMENT_CONFIRMATION','TOPUP_RESULT','DELIVERY_RESULT'),'$.timeline',JSON_ARRAY(JSON_OBJECT('timelineItemRef','IT-TL-5','sequence',1,'projectionVersion',1,'stateCode','DELIVERED','occurredAt','2026-08-03T00:00:00Z','userMessageCode','ORDER_STATUS_DELIVERED'))),0),
('IT-P021-REFUNDED','IT-Q-P021-REFUNDED','REFUNDED',JSON_SET(@p021_base,'$.orderRef','IT-P021-REFUNDED','$.stateCode','REFUNDED','$.responsibilityCode','NONE','$.confirmedItems',JSON_ARRAY('REFUND_RESULT'),'$.timeline',JSON_ARRAY(JSON_OBJECT('timelineItemRef','IT-TL-6','sequence',1,'projectionVersion',1,'stateCode','REFUNDED','occurredAt','2026-08-03T00:00:00Z','userMessageCode','ORDER_STATUS_REFUNDED'))),0),
('IT-P021-REVOKED','IT-Q-P021-REVOKED','AWAITING_PAYMENT',JSON_SET(@p021_base,'$.orderRef','IT-P021-REVOKED','$.timeline',JSON_ARRAY(JSON_OBJECT('timelineItemRef','IT-TL-7','sequence',1,'projectionVersion',1,'stateCode','AWAITING_PAYMENT','occurredAt','2026-08-03T00:00:00Z','userMessageCode','ORDER_STATUS_AWAITING_PAYMENT'))),1);

DELETE FROM hz_order_detail_projection WHERE order_ref IN (SELECT order_ref FROM p021_fixture_rows);
DELETE FROM hz_order WHERE order_ref IN (SELECT order_ref FROM p021_fixture_rows);
DELETE FROM hz_quote WHERE quote_ref IN (SELECT quote_ref FROM p021_fixture_rows);

INSERT INTO hz_quote (quote_ref,project_subject_ref,phone_masked,operator_code,product_code,denomination_ref,
 supported_operator_set_version,catalog_version,mnp_state,total_amount,total_amount_minor,total_currency,
 price_snapshot,expires_at,created_at)
SELECT quote_ref,'IT-SUBJECT-P021','******1234','IT-OPERATOR','IT-PRODUCT','IT-DENOMINATION',1,1,
 'CONFIRMED',1250.0000,125000,'BDT',JSON_OBJECT('amountMinor',125000,'currency','BDT',
 'denominationRef','IT-DENOMINATION','supportedOperatorSetVersion',1,'catalogVersion',1),
 '2027-01-01 00:00:00.000','2026-08-03 00:00:00.000' FROM p021_fixture_rows;

INSERT INTO hz_order (order_ref,project_subject_ref,quote_ref,order_state,payment_state,upstream_debit_state,
 delivery_state,refund_state,projection_version,aggregate_version,allowed_action,created_at,updated_at)
SELECT order_ref,'IT-SUBJECT-P021',quote_ref,state_code,'NOT_OBSERVED','NOT_OBSERVED','NOT_OBSERVED',
 'NOT_OBSERVED',1,1,'READ_ONLY','2026-08-03 00:00:00.000','2026-08-03 00:00:00.000'
FROM p021_fixture_rows;

INSERT INTO hz_order_detail_projection (order_ref,quote_ref,project_subject_ref,session_ref,session_version,
 authorization_set_ref,authorization_evidence_version,authorized_order_refs,price_snapshot_digest,
 quote_snapshot_digest,projection_json,projection_version,updated_at,revoked)
SELECT order_ref,quote_ref,'IT-SUBJECT-P021','IT-SESSION-P021',1,'IT-AUTHSET-P021',
 'IT-AUTH-EVIDENCE-P021-V1',@p021_authorized_order_refs,
 'D63B5B00F47775240F6F33096153147E75E92315ED3909B2E32F747DFA73C2A5',
 'C5F4EBF8CE0EE01350EAD721E4B2D2F274C87C7282086E8E9915AB839F1C7638',
 projection_json,1,'2026-08-03 00:00:00.000',revoked FROM p021_fixture_rows;

DROP TEMPORARY TABLE p021_fixture_rows;
DROP TEMPORARY TABLE p021_database_guard;
