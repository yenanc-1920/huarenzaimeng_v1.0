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
              (project_subject_ref, quote_ref, phone_masked, operator_code, product_code, mnp_state,
               total_amount, total_amount_minor, total_currency, price_snapshot, expires_at, created_at)
            VALUES (#{subject}, #{quoteRef}, #{phone}, #{operatorCode}, #{productCode}, 'CONFIRMED',
                    #{displayAmount}, #{amountMinor}, #{currency},
                    JSON_OBJECT('amountMinor', #{amountMinor}, 'currency', #{currency}), #{expiresAt}, #{createdAt})
            """)
    int insertQuote(@Param("subject") String subject, @Param("quoteRef") String quoteRef,
                    @Param("phone") String phone, @Param("operatorCode") String operatorCode,
                    @Param("productCode") String productCode, @Param("displayAmount") BigDecimal displayAmount,
                    @Param("amountMinor") long amountMinor, @Param("currency") String currency,
                    @Param("expiresAt") Timestamp expiresAt, @Param("createdAt") Timestamp createdAt);

    @Select("""
            SELECT quote_ref, phone_masked, operator_code, product_code,
                   total_amount_minor, total_currency, expires_at
            FROM hz_quote WHERE project_subject_ref=#{subject} AND quote_ref=#{quoteRef}
            """)
    Map<String, Object> selectQuote(@Param("subject") String subject, @Param("quoteRef") String quoteRef);

    @Insert("""
            INSERT INTO hz_order
              (project_subject_ref, order_ref, quote_ref, order_state, payment_state, upstream_debit_state,
               delivery_state, refund_state, projection_version, aggregate_version,
               allowed_action, created_at, updated_at)
            VALUES (#{subject}, #{orderRef}, #{quoteRef}, 'AWAITING_PAYMENT', 'ABSENT_CONFIRMED',
                    'ABSENT_CONFIRMED', 'ABSENT_CONFIRMED', 'ABSENT_CONFIRMED', 1, 1,
                    'REQUEST_MOCK_PAYMENT', #{now}, #{now})
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
