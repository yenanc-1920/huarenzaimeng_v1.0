package com.huarenzaimeng.api;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
interface P021ProjectionMapper {
    @Select("""
        SELECT p.order_ref, p.project_subject_ref, p.session_ref, p.session_version,
               p.authorization_set_ref, p.authorization_evidence_version,
               CAST(p.authorized_order_refs AS CHAR) AS authorized_order_refs,
               p.price_snapshot_digest, p.quote_snapshot_digest,
               CAST(p.projection_json AS CHAR) AS projection_json,
               o.aggregate_version AS authority_aggregate_version,
               o.projection_version AS authority_projection_version,
               q.total_amount_minor AS authority_total_minor, q.total_currency AS authority_currency,
               q.phone_masked AS authority_masked_target,
               UNIX_TIMESTAMP(q.expires_at) AS authority_valid_until_epoch,
               CAST(q.price_snapshot AS CHAR) AS authority_quote_snapshot
          FROM hz_order_detail_projection p
          JOIN hz_order o ON o.order_ref=p.order_ref AND o.quote_ref=p.quote_ref
                         AND o.project_subject_ref=p.project_subject_ref
          JOIN hz_quote q ON q.quote_ref=p.quote_ref AND q.project_subject_ref=p.project_subject_ref
         WHERE p.order_ref = #{orderRef}
           AND p.project_subject_ref = #{subjectRef}
           AND p.session_ref = #{sessionRef}
           AND JSON_CONTAINS(p.authorized_order_refs, JSON_QUOTE(#{orderRef}))
           AND p.revoked = 0
         LIMIT 1
        """)
    Map<String, Object> selectAuthorized(@Param("orderRef") String orderRef,
                                         @Param("subjectRef") String subjectRef,
                                         @Param("sessionRef") String sessionRef);

    @Select("""
        SELECT p.project_subject_ref, p.session_ref, p.session_version,
               p.authorization_set_ref, p.authorization_evidence_version,
               CAST(p.authorized_order_refs AS CHAR) AS authorized_order_refs, p.revoked,
               p.price_snapshot_digest, p.quote_snapshot_digest,
               CAST(p.projection_json AS CHAR) AS projection_json,
               o.aggregate_version AS authority_aggregate_version,
               o.projection_version AS authority_projection_version,
               q.total_amount_minor AS authority_total_minor, q.total_currency AS authority_currency,
               q.phone_masked AS authority_masked_target,
               UNIX_TIMESTAMP(q.expires_at) AS authority_valid_until_epoch,
               CAST(q.price_snapshot AS CHAR) AS authority_quote_snapshot,
               CASE WHEN o.order_ref IS NULL THEN 0 ELSE 1 END AS authority_order_joined,
               CASE WHEN q.quote_ref IS NULL THEN 0 ELSE 1 END AS authority_quote_joined
          FROM hz_order_detail_projection p
          LEFT JOIN hz_order o ON o.order_ref=p.order_ref AND o.quote_ref=p.quote_ref
                              AND o.project_subject_ref=p.project_subject_ref
          LEFT JOIN hz_quote q ON q.quote_ref=p.quote_ref AND q.project_subject_ref=p.project_subject_ref
         WHERE p.order_ref = #{orderRef}
         LIMIT 1
        """)
    Map<String, Object> selectQualificationDiagnostic(@Param("orderRef") String orderRef);
}
