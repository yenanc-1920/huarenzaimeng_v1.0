package com.huarenzaimeng.api;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
interface AdminReadMapper {
    @Select("SELECT city_code AS cityRef,country_code AS countryCode,display_name AS displayName,local_name AS localName,timezone_id AS timezoneId,city_state AS state,sort_order AS sortOrder,aggregate_version AS version,updated_at AS updatedAt FROM hz_city ORDER BY sort_order,city_code")
    List<Map<String,Object>> selectCities();
    @Select("SELECT case_ref AS caseRef,source_type AS sourceType,issue_type AS issueType,related_order_ref AS relatedOrderRef,priority_code AS priorityCode,owner_ref AS ownerRef,case_state AS state,updated_at AS updatedAt FROM hz_customer_case ORDER BY updated_at DESC,case_ref LIMIT 200")
    List<Map<String,Object>> selectCustomerCases();

    @Select("SELECT reconciliation_ref AS reconciliationRef,order_ref AS orderRef,difference_type AS differenceType,amount,currency,case_state AS state,owner_ref AS ownerRef,discovered_at AS discoveredAt,updated_at AS updatedAt FROM hz_reconciliation_case ORDER BY updated_at DESC,reconciliation_ref LIMIT 200")
    List<Map<String,Object>> selectReconciliationCases();

    @Select("SELECT report_ref AS reportRef,entry_ref AS entryRef,reason_code AS reasonCode,description,report_state AS state,created_at AS createdAt FROM hz_directory_report ORDER BY created_at DESC,report_ref LIMIT 200")
    List<Map<String,Object>> selectDirectoryReports();

    @Select("""
            SELECT e.entry_ref AS entryRef,e.city_code AS cityRef,c.display_name AS cityName,
                   e.category_code AS category,e.display_name AS name,
                   e.summary,e.local_address AS localAddress,e.phone,e.source_label AS sourceRef,e.verified_at AS verifiedAt,e.valid_until AS validUntil,
                   e.aggregate_version AS version,e.publish_state AS state,e.updated_at AS updatedAt
              FROM hz_directory_entry e JOIN hz_city c ON c.city_code=e.city_code
             ORDER BY e.updated_at DESC,e.entry_ref LIMIT 200
            """)
    List<Map<String,Object>> selectDirectoryEntries();

    @Select("""
            SELECT 'HOLIDAY' AS objectType,rule_ref AS objectRef,country_code AS countryCode,rule_type AS category,
                   display_name AS title,NULL AS summary,NULL AS bodyText,source_label AS sourceRef,NULL AS editor,
                   start_date AS publishAt,effective_until AS validUntil,start_date AS startDate,end_date AS endDate,
                   weekend_days AS weekendDays,effective_from AS effectiveFrom,effective_until AS effectiveUntil,
                   aggregate_version AS version,publish_state AS state,updated_at AS updatedAt
              FROM hz_holiday_rule
            UNION ALL
            SELECT 'NEWS',article_ref,NULL,category_code,title,summary,body_text,source_label,author_name,
                   published_at,valid_until,NULL,NULL,NULL,published_at,valid_until,
                   aggregate_version,publish_state,updated_at FROM hz_news_article
             ORDER BY updated_at DESC,object_ref LIMIT 300
            """)
    List<Map<String,Object>> selectHolidayAndNews();

