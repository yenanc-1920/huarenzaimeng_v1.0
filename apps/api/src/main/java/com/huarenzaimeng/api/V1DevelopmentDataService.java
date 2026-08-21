package com.huarenzaimeng.api;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
@Profile("release-mysql")
final class V1DevelopmentDataService {
    private static final Map<String,String> OPERATOR_NAMES=Map.of(
            "GRAMEENPHONE","Grameenphone","ROBI","Robi","BANGLALINK","Banglalink","AIRTEL","Airtel","TELETALK","Teletalk");
    private final JdbcTemplate jdbc;
    private final Clock clock;

    V1DevelopmentDataService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    List<Map<String,Object>> products(String operatorCode, String productType) {
        String sql = """
                SELECT p.platform_product_ref AS productRef,p.operator_code AS operatorCode,
                       p.product_type AS productType,p.display_name AS displayName,
                       p.benefit_text AS benefitText,p.denomination_bdt AS denominationBdt,
                       p.validity_text AS validityText,v.final_amount_cny AS finalAmountCny,
                       v.price_version_ref AS priceVersionRef,v.effective_until AS priceValidUntil
                FROM hz_platform_product p JOIN hz_price_version v
                  ON v.platform_product_ref=p.platform_product_ref
                WHERE p.operator_code=? AND p.enable_state='ENABLED' AND v.price_state='ACTIVE'
                  AND v.effective_from<=CURRENT_TIMESTAMP(3) AND v.effective_until>CURRENT_TIMESTAMP(3)
                  AND NOT EXISTS (SELECT 1 FROM hz_price_version newer
                                   WHERE newer.platform_product_ref=v.platform_product_ref AND newer.price_state='ACTIVE'
                                     AND newer.effective_from<=CURRENT_TIMESTAMP(3) AND newer.effective_until>CURRENT_TIMESTAMP(3)
                                     AND (newer.effective_from>v.effective_from OR
                                          (newer.effective_from=v.effective_from AND newer.price_version_ref>v.price_version_ref)))
                """ + (present(productType) ? " AND p.product_type=?" : "") + " ORDER BY p.product_type,p.platform_product_ref";
        Object[] args = present(productType) ? new Object[]{operatorCode, productType} : new Object[]{operatorCode};
        return camel(jdbc.queryForList(sql, args));
    }

    Map<String,Object> catalog(String operatorCode,String productType) {
        List<Map<String,Object>> supported=camel(jdbc.queryForList("""
                SELECT DISTINCT p.operator_code AS operatorCode
                  FROM hz_platform_product p JOIN hz_price_version v ON v.platform_product_ref=p.platform_product_ref
                 WHERE p.enable_state='ENABLED' AND v.price_state='ACTIVE'
                   AND v.effective_from<=CURRENT_TIMESTAMP(3) AND v.effective_until>CURRENT_TIMESTAMP(3)
                 ORDER BY p.operator_code
                """)).stream().map(row -> Map.<String,Object>of(
                        "operatorCode",row.get("operatorCode"),"displayName",operatorName(String.valueOf(row.get("operatorCode"))))).toList();
        Long version=jdbc.queryForObject("SELECT COALESCE(MAX(aggregate_version),1) FROM hz_platform_product",Long.class);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("supportedOperatorSetVersion",version==null?1:version);
        result.put("catalogVersion",version==null?1:version);
        result.put("operatorCode",operatorCode);
        result.put("operatorName",operatorName(operatorCode));
        result.put("supportedOperators",supported);
        result.put("items",products(operatorCode,productType));
        return result;
    }

    static String operatorName(String code){return OPERATOR_NAMES.getOrDefault(code,code);}

    List<Map<String,Object>> cities() {
        return camel(jdbc.queryForList("""
                SELECT city_code AS cityCode,country_code AS countryCode,display_name AS displayName,
                       local_name AS localName,timezone_id AS timezoneId
                FROM hz_city WHERE city_state='ACTIVE' ORDER BY sort_order,city_code
                """));
    }

