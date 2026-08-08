package com.huarenzaimeng.api;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Mapper
interface FlowMapper {
    @Select("""
            SELECT content_ref, title, summary, category, ownership_mode, source_category, source_ref,
                   verification_scope, verified_by, verified_at, valid_until, aggregate_version,
                   content_state, complaint_pending, updated_at
            FROM hz_content_item
            WHERE content_state='PUBLISHED' AND complaint_pending=0
            ORDER BY content_ref
            """)
    List<Map<String, Object>> selectPublicContent();

    @Select("""
            SELECT content_ref, title, summary, category, ownership_mode, source_category, source_ref,
                   verification_scope, verified_by, verified_at, valid_until, aggregate_version,
                   content_state, complaint_pending, updated_at
            FROM hz_content_item WHERE content_ref=#{contentRef}
            """)
    Map<String, Object> selectContent(@Param("contentRef") String contentRef);

    @Select("""
            SELECT content_ref, title, summary, category, ownership_mode, source_category, source_ref,
                   verification_scope, verified_by, verified_at, valid_until, aggregate_version,
                   content_state, complaint_pending, updated_at
            FROM hz_content_item WHERE content_ref=#{contentRef} FOR UPDATE
            """)
    Map<String, Object> selectContentForUpdate(@Param("contentRef") String contentRef);

    @Select("""
            SELECT content_ref, title, summary, category, ownership_mode, source_category, source_ref,
                   verification_scope, verified_by, verified_at, valid_until, aggregate_version,
                   content_state, complaint_pending, updated_at
            FROM hz_content_item ORDER BY content_ref
            """)
    List<Map<String, Object>> selectAllContent();

    @Select("""
            SELECT audit_id, action_type, actor_ref, reason, before_version, after_version, occurred_at,
                   scope, authorization_ref, result, evidence_ref
            FROM hz_content_audit WHERE content_ref=#{contentRef} ORDER BY audit_id
            """)
    List<Map<String, Object>> selectContentAudit(@Param("contentRef") String contentRef);

    @Insert("""
            INSERT INTO hz_content_item
              (content_ref, title, summary, category, ownership_mode, aggregate_version,
               content_state, complaint_pending, created_at, updated_at)
            VALUES (#{contentRef}, #{title}, #{summary}, #{category}, 'SELF_OPERATED_CHINA_COMPANY',
                    1, 'DRAFT', 0, #{now}, #{now})
            """)
    int insertContent(@Param("contentRef") String contentRef, @Param("title") String title,
                      @Param("summary") String summary, @Param("category") String category,
                      @Param("now") Timestamp now);

    @Select("""
            SELECT command_id, idempotency_key, canonical_fingerprint, content_ref
            FROM hz_content_command
            WHERE command_id=#{commandId} OR idempotency_key=#{idempotencyKey}
            FOR UPDATE
            """)
    List<Map<String, Object>> selectContentCommandsForUpdate(@Param("commandId") String commandId,
                                                             @Param("idempotencyKey") String idempotencyKey);

    @Insert("""
            INSERT INTO hz_content_command
              (command_id, idempotency_key, canonical_fingerprint, content_ref, created_at)
            VALUES (#{commandId}, #{idempotencyKey}, #{fingerprint}, #{contentRef}, #{now})
            """)
    int insertContentCommand(@Param("commandId") String commandId,
                             @Param("idempotencyKey") String idempotencyKey,
                             @Param("fingerprint") String fingerprint,
                             @Param("contentRef") String contentRef, @Param("now") Timestamp now);

    @Update("""
            UPDATE hz_content_item
            SET source_category=#{sourceCategory}, source_ref=#{sourceRef},
                verification_scope=#{verificationScope}, verified_by=#{verifiedBy},
                verified_at=#{verifiedAt}, valid_until=#{validUntil}, content_state=#{state},
                complaint_pending=#{complaintPending}, aggregate_version=#{nextVersion}, updated_at=#{now}
            WHERE content_ref=#{contentRef} AND aggregate_version=#{expectedVersion}
            """)
    int updateContent(@Param("contentRef") String contentRef,
                      @Param("sourceCategory") String sourceCategory, @Param("sourceRef") String sourceRef,
                      @Param("verificationScope") String verificationScope, @Param("verifiedBy") String verifiedBy,
                      @Param("verifiedAt") Timestamp verifiedAt, @Param("validUntil") Timestamp validUntil,
                      @Param("state") String state, @Param("complaintPending") boolean complaintPending,
                      @Param("nextVersion") long nextVersion, @Param("expectedVersion") long expectedVersion,
                      @Param("now") Timestamp now);