    @Select("""
            SELECT p.platform_product_ref AS productRef,p.country_code AS countryCode,p.operator_code AS operatorCode,p.product_type AS productType,p.display_name AS displayName,p.benefit_text AS benefitText,
                   p.denomination_bdt AS denominationBdt,p.data_allowance_mb AS dataAllowanceMb,p.voice_minutes AS voiceMinutes,p.sms_count AS smsCount,
                   p.validity_text AS validityText,p.provider_code AS providerCode,p.provider_sku AS providerSku,p.channel_priority AS channelPriority,
                   p.phone_rule AS phoneRule,p.sale_start_at AS saleStartAt,p.sale_end_at AS saleEndAt,p.enable_state AS state,
                   p.catalog_batch_ref AS catalogBatchRef,p.raw_sku_name AS rawSkuName,p.raw_benefit_text AS rawBenefitText,
                   p.supplier_cost AS supplierCost,p.settlement_currency AS settlementCurrency,p.supplier_availability AS supplierAvailability,
                   p.catalog_synced_at AS catalogSyncedAt,p.normalized_type AS normalizedType,p.normalized_operator AS normalizedOperator,
                   p.mapping_state AS mappingState,p.mapping_failure_reason AS mappingFailureReason,
                   p.aggregate_version AS version,v.price_version_ref AS priceVersionRef,v.final_amount_cny AS finalAmountCny,v.fx_source AS fxSource,v.fx_snapshot_ref AS fxSnapshotRef,
                   v.supplier_cost AS priceSupplierCost,v.settlement_currency AS priceSettlementCurrency,v.fx_direction AS fxDirection,
                   v.fx_rate AS fxRate,v.fx_updated_at AS fxUpdatedAt,v.fx_valid_until AS fxValidUntil,v.buffer_rate AS bufferRate,
                   v.markup_rate AS markupRate,v.wechat_fee_rate AS wechatFeeRate,v.tax_rate AS taxRate,v.minimum_margin_rate AS minimumMarginRate,
                   v.rounding_rule AS roundingRule,v.promotion_bearer AS promotionBearer,v.pricing_scope AS pricingScope,
                   v.price_state AS priceState,v.effective_from AS effectiveFrom,v.effective_until AS effectiveUntil,v.aggregate_version AS priceVersion
              FROM hz_platform_product p LEFT JOIN hz_price_version v
                ON v.platform_product_ref=p.platform_product_ref
             ORDER BY p.operator_code,p.product_type,p.platform_product_ref,v.effective_from DESC LIMIT 300
            """)
    List<Map<String,Object>> selectPlatformProducts();

    @Select("SELECT channel_ref AS channelRef,provider_code AS providerCode,display_name AS displayName,channel_priority AS channelPriority,channel_state AS state,aggregate_version AS version,updated_at AS updatedAt FROM hz_provider_channel ORDER BY channel_priority,channel_ref")
    List<Map<String,Object>> selectProviderChannels();

    @Select("""
            SELECT i.catalog_version,i.operator_code,i.product_ref,i.denomination_ref,i.item_kind,
                   i.amount_minor,i.currency,i.item_state,c.catalog_ref,c.catalog_state,c.expires_at,
                   b.batch_ref,b.batch_state,b.qualification_known
            FROM hz_catalog_item i
            JOIN hz_product_catalog c ON c.catalog_version=i.catalog_version
            JOIN hz_operator_support_batch b ON b.supported_operator_set_version=c.supported_operator_set_version
            ORDER BY i.catalog_version DESC,i.operator_code,i.product_ref,i.denomination_ref
            LIMIT 200
            """)
    List<Map<String, Object>> selectAdminCatalog();

    @Select("""
            SELECT o.order_ref,o.order_state,o.payment_state,o.upstream_debit_state,o.delivery_state,
                   o.refund_state,o.updated_at,q.phone_masked,q.total_amount_minor,q.total_currency
            FROM hz_order o JOIN hz_quote q ON q.quote_ref=o.quote_ref
            ORDER BY o.updated_at DESC,o.order_ref
            LIMIT 200
            """)
    List<Map<String, Object>> selectAdminOrders();

    @Select("""
            SELECT o.order_ref,o.order_state,o.payment_state,o.delivery_state,o.refund_state,
                   o.updated_at,q.phone_masked,q.total_amount_minor,q.total_currency
            FROM hz_order o JOIN hz_quote q ON q.quote_ref=o.quote_ref
            WHERE o.order_ref=#{orderRef}
            """)
    Map<String, Object> selectAdminOrderDetail(String orderRef);

    @Select("""
            SELECT f.provider_fact_ref,f.fact_type,f.observed_at
            FROM hz_payment_intent p
            JOIN hz_semantic_action s ON s.semantic_action_key=p.semantic_action_key
            JOIN hz_external_fact f ON f.case_key=s.case_key
            WHERE p.order_ref=#{orderRef} AND f.provider='RELOADLY_SANDBOX'
            ORDER BY f.observed_at DESC,f.external_fact_id DESC
            LIMIT 1
            """)
    Map<String, Object> selectLatestAdminSandboxFact(String orderRef);
}
