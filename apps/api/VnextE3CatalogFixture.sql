-- E3 synthetic-only fixture for the disposable huarenzaimeng_it_vnext schema.
-- Review before execution. Do not use --force. Do not run against any shared or production schema.
-- This script intentionally has no cleanup or real operator/product/content data.

USE huarenzaimeng_it_vnext;

SET @e3_set_version = 8602001;
SET @e3_catalog_version = 8602001;
SET @e3_operator_code = 'SYN-VNEXT-OP';
SET @e3_product_ref = 'SYN-VNEXT-PRODUCT-1000';
SET @e3_denomination_ref = 'SYN-VNEXT-DENOM-1000';
SET @e3_content_ref = 'SYN-CONTENT-VNEXT-E3-20260802';

-- Precondition: each result must be zero. @e3_clean makes every INSERT a no-op otherwise.
SELECT COUNT(*) AS existing_batch
FROM hz_operator_support_batch
WHERE supported_operator_set_version = @e3_set_version OR batch_ref = 'BATCH-E3-VNEXT-8602001';

SELECT COUNT(*) AS existing_catalog
FROM hz_product_catalog
WHERE catalog_version = @e3_catalog_version OR catalog_ref = 'CATALOG-E3-VNEXT-8602001';

SELECT COUNT(*) AS existing_content
FROM hz_content_item
WHERE content_ref = @e3_content_ref;

SET @e3_clean = (
  SELECT CASE WHEN
    (SELECT COUNT(*) FROM hz_operator_support_batch
      WHERE supported_operator_set_version = @e3_set_version
         OR batch_ref = 'BATCH-E3-VNEXT-8602001') = 0
    AND (SELECT COUNT(*) FROM hz_product_catalog
      WHERE catalog_version = @e3_catalog_version
         OR catalog_ref = 'CATALOG-E3-VNEXT-8602001') = 0
    AND (SELECT COUNT(*) FROM hz_content_item
      WHERE content_ref = @e3_content_ref) = 0
  THEN 1 ELSE 0 END
);

START TRANSACTION;

INSERT INTO hz_operator_support_batch
  (supported_operator_set_version, batch_ref, approval_ref, batch_state, qualification_known,
   effective_from, expires_at, rollback_version, created_at)
SELECT
  @e3_set_version, 'BATCH-E3-VNEXT-8602001', 'APPROVAL-E3-SYNTHETIC-ONLY', 'ACTIVE', 1,
   DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 DAY),
   DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 7 DAY), NULL, CURRENT_TIMESTAMP(3)
FROM DUAL WHERE @e3_clean = 1;

INSERT INTO hz_operator_membership
  (supported_operator_set_version, operator_code, membership_state, evidence_ref, created_at)
SELECT
  @e3_set_version, @e3_operator_code, 'SUPPORTED', 'EVIDENCE-E3-SYNTHETIC-ONLY', CURRENT_TIMESTAMP(3)
FROM DUAL WHERE @e3_clean = 1;

INSERT INTO hz_product_catalog
  (catalog_version, supported_operator_set_version, catalog_ref, approval_ref, catalog_state,
   effective_from, expires_at, created_at)
SELECT
  @e3_catalog_version, @e3_set_version, 'CATALOG-E3-VNEXT-8602001',
   'APPROVAL-E3-SYNTHETIC-ONLY', 'ACTIVE',
   DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 DAY),
   DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 7 DAY), CURRENT_TIMESTAMP(3)
FROM DUAL WHERE @e3_clean = 1;

INSERT INTO hz_catalog_item
  (catalog_version, operator_code, product_ref, denomination_ref, item_kind,
   amount_minor, currency, item_state, created_at)
SELECT
  @e3_catalog_version, @e3_operator_code, @e3_product_ref, @e3_denomination_ref,
  'PRESET_DENOMINATION', 1000, 'CNY', 'ACTIVE', CURRENT_TIMESTAMP(3)
FROM DUAL WHERE @e3_clean = 1;

INSERT INTO hz_content_item
  (content_ref, title, summary, category, ownership_mode, aggregate_version,
   content_state, complaint_pending, created_at, updated_at)
SELECT
  @e3_content_ref, 'E3合成服务信息', '仅用于vnext持久化烟测，不代表现实机构或服务。',
   'E3_SYNTHETIC', 'SELF_OPERATED_CHINA_COMPANY', 1, 'DRAFT', 0,
   CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM DUAL WHERE @e3_clean = 1;

COMMIT;

-- Postcondition: exactly one row in each result and no non-synthetic identifiers.
SELECT supported_operator_set_version, batch_ref, approval_ref, batch_state, qualification_known,
       effective_from, expires_at
FROM hz_operator_support_batch
WHERE supported_operator_set_version = @e3_set_version;

SELECT supported_operator_set_version, operator_code, membership_state, evidence_ref
FROM hz_operator_membership
WHERE supported_operator_set_version = @e3_set_version;

SELECT catalog_version, supported_operator_set_version, catalog_ref, approval_ref, catalog_state,
       effective_from, expires_at
FROM hz_product_catalog
WHERE catalog_version = @e3_catalog_version;

SELECT catalog_version, operator_code, product_ref, denomination_ref, item_kind,
       amount_minor, currency, item_state
FROM hz_catalog_item
WHERE catalog_version = @e3_catalog_version;

SELECT content_ref, title, category, ownership_mode, aggregate_version,
       content_state, complaint_pending, created_at, updated_at
FROM hz_content_item
WHERE content_ref = @e3_content_ref;

-- After Invoke-VnextE3PersistenceSmoke.ps1, paste only its returned quoteRef below.
-- Never paste a token or connection string into this file.
SET @e3_subject_ref = 'SYN-SUBJECT-VNEXT-E3-20260802';
SET @e3_quote_ref = 'REPLACE_WITH_RETURNED_QUOTE_REF';

SELECT quote_ref, project_subject_ref, phone_masked, operator_code, product_code,
       denomination_ref, supported_operator_set_version, catalog_version,
       total_amount_minor, total_currency, expires_at
FROM hz_quote
WHERE project_subject_ref = @e3_subject_ref AND quote_ref = @e3_quote_ref;

SELECT command_id, idempotency_key, endpoint_scope, resource_scope,
       semantic_action_key, canonical_fingerprint, resource_ref, command_state
FROM hz_command
WHERE project_subject_ref = @e3_subject_ref
  AND command_id = 'CMD-E3-VNEXT-QUOTE-20260802';

SELECT COUNT(*) AS e3_quote_count
FROM hz_quote
WHERE project_subject_ref = @e3_subject_ref AND quote_ref = @e3_quote_ref;

SELECT COUNT(*) AS e3_command_count
FROM hz_command
WHERE project_subject_ref = @e3_subject_ref
  AND command_id = 'CMD-E3-VNEXT-QUOTE-20260802';

SELECT content_ref, aggregate_version, content_state, complaint_pending,
       source_category, source_ref, verification_scope, verified_by, verified_at, valid_until
FROM hz_content_item
WHERE content_ref = @e3_content_ref;

SELECT command_id, idempotency_key, canonical_fingerprint, content_ref
FROM hz_content_command
WHERE content_ref = @e3_content_ref
ORDER BY created_at;

SELECT action_type, actor_ref, reason, before_version, after_version,
       scope, authorization_ref, result, evidence_ref, occurred_at
FROM hz_content_audit
WHERE content_ref = @e3_content_ref
ORDER BY audit_id;
