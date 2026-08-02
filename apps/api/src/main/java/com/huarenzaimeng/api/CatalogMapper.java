package com.huarenzaimeng.api;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Mapper
interface CatalogMapper {
    @Select("""
            SELECT b.supported_operator_set_version, c.catalog_version, b.approval_ref,
                   b.qualification_known, b.effective_from, b.expires_at
            FROM hz_operator_support_batch b
            JOIN hz_product_catalog c
              ON c.supported_operator_set_version=b.supported_operator_set_version
             AND c.catalog_state='ACTIVE'
            WHERE b.batch_state='ACTIVE' AND b.effective_from<=#{now} AND b.expires_at>#{now}
              AND c.effective_from<=#{now} AND c.expires_at>#{now}
            ORDER BY b.supported_operator_set_version DESC, c.catalog_version DESC LIMIT 1
            """)
    Map<String, Object> selectActiveCatalog(@Param("now") Timestamp now);

    @Select("""
            SELECT operator_code FROM hz_operator_membership
            WHERE supported_operator_set_version=#{version} AND membership_state='SUPPORTED'
            ORDER BY operator_code
            """)
    List<String> selectSupportedOperators(@Param("version") long version);

    @Select("""
            SELECT operator_code, product_ref, denomination_ref, item_kind, amount_minor, currency
            FROM hz_catalog_item
            WHERE catalog_version=#{catalogVersion} AND item_state='ACTIVE'
            ORDER BY operator_code, product_ref, denomination_ref
            """)
    List<Map<String, Object>> selectCatalogItems(@Param("catalogVersion") long catalogVersion);
}