    @Insert("""
            INSERT INTO hz_content_audit
              (content_ref, action_type, actor_ref, reason, before_version, after_version, occurred_at,
               scope, authorization_ref, result, evidence_ref)
            VALUES (#{contentRef}, #{action}, #{actorRef}, #{reason}, #{beforeVersion}, #{afterVersion}, #{now},
                    #{scope}, #{authorizationRef}, #{result}, #{evidenceRef})
            """)
    int insertContentAudit(@Param("contentRef") String contentRef, @Param("action") String action,
                           @Param("actorRef") String actorRef, @Param("reason") String reason,
                           @Param("beforeVersion") long beforeVersion, @Param("afterVersion") long afterVersion,
                           @Param("now") Timestamp now, @Param("scope") String scope,
                           @Param("authorizationRef") String authorizationRef, @Param("result") String result,
                           @Param("evidenceRef") String evidenceRef);

    @Select("""
            SELECT result_key, task_key, fencing_token, aggregate_ref, canonical_fingerprint,
                   CAST(payload_json AS CHAR) AS payload_json
            FROM hz_task_domain_result WHERE result_key=#{resultKey} FOR UPDATE
            """)
    Map<String, Object> selectFencedResultForUpdate(@Param("resultKey") String resultKey);

    @Insert("""
            INSERT INTO hz_task_domain_result
              (result_key, task_key, lease_owner, fencing_token, aggregate_ref,
               canonical_fingerprint, payload_json, created_at)
            VALUES (#{resultKey}, #{taskKey}, #{owner}, #{token}, #{aggregateRef},
                    #{fingerprint}, CAST(#{payloadJson} AS JSON), #{createdAt})
            """)
    int insertFencedDomainResult(@Param("resultKey") String resultKey, @Param("taskKey") String taskKey,
                                 @Param("owner") String owner, @Param("token") long token,
                                 @Param("aggregateRef") String aggregateRef,
                                 @Param("fingerprint") String fingerprint,
                                 @Param("payloadJson") String payloadJson,
                                 @Param("createdAt") Timestamp createdAt);

    @Insert("""
            INSERT INTO hz_task_ledger_marker
              (marker_key, result_key, task_key, fencing_token, created_at)
            VALUES (#{markerKey}, #{resultKey}, #{taskKey}, #{token}, #{createdAt})
            """)
    int insertFencedLedgerMarker(@Param("markerKey") String markerKey, @Param("resultKey") String resultKey,
                                 @Param("taskKey") String taskKey, @Param("token") long token,
                                 @Param("createdAt") Timestamp createdAt);

    @Insert("""
            INSERT INTO hz_outbox
              (event_key, aggregate_ref, event_type, payload_json, event_state, created_at)
            VALUES (#{eventKey}, #{aggregateRef}, 'FENCED_MOCK_RESULT',
                    JSON_OBJECT('resultKey', #{resultKey}, 'fencingToken', #{token}),
                    'PENDING', #{createdAt})
            """)
    int insertFencedOutbox(@Param("eventKey") String eventKey, @Param("aggregateRef") String aggregateRef,
                           @Param("resultKey") String resultKey, @Param("token") long token,
                           @Param("createdAt") Timestamp createdAt);

    @Select("SELECT CURRENT_TIMESTAMP(3)")
    Timestamp selectDatabaseNow();

    @Insert("""
            INSERT INTO hz_task
              (task_key, task_type, task_state, payload_json, available_at, fencing_token,
               attempt_count, created_at, updated_at)
            VALUES (#{taskKey}, #{taskType}, 'AVAILABLE', CAST(#{payloadJson} AS JSON), #{availableAt},
                    0, 0, #{now}, #{now})
            """)
    int insertTask(@Param("taskKey") String taskKey, @Param("taskType") String taskType,
                   @Param("payloadJson") String payloadJson, @Param("availableAt") Timestamp availableAt,
                   @Param("now") Timestamp now);

