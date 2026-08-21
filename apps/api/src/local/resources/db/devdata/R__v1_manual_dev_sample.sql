-- Local development preset only. This location is not part of the release Flyway locations.
-- Every row is persistent, deterministic and explicitly marked MANUAL_DEV_SAMPLE.
-- Registry convergence is deliberately limited to untouched aggregate_version=1 sample rows.
-- Operator-edited rows have a higher aggregate_version and are never deleted or overwritten.
INSERT INTO hz_v1_dev_seed_registry(object_type,object_ref,seed_version,source_mode,updated_at) VALUES
('CITY','DHAKA',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('CITY','CHITTAGONG',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('DIRECTORY','DIR-DHAKA-HOSPITAL-001',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('DIRECTORY','DIR-DHAKA-SERVICE-001',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('HOLIDAY','BD-WEEKEND-2026',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('HOLIDAY','CN-WEEKEND-2026',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('HOLIDAY','BD-HOLIDAY-DEV-2026',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('HOLIDAY','CN-HOLIDAY-DEV-2026',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('NEWS','NEWS-LIFE-001',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('NEWS','NEWS-HOLIDAY-001',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('PRODUCT','GP-BALANCE-100',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('PRODUCT','ROBI-DATA-5GB',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('PRODUCT','BANGLALINK-COMBO-10GB',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('PRODUCT','AIRTEL-BALANCE-200',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('PRODUCT','TELETALK-DATA-2GB',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('CHANNEL','CHANNEL-WINLA-DEV',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('CUSTOMER_CASE','CASE-DEV-001',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('RECONCILIATION','DIFF-DEV-001',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('QUOTE','QUOTE-DEV-PAYMENT',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('QUOTE','QUOTE-DEV-TOPUP',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('QUOTE','QUOTE-DEV-SUCCESS',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('QUOTE','QUOTE-DEV-REFUND',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('ORDER','ORDER-DEV-PAYMENT',1,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('ORDER','ORDER-DEV-TOPUP',3,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('ORDER','ORDER-DEV-SUCCESS',4,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000'),
('ORDER','ORDER-DEV-REFUND',5,'MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000')
ON DUPLICATE KEY UPDATE seed_version=VALUES(seed_version),source_mode=VALUES(source_mode),updated_at=VALUES(updated_at);

DELETE d FROM hz_reconciliation_case d JOIN hz_v1_dev_seed_registry r ON r.object_type='RECONCILIATION' AND r.object_ref=d.reconciliation_ref AND r.source_mode='MANUAL_DEV_SAMPLE' WHERE d.data_origin='MANUAL_DEV_SAMPLE';
DELETE c FROM hz_customer_case c JOIN hz_v1_dev_seed_registry r ON r.object_type='CUSTOMER_CASE' AND r.object_ref=c.case_ref AND r.source_mode='MANUAL_DEV_SAMPLE' WHERE c.data_origin='MANUAL_DEV_SAMPLE';
DELETE o FROM hz_order o JOIN hz_v1_dev_seed_registry r ON r.object_type='ORDER' AND r.object_ref=o.order_ref AND r.source_mode='MANUAL_DEV_SAMPLE' WHERE o.environment='MANUAL_DEV_SAMPLE' AND o.aggregate_version=r.seed_version;
DELETE q FROM hz_quote q JOIN hz_v1_dev_seed_registry r ON r.object_type='QUOTE' AND r.object_ref=q.quote_ref AND r.source_mode='MANUAL_DEV_SAMPLE' WHERE JSON_UNQUOTE(JSON_EXTRACT(q.price_snapshot,'$.source'))='MANUAL_DEV_SAMPLE' AND NOT EXISTS (SELECT 1 FROM hz_order o WHERE o.quote_ref=q.quote_ref);
DELETE e FROM hz_directory_entry e JOIN hz_v1_dev_seed_registry r ON r.object_type='DIRECTORY' AND r.object_ref=e.entry_ref AND r.source_mode='MANUAL_DEV_SAMPLE' WHERE e.aggregate_version=1;
DELETE h FROM hz_holiday_rule h JOIN hz_v1_dev_seed_registry r ON r.object_type='HOLIDAY' AND r.object_ref=h.rule_ref AND r.source_mode='MANUAL_DEV_SAMPLE' WHERE h.aggregate_version=1;
DELETE n FROM hz_news_article n JOIN hz_v1_dev_seed_registry r ON r.object_type='NEWS' AND r.object_ref=n.article_ref AND r.source_mode='MANUAL_DEV_SAMPLE' WHERE n.aggregate_version=1;
DELETE v FROM hz_price_version v JOIN hz_v1_dev_seed_registry r ON r.object_type='PRODUCT' AND r.object_ref=v.platform_product_ref AND r.source_mode='MANUAL_DEV_SAMPLE' WHERE v.aggregate_version=1 AND v.enabled_by='SUPER_ADMIN_DEV';
DELETE p FROM hz_platform_product p JOIN hz_v1_dev_seed_registry r ON r.object_type='PRODUCT' AND r.object_ref=p.platform_product_ref AND r.source_mode='MANUAL_DEV_SAMPLE' WHERE p.aggregate_version=1 AND p.source_mode='MANUAL_DEV_SAMPLE' AND NOT EXISTS (SELECT 1 FROM hz_price_version v WHERE v.platform_product_ref=p.platform_product_ref);
DELETE c FROM hz_city c JOIN hz_v1_dev_seed_registry r ON r.object_type='CITY' AND r.object_ref=c.city_code AND r.source_mode='MANUAL_DEV_SAMPLE' WHERE c.aggregate_version=1 AND NOT EXISTS (SELECT 1 FROM hz_directory_entry e WHERE e.city_code=c.city_code);
DELETE c FROM hz_provider_channel c JOIN hz_v1_dev_seed_registry r ON r.object_type='CHANNEL' AND r.object_ref=c.channel_ref AND r.source_mode='MANUAL_DEV_SAMPLE' WHERE c.aggregate_version=1;
INSERT IGNORE INTO hz_city
(city_code,country_code,display_name,local_name,timezone_id,city_state,sort_order,aggregate_version,updated_at) VALUES
('DHAKA','BD','达卡','Dhaka','Asia/Dhaka','ACTIVE',10,1,'2026-08-16 00:00:00.000'),
('CHITTAGONG','BD','吉大港','Chattogram','Asia/Dhaka','ACTIVE',20,1,'2026-08-16 00:00:00.000');

INSERT IGNORE INTO hz_directory_entry
(entry_ref,city_code,category_code,display_name,summary,local_address,phone,source_label,verified_at,valid_until,publish_state,aggregate_version,updated_at) VALUES
('DIR-DHAKA-HOSPITAL-001','DHAKA','MEDICAL','达卡社区医疗服务中心','提供日常门诊与基础健康咨询。','Road 11, Banani, Dhaka 1213','+880 2 0000 0000','运营人员录入样例','2026-08-16 00:00:00.000','2027-08-16 00:00:00.000','PUBLISHED',1,'2026-08-16 00:00:00.000'),
('DIR-DHAKA-SERVICE-001','DHAKA','LIFE_SERVICE','达卡华人生活服务站','提供本地生活信息咨询。','House 8, Road 5, Dhanmondi, Dhaka 1205','+880 17 0000 0000','运营人员录入样例','2026-08-16 00:00:00.000','2027-08-16 00:00:00.000','PUBLISHED',1,'2026-08-16 00:00:00.000');

INSERT IGNORE INTO hz_holiday_rule
(rule_ref,country_code,rule_type,display_name,start_date,end_date,weekend_days,source_label,publish_state,aggregate_version,effective_from,effective_until,updated_at) VALUES
('BD-WEEKEND-2026','BD','WEEKEND','孟加拉周休','2026-01-01','2026-12-31','FRI,SAT','运营人员录入样例','PUBLISHED',1,'2026-01-01 00:00:00.000','2027-01-01 00:00:00.000','2026-08-16 00:00:00.000'),
('CN-WEEKEND-2026','CN','WEEKEND','中国周休','2026-01-01','2026-12-31','SAT,SUN','运营人员录入样例','PUBLISHED',1,'2026-01-01 00:00:00.000','2027-01-01 00:00:00.000','2026-08-16 00:00:00.000'),
('BD-HOLIDAY-DEV-2026','BD','HOLIDAY','独立日','2026-03-26','2026-03-26',NULL,'运营人员录入样例','PUBLISHED',1,'2026-01-01 00:00:00.000','2027-01-01 00:00:00.000','2026-08-16 00:00:00.000'),
('CN-HOLIDAY-DEV-2026','CN','HOLIDAY','国庆节','2026-10-01','2026-10-07',NULL,'运营人员录入样例','PUBLISHED',1,'2026-01-01 00:00:00.000','2027-01-01 00:00:00.000','2026-08-16 00:00:00.000');

INSERT IGNORE INTO hz_news_article
(article_ref,category_code,title,summary,body_text,source_label,author_name,publish_state,aggregate_version,published_at,valid_until,updated_at) VALUES
('NEWS-LIFE-001','LIFE_REMINDER','在孟生活：常用紧急电话说明','保存常用号码，遇到紧急情况优先联系当地公共服务。','建议将当地紧急电话、所在机构联系人和家人联系方式保存在手机通讯录，并定期核验。','运营人员录入样例','内容运营','PUBLISHED',1,'2026-08-16 00:00:00.000','2027-08-16 00:00:00.000','2026-08-16 00:00:00.000'),
('NEWS-HOLIDAY-001','HOLIDAY_EXPLANATION','孟加拉节假日安排说明','具体放假安排以当地主管部门正式公告为准。','节假日可能影响营业时间和充值到账时效，请在出行或办理业务前核对正式公告。','运营人员录入样例','内容运营','PUBLISHED',1,'2026-08-16 00:00:00.000','2027-08-16 00:00:00.000','2026-08-16 00:00:00.000');

INSERT IGNORE INTO hz_platform_product
(platform_product_ref,operator_code,product_type,display_name,benefit_text,denomination_bdt,validity_text,
 provider_code,provider_sku,enable_state,source_mode,mapping_state,supplier_availability,aggregate_version,updated_at) VALUES
('GP-BALANCE-100','GRAMEENPHONE','BALANCE','Grameenphone 100塔卡余额','到账100 BDT话费余额',100.00,NULL,'WINLA','DEV-GP-BALANCE-100','ENABLED','MANUAL_DEV_SAMPLE','MAPPED','AVAILABLE',1,'2026-08-16 00:00:00.000'),
('ROBI-DATA-5GB','ROBI','DATA','Robi 5GB流量包','5GB流量',NULL,'7天','WINLA','DEV-ROBI-DATA-5GB','ENABLED','MANUAL_DEV_SAMPLE','MAPPED','AVAILABLE',1,'2026-08-16 00:00:00.000'),
('BANGLALINK-COMBO-10GB','BANGLALINK','BUNDLE','Banglalink畅享套餐','10GB流量与100分钟通话',NULL,'30天','WINLA','DEV-BL-COMBO-10GB','ENABLED','MANUAL_DEV_SAMPLE','MAPPED','AVAILABLE',1,'2026-08-16 00:00:00.000'),
('AIRTEL-BALANCE-200','AIRTEL','BALANCE','Airtel 200塔卡余额','到账200 BDT话费余额',200.00,NULL,'WINLA','DEV-AIRTEL-BALANCE-200','ENABLED','MANUAL_DEV_SAMPLE','MAPPED','AVAILABLE',1,'2026-08-16 00:00:00.000'),
('TELETALK-DATA-2GB','TELETALK','DATA','Teletalk 2GB流量包','2GB流量',NULL,'7天','WINLA','DEV-TELETALK-DATA-2GB','ENABLED','MANUAL_DEV_SAMPLE','MAPPED','AVAILABLE',1,'2026-08-16 00:00:00.000');

INSERT IGNORE INTO hz_price_version
(price_version_ref,platform_product_ref,final_amount_cny,fx_source,fx_snapshot_ref,price_state,
 effective_from,effective_until,enabled_by,aggregate_version,updated_at) VALUES
('PRICE-GP-BALANCE-100-V1','GP-BALANCE-100',6.80,'ECB','ECB-DEV-20260816','ACTIVE','2026-08-16 00:00:00.000','2027-08-16 00:00:00.000','SUPER_ADMIN_DEV',1,'2026-08-16 00:00:00.000'),
('PRICE-ROBI-DATA-5GB-V1','ROBI-DATA-5GB',18.90,'ECB','ECB-DEV-20260816','ACTIVE','2026-08-16 00:00:00.000','2027-08-16 00:00:00.000','SUPER_ADMIN_DEV',1,'2026-08-16 00:00:00.000'),
('PRICE-BL-COMBO-10GB-V1','BANGLALINK-COMBO-10GB',32.50,'ECB','ECB-DEV-20260816','ACTIVE','2026-08-16 00:00:00.000','2027-08-16 00:00:00.000','SUPER_ADMIN_DEV',1,'2026-08-16 00:00:00.000'),
('PRICE-AIRTEL-BALANCE-200-V1','AIRTEL-BALANCE-200',12.90,'ECB','ECB-DEV-20260816','ACTIVE','2026-08-16 00:00:00.000','2027-08-16 00:00:00.000','SUPER_ADMIN_DEV',1,'2026-08-16 00:00:00.000'),
('PRICE-TELETALK-DATA-2GB-V1','TELETALK-DATA-2GB',9.90,'ECB','ECB-DEV-20260816','ACTIVE','2026-08-16 00:00:00.000','2027-08-16 00:00:00.000','SUPER_ADMIN_DEV',1,'2026-08-16 00:00:00.000');

INSERT IGNORE INTO hz_customer_case
(case_ref,source_type,issue_type,related_order_ref,priority_code,owner_ref,case_state,description,data_origin,created_at,updated_at) VALUES
('CASE-DEV-001','ADMIN_MANUAL','TOPUP_STATUS_QUERY',NULL,'NORMAL','CS-DEV','OPEN','用户咨询充值处理进度。','MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000','2026-08-16 00:00:00.000');

INSERT IGNORE INTO hz_reconciliation_case
(reconciliation_ref,order_ref,difference_type,amount,currency,case_state,owner_ref,data_origin,discovered_at,updated_at) VALUES
('DIFF-DEV-001','ORDER-DEV-TOPUP','PAYMENT_TOPUP_PENDING',6.80,'CNY','OPEN','OPS-DEV','MANUAL_DEV_SAMPLE','2026-08-16 00:00:00.000','2026-08-16 00:00:00.000');

INSERT IGNORE INTO hz_provider_channel
(channel_ref,provider_code,display_name,channel_priority,channel_state,aggregate_version,updated_at) VALUES
('CHANNEL-WINLA-DEV','WINLA','WINLA manual development channel',100,'ENABLED',1,'2026-08-16 00:00:00.000');

INSERT IGNORE INTO hz_quote
(quote_ref,project_subject_ref,phone_masked,operator_code,product_code,denomination_ref,
 supported_operator_set_version,catalog_version,mnp_state,total_amount,total_amount_minor,total_currency,
 price_snapshot,expires_at,created_at) VALUES
('QUOTE-DEV-PAYMENT','DEV-BUYER-001','017****678','GRAMEENPHONE','GP-BALANCE-100','DEV-GP-BALANCE-100',1,1,'NOT_CHECKED',6.8000,680,'CNY','{"source":"MANUAL_DEV_SAMPLE","product":"GP-BALANCE-100","priceVersion":"PRICE-GP-BALANCE-100-V1"}','2027-08-16 00:00:00.000','2026-08-16 00:00:00.000'),
('QUOTE-DEV-TOPUP','DEV-BUYER-001','018****321','ROBI','ROBI-DATA-5GB','DEV-ROBI-DATA-5GB',1,1,'NOT_CHECKED',18.9000,1890,'CNY','{"source":"MANUAL_DEV_SAMPLE","product":"ROBI-DATA-5GB","priceVersion":"PRICE-ROBI-DATA-5GB-V1"}','2027-08-16 00:00:00.000','2026-08-16 00:00:00.000'),
('QUOTE-DEV-SUCCESS','DEV-BUYER-001','019****246','BANGLALINK','BANGLALINK-COMBO-10GB','DEV-BL-COMBO-10GB',1,1,'NOT_CHECKED',32.5000,3250,'CNY','{"source":"MANUAL_DEV_SAMPLE","product":"BANGLALINK-COMBO-10GB","priceVersion":"PRICE-BL-COMBO-10GB-V1"}','2027-08-16 00:00:00.000','2026-08-16 00:00:00.000'),
('QUOTE-DEV-REFUND','DEV-BUYER-001','015****135','TELETALK','TELETALK-DATA-2GB','DEV-TELETALK-DATA-2GB',1,1,'NOT_CHECKED',9.9000,990,'CNY','{"source":"MANUAL_DEV_SAMPLE","product":"TELETALK-DATA-2GB","priceVersion":"PRICE-TELETALK-DATA-2GB-V1"}','2027-08-16 00:00:00.000','2026-08-16 00:00:00.000');

INSERT IGNORE INTO hz_order
(order_ref,project_subject_ref,quote_ref,order_state,payment_state,upstream_debit_state,delivery_state,
 refund_state,projection_version,aggregate_version,allowed_action,created_at,updated_at,environment,evidence_level,authority_state) VALUES
('ORDER-DEV-PAYMENT','DEV-BUYER-001','QUOTE-DEV-PAYMENT','AWAITING_PAYMENT','PENDING','ABSENT_CONFIRMED','ABSENT_CONFIRMED','NOT_APPLICABLE',1,1,'QUERY_PAYMENT','2026-08-16 00:00:00.000','2026-08-16 00:00:00.000','MANUAL_DEV_SAMPLE','L1','NON_PRODUCTION'),
('ORDER-DEV-TOPUP','DEV-BUYER-001','QUOTE-DEV-TOPUP','TOPUP_PROCESSING','CONFIRMED','CONFIRMED','PROCESSING','NOT_APPLICABLE',3,3,'QUERY_TOPUP','2026-08-16 00:00:00.000','2026-08-16 00:10:00.000','MANUAL_DEV_SAMPLE','L1','NON_PRODUCTION'),
('ORDER-DEV-SUCCESS','DEV-BUYER-001','QUOTE-DEV-SUCCESS','COMPLETED','CONFIRMED','CONFIRMED','SUCCEEDED','NOT_APPLICABLE',4,4,NULL,'2026-08-16 00:00:00.000','2026-08-16 00:15:00.000','MANUAL_DEV_SAMPLE','L1','NON_PRODUCTION'),
('ORDER-DEV-REFUND','DEV-BUYER-001','QUOTE-DEV-REFUND','REFUNDED','CONFIRMED','CONFIRMED','FAILED','REFUNDED',5,5,NULL,'2026-08-16 00:00:00.000','2026-08-16 00:20:00.000','MANUAL_DEV_SAMPLE','L1','NON_PRODUCTION');
