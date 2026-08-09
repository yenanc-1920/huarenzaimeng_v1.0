package com.huarenzaimeng.api;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
interface AdminReadMapper {
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
}