    @Select("""
            SELECT task_key, lease_owner, lease_until, fencing_token, available_at
            FROM hz_task WHERE task_key=#{taskKey} FOR UPDATE
            """)
    Map<String, Object> selectTaskForUpdate(@Param("taskKey") String taskKey);

    @Update("""
            UPDATE hz_task
            SET task_state='LEASED', lease_owner=#{owner}, lease_until=#{leaseUntil},
                fencing_token=#{nextToken}, attempt_count=attempt_count+1, updated_at=#{now}
            WHERE task_key=#{taskKey} AND fencing_token=#{expectedToken}
              AND available_at <= #{now}
              AND (lease_owner IS NULL OR lease_until IS NULL OR lease_until <= #{now})
            """)
    int claimTask(@Param("taskKey") String taskKey, @Param("owner") String owner,
                  @Param("nextToken") long nextToken, @Param("leaseUntil") Timestamp leaseUntil,
                  @Param("expectedToken") long expectedToken, @Param("now") Timestamp now);

    @Update("""
            UPDATE hz_task SET lease_until=#{leaseUntil}, updated_at=#{now}
            WHERE task_key=#{taskKey} AND task_state='LEASED' AND lease_owner=#{owner}
              AND fencing_token=#{token} AND lease_until > #{now}
            """)
    int renewTask(@Param("taskKey") String taskKey, @Param("owner") String owner,
                  @Param("token") long token, @Param("now") Timestamp now,
                  @Param("leaseUntil") Timestamp leaseUntil);

    @Update("""
            UPDATE hz_task
            SET task_state='AVAILABLE', lease_owner=NULL, lease_until=NULL, updated_at=#{updatedAt}
            WHERE task_key=#{taskKey} AND task_state='LEASED' AND lease_owner=#{owner}
              AND fencing_token=#{token} AND lease_until > #{now}
            """)
    int releaseTask(@Param("taskKey") String taskKey, @Param("owner") String owner,
                    @Param("token") long token, @Param("now") Timestamp now,
                    @Param("updatedAt") Timestamp updatedAt);

    @Insert("""
            INSERT INTO hz_quote
              (project_subject_ref, quote_ref, phone_masked, operator_code, product_code, denomination_ref,
               supported_operator_set_version, catalog_version, mnp_state,
               total_amount, total_amount_minor, total_currency, price_snapshot, expires_at, created_at)
            VALUES (#{subject}, #{quoteRef}, #{phone}, #{operatorCode}, #{productCode}, #{denominationRef},
                    #{supportedOperatorSetVersion}, #{catalogVersion}, 'CONFIRMED',
                    #{displayAmount}, #{amountMinor}, #{currency},
                    JSON_OBJECT('amountMinor', #{amountMinor}, 'currency', #{currency},
                                'denominationRef', #{denominationRef},
                                'supportedOperatorSetVersion', #{supportedOperatorSetVersion},
                                'catalogVersion', #{catalogVersion}), #{expiresAt}, #{createdAt})
            """)
    int insertQuote(@Param("subject") String subject, @Param("quoteRef") String quoteRef,
                    @Param("phone") String phone, @Param("operatorCode") String operatorCode,
                    @Param("productCode") String productCode, @Param("denominationRef") String denominationRef,
                    @Param("supportedOperatorSetVersion") long supportedOperatorSetVersion,
                    @Param("catalogVersion") long catalogVersion, @Param("displayAmount") BigDecimal displayAmount,
                    @Param("amountMinor") long amountMinor, @Param("currency") String currency,
                    @Param("expiresAt") Timestamp expiresAt, @Param("createdAt") Timestamp createdAt);

    @Select("""
            SELECT quote_ref, phone_masked, operator_code, product_code, denomination_ref,
                   supported_operator_set_version, catalog_version,
                   total_amount_minor, total_currency, expires_at
            FROM hz_quote WHERE project_subject_ref=#{subject} AND quote_ref=#{quoteRef}
            """)
    Map<String, Object> selectQuote(@Param("subject") String subject, @Param("quoteRef") String quoteRef);