    List<Map<String,Object>> directory(String cityCode, String category) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.entry_ref AS entryRef,e.city_code AS cityCode,c.display_name AS cityName,
                       e.category_code AS category,e.display_name AS displayName,e.summary,
                       e.local_address AS localAddress,e.phone,
                       e.verified_at AS verifiedAt,e.valid_until AS validUntil,e.updated_at AS updatedAt
                FROM hz_directory_entry e JOIN hz_city c ON c.city_code=e.city_code
                WHERE e.publish_state='PUBLISHED' AND e.valid_until>CURRENT_TIMESTAMP(3)
                """);
        List<Object> args = new ArrayList<>();
        if (present(cityCode)) { sql.append(" AND e.city_code=?"); args.add(cityCode); }
        if (present(category)) { sql.append(" AND e.category_code=?"); args.add(category); }
        sql.append(" ORDER BY c.sort_order,e.category_code,e.display_name");
        return camel(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    Map<String,Object> directoryDetail(String entryRef) {
        return camelOne(jdbc.queryForMap("""
                SELECT e.entry_ref AS entryRef,e.city_code AS cityCode,c.display_name AS cityName,
                       e.category_code AS category,e.display_name AS displayName,e.summary,
                       e.local_address AS localAddress,e.phone,
                       e.verified_at AS verifiedAt,e.valid_until AS validUntil,e.updated_at AS updatedAt
                FROM hz_directory_entry e JOIN hz_city c ON c.city_code=e.city_code
                WHERE e.entry_ref=? AND e.publish_state='PUBLISHED' AND e.valid_until>CURRENT_TIMESTAMP(3)
                """, entryRef));
    }

    List<Map<String,Object>> news(String category) {
        String sql = """
                SELECT article_ref AS contentRef,category_code AS category,title,summary,
                       published_at AS publishedAt,updated_at AS updatedAt,valid_until AS validUntil
                FROM hz_news_article WHERE publish_state='PUBLISHED' AND valid_until>CURRENT_TIMESTAMP(3)
                """ + (present(category) ? " AND category_code=?" : "") + " ORDER BY published_at DESC,article_ref";
        return camel(present(category) ? jdbc.queryForList(sql, category) : jdbc.queryForList(sql));
    }

    Map<String,Object> newsDetail(String articleRef) {
        return camelOne(jdbc.queryForMap("""
                SELECT article_ref AS contentRef,category_code AS category,title,summary,body_text AS bodyText,
                       author_name AS authorName,published_at AS publishedAt,
                       updated_at AS updatedAt,valid_until AS validUntil
                FROM hz_news_article WHERE article_ref=? AND publish_state='PUBLISHED'
                  AND valid_until>CURRENT_TIMESTAMP(3)
                """, articleRef));
    }

    Map<String,Object> temporalOverview() {
        Instant now = clock.instant();
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("serverTime", now);
        result.put("dhaka", temporalCountry("BD", ZoneId.of("Asia/Dhaka"), now));
        result.put("beijing", temporalCountry("CN", ZoneId.of("Asia/Shanghai"), now));
        result.put("priority", List.of("HOLIDAY", "REST_DAY", "WORK_DAY"));
        return result;
    }

    private Map<String,Object> temporalCountry(String countryCode, ZoneId zone, Instant now) {
        LocalDate date = now.atZone(zone).toLocalDate();
        String day = date.getDayOfWeek().name().substring(0, 3);
        List<Map<String,Object>> holidays = camel(jdbc.queryForList("""
                SELECT rule_ref AS ruleRef,display_name AS displayName,source_label AS sourceLabel,
                       aggregate_version AS ruleVersion,effective_from AS effectiveFrom,effective_until AS effectiveUntil
                  FROM hz_holiday_rule
                 WHERE country_code=? AND rule_type='HOLIDAY' AND publish_state='PUBLISHED'
                   AND ? BETWEEN start_date AND end_date
                   AND effective_from<=? AND effective_until>?
                """, countryCode, date, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)));
        List<Map<String,Object>> weekendRules = camel(jdbc.queryForList("""
                SELECT rule_ref AS ruleRef,display_name AS displayName,weekend_days AS weekendDays,
                       source_label AS sourceLabel,aggregate_version AS ruleVersion,
                       effective_from AS effectiveFrom,effective_until AS effectiveUntil
                  FROM hz_holiday_rule WHERE country_code=? AND rule_type='WEEKEND'
                  AND publish_state='PUBLISHED'
                  AND effective_from<=? AND effective_until>?
                """, countryCode, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)));
        Map<String,Object> authority = holidays.isEmpty() ? (weekendRules.isEmpty() ? null : weekendRules.get(0)) : holidays.get(0);
        boolean weekend = authority != null && holidays.isEmpty()
                && java.util.Arrays.asList(String.valueOf(authority.get("weekendDays")).split(",")).contains(day);
        Map<String,Object> value = new LinkedHashMap<>();
        value.put("countryCode", countryCode); value.put("timezone", zone.getId()); value.put("date", date);
        value.put("localTime", now.atZone(zone).toLocalTime().withNano(0));
        value.put("dayType", holidays.isEmpty() ? (weekend ? "REST_DAY" : "WORK_DAY") : "HOLIDAY");
        value.put("holidayName", holidays.isEmpty() ? null : holidays.get(0).get("displayName"));
        value.put("ruleRef", authority == null ? null : authority.get("ruleRef"));
        value.put("sourceLabel", authority == null ? null : authority.get("sourceLabel"));
        value.put("ruleVersion", authority == null ? null : String.valueOf(authority.get("ruleVersion")));
        value.put("effectiveFrom", authority == null ? null : ((java.sql.Timestamp)authority.get("effectiveFrom")).toInstant());
        value.put("effectiveUntil", authority == null ? null : ((java.sql.Timestamp)authority.get("effectiveUntil")).toInstant());
        return value;
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static List<Map<String,Object>> camel(List<Map<String,Object>> rows) {
        return rows.stream().map(V1DevelopmentDataService::camelOne).toList();
    }
    private static Map<String,Object> camelOne(Map<String,Object> row) {
        Map<String,Object> result = new LinkedHashMap<>();
        row.forEach((key,value) -> result.put(toCamel(key), value)); return result;
    }
    private static String toCamel(String value) {
        if (!value.contains("_")) return Character.toLowerCase(value.charAt(0)) + value.substring(1);
        String lower=value.toLowerCase(Locale.ROOT); StringBuilder out=new StringBuilder(); boolean upper=false;
        for(char c:lower.toCharArray()){if(c=='_'){upper=true;}else{out.append(upper?Character.toUpperCase(c):c);upper=false;}}
        return out.toString();
    }
}