    @Select("SELECT COUNT(*) FROM hz_quote WHERE project_subject_ref=#{subject}")
    long countQuotes(@Param("subject") String subject);

    @Insert("""
            INSERT INTO hz_order
              (project_subject_ref, order_ref, quote_ref, order_state, payment_state, upstream_debit_state,
               delivery_state, refund_state, projection_version, aggregate_version,
               allowed_action, created_at, updated_at)
            VALUES (#{subject}, #{orderRef}, #{quoteRef}, 'AWAITING_PAYMENT', 'ABSENT_CONFIRMED',
                    'ABSENT_CONFIRMED', 'ABSENT_CONFIRMED', 'ABSENT_CONFIRMED', 1, 1,
                    'CREATE_LOCAL_SYNTHETIC_PAYMENT_INTENT', #{now}, #{now})
            """)
    int insertOrder(@Param("subject") String subject, @Param("orderRef") String orderRef,
                    @Param("quoteRef") String quoteRef, @Param("now") Timestamp now);

    @Select("""
            SELECT o.order_ref, o.quote_ref, o.order_state, o.payment_state,
                   o.upstream_debit_state, o.delivery_state, o.refund_state,
                   q.total_amount_minor, q.total_currency, o.projection_version,
                   o.aggregate_version, o.allowed_action
            FROM hz_order o JOIN hz_quote q
              ON q.project_subject_ref=o.project_subject_ref AND q.quote_ref=o.quote_ref
            WHERE o.project_subject_ref=#{subject} AND o.order_ref=#{orderRef}
            """)
    Map<String, Object> selectOrder(@Param("subject") String subject, @Param("orderRef") String orderRef);

    @Select("""
            SELECT o.order_ref, o.quote_ref, o.order_state, o.payment_state,
                   o.upstream_debit_state, o.delivery_state, o.refund_state,
                   q.total_amount_minor, q.total_currency, o.projection_version,
                   o.aggregate_version, o.allowed_action
            FROM hz_order o JOIN hz_quote q
              ON q.project_subject_ref=o.project_subject_ref AND q.quote_ref=o.quote_ref
            WHERE o.project_subject_ref=#{subject} AND o.order_ref=#{orderRef}
            FOR UPDATE
            """)
    Map<String, Object> selectOrderForUpdate(@Param("subject") String subject,
                                             @Param("orderRef") String orderRef);

    @Insert("""
            INSERT INTO hz_semantic_action
              (semantic_action_key, case_key, action_kind, approved_branch,
               action_state, created_at, updated_at)
            VALUES (#{semanticActionKey}, #{caseKey}, #{actionKind}, #{approvedBranch},
                    'INTENT_RECORDED', #{now}, #{now})
            """)
    int insertSemanticAction(@Param("semanticActionKey") String semanticActionKey,
                             @Param("caseKey") String caseKey,
                             @Param("actionKind") String actionKind,
                             @Param("approvedBranch") String approvedBranch,
                             @Param("now") Timestamp now);

    @Insert("""
            INSERT INTO hz_payment_intent
              (payment_intent_ref, environment, project_subject_ref, order_ref, business_key,
               semantic_action_key, request_fingerprint, price_snapshot_digest,
               payment_eligibility_decision_ref, intent_scope, created_at)
            VALUES (#{paymentIntentRef}, #{environment}, #{subject}, #{orderRef}, #{businessKey},
                    #{semanticActionKey}, #{requestFingerprint}, #{priceSnapshotDigest},
                    #{paymentEligibilityDecisionRef}, #{intentScope}, #{now})
            """)
    int insertPaymentIntent(@Param("paymentIntentRef") String paymentIntentRef,
                            @Param("environment") String environment,
                            @Param("subject") String subject,
                            @Param("orderRef") String orderRef,
                            @Param("businessKey") String businessKey,
                            @Param("semanticActionKey") String semanticActionKey,
                            @Param("requestFingerprint") String requestFingerprint,
                            @Param("priceSnapshotDigest") String priceSnapshotDigest,
                            @Param("paymentEligibilityDecisionRef") String paymentEligibilityDecisionRef,
                            @Param("intentScope") String intentScope,
                            @Param("now") Timestamp now);

    @Select("""
            SELECT pi.payment_intent_ref, pi.environment, pi.project_subject_ref, pi.order_ref,
                   pi.business_key, pi.semantic_action_key, pi.request_fingerprint,
                   pi.price_snapshot_digest, pi.payment_eligibility_decision_ref,
                   pi.intent_scope, pi.created_at,
                   q.quote_ref, q.phone_masked, q.operator_code, q.product_code, q.denomination_ref,
                   q.supported_operator_set_version, q.catalog_version,
                   q.total_amount_minor, q.total_currency, q.expires_at
            FROM hz_payment_intent pi
            JOIN hz_order o ON o.project_subject_ref=pi.project_subject_ref AND o.order_ref=pi.order_ref
            JOIN hz_quote q ON q.project_subject_ref=o.project_subject_ref AND q.quote_ref=o.quote_ref
            WHERE pi.project_subject_ref=#{subject} AND pi.payment_intent_ref=#{paymentIntentRef}
            """)
    Map<String, Object> selectPaymentIntent(@Param("subject") String subject,
                                            @Param("paymentIntentRef") String paymentIntentRef);

    @Select("""
            SELECT c.command_id, c.idempotency_key,
                   c.canonical_fingerprint AS command_canonical_fingerprint,
                   c.semantic_action_key AS command_semantic_action_key,
                   pi.payment_intent_ref, pi.environment, pi.project_subject_ref, pi.order_ref,
                   pi.business_key AS payment_intent_business_key,
                   pi.semantic_action_key AS payment_intent_semantic_action_key,
                   pi.request_fingerprint AS payment_intent_request_fingerprint
            FROM hz_command c
            JOIN hz_payment_intent pi
              ON pi.project_subject_ref=c.project_subject_ref AND pi.payment_intent_ref=c.resource_ref
            WHERE c.project_subject_ref=#{subject}
              AND c.command_id=#{commandId}
              AND c.idempotency_key=#{idempotencyKey}
              AND c.endpoint_scope='POST:/api/v1/orders/{orderRef}/payment-intents'
              AND c.resource_scope=#{orderRef}
            """)
    Map<String, Object> selectPaymentIntentResultByOriginalKeys(
            @Param("subject") String subject, @Param("orderRef") String orderRef,
            @Param("commandId") String commandId, @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE hz_order
            SET order_state=#{orderState}, payment_state=#{paymentState},
                upstream_debit_state=#{debitState}, delivery_state=#{deliveryState},
                refund_state=#{refundState}, allowed_action=#{allowedAction},
                projection_version=#{nextProjectionVersion}, aggregate_version=#{nextAggregateVersion},
                updated_at=#{updatedAt}
            WHERE project_subject_ref=#{subject} AND order_ref=#{orderRef}
              AND aggregate_version=#{expectedAggregateVersion}
            """)
    int updateOrder(@Param("subject") String subject, @Param("orderRef") String orderRef,
                    @Param("orderState") String orderState, @Param("paymentState") String paymentState,
                    @Param("debitState") String debitState, @Param("deliveryState") String deliveryState,
                    @Param("refundState") String refundState, @Param("allowedAction") String allowedAction,
                    @Param("nextProjectionVersion") long nextProjectionVersion,
                    @Param("nextAggregateVersion") long nextAggregateVersion,
                    @Param("expectedAggregateVersion") long expectedAggregateVersion,
                    @Param("updatedAt") Timestamp updatedAt);

    @Select("""
            SELECT project_subject_ref, command_id, idempotency_key, endpoint_scope, resource_scope,
                   semantic_action_key, canonical_fingerprint, resource_ref
            FROM hz_command
            WHERE project_subject_ref=#{subject}
              AND (command_id=#{commandId}
                   OR (endpoint_scope=#{endpointScope} AND resource_scope=#{resourceScope}
                       AND idempotency_key=#{idempotencyKey})
                   OR semantic_action_key=#{semanticActionKey})
            FOR UPDATE
            """)
    List<Map<String, Object>> selectCommandsForUpdate(
            @Param("subject") String subject, @Param("commandId") String commandId,
            @Param("endpointScope") String endpointScope, @Param("resourceScope") String resourceScope,
            @Param("idempotencyKey") String idempotencyKey, @Param("semanticActionKey") String semanticActionKey);

    @Select("""
            SELECT project_subject_ref, command_id, idempotency_key, endpoint_scope, resource_scope,
                   semantic_action_key, canonical_fingerprint, resource_ref
            FROM hz_command
            WHERE project_subject_ref=#{subject}
              AND (command_id=#{commandId}
                   OR (endpoint_scope=#{endpointScope} AND resource_scope=#{resourceScope}
                       AND idempotency_key=#{idempotencyKey})
                   OR semantic_action_key=#{semanticActionKey})
            """)
    List<Map<String, Object>> selectCommands(
            @Param("subject") String subject, @Param("commandId") String commandId,
            @Param("endpointScope") String endpointScope, @Param("resourceScope") String resourceScope,
            @Param("idempotencyKey") String idempotencyKey, @Param("semanticActionKey") String semanticActionKey);

    @Select("""
            SELECT project_subject_ref, command_id, idempotency_key, endpoint_scope, resource_scope,
                   semantic_action_key, canonical_fingerprint, resource_ref
            FROM hz_command
            WHERE project_subject_ref=#{subject}
              AND (command_id=#{commandId}
                   OR (endpoint_scope=#{endpointScope} AND idempotency_key=#{idempotencyKey})
                   OR semantic_action_key=#{semanticActionKey})
            FOR UPDATE
            """)
    List<Map<String, Object>> selectOrderCommandsForUpdate(
            @Param("subject") String subject, @Param("commandId") String commandId,
            @Param("endpointScope") String endpointScope, @Param("idempotencyKey") String idempotencyKey,
            @Param("semanticActionKey") String semanticActionKey);

    @Select("""
            SELECT project_subject_ref, command_id, idempotency_key, endpoint_scope, resource_scope,
                   semantic_action_key, canonical_fingerprint, resource_ref
            FROM hz_command
            WHERE project_subject_ref=#{subject}
              AND (command_id=#{commandId}
                   OR (endpoint_scope=#{endpointScope} AND idempotency_key=#{idempotencyKey})
                   OR semantic_action_key=#{semanticActionKey})
            """)
    List<Map<String, Object>> selectOrderCommands(
            @Param("subject") String subject, @Param("commandId") String commandId,
            @Param("endpointScope") String endpointScope, @Param("idempotencyKey") String idempotencyKey,
            @Param("semanticActionKey") String semanticActionKey);

    @Insert("""
            INSERT INTO hz_command
              (project_subject_ref, command_id, idempotency_key, endpoint_scope, resource_scope,
               semantic_action_key, canonical_fingerprint, resource_ref, command_state, created_at)
            VALUES (#{subject}, #{commandId}, #{idempotencyKey}, #{endpointScope}, #{resourceScope},
                    #{semanticActionKey}, #{fingerprint}, #{resourceRef}, 'ACCEPTED', #{createdAt})
            """)
    int insertCommand(@Param("subject") String subject, @Param("commandId") String commandId,
                      @Param("idempotencyKey") String idempotencyKey,
                      @Param("endpointScope") String endpointScope, @Param("resourceScope") String resourceScope,
                      @Param("semanticActionKey") String semanticActionKey,
                      @Param("fingerprint") String fingerprint, @Param("resourceRef") String resourceRef,
                      @Param("createdAt") Timestamp createdAt);

    @Insert("""
            INSERT INTO hz_outbox
              (event_key, aggregate_ref, event_type, payload_json, event_state, created_at)
            VALUES (#{eventKey}, #{orderRef}, 'ORDER_PROJECTION_CHANGED',
                    JSON_OBJECT('orderRef', #{orderRef}, 'projectionVersion', #{projectionVersion},
                                'aggregateVersion', #{aggregateVersion}), 'PENDING', #{createdAt})
            """)
    int insertOutbox(@Param("eventKey") String eventKey, @Param("orderRef") String orderRef,
                     @Param("projectionVersion") long projectionVersion,
                     @Param("aggregateVersion") long aggregateVersion,
                     @Param("createdAt") Timestamp createdAt);
}
