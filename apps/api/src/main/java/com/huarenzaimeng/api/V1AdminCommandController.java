package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.huarenzaimeng.core.PricingCalculator;
import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@RestController
@Profile("release-mysql")
@ConditionalOnProperty(name="hz.admin-command.enabled",havingValue="true",matchIfMissing=true)
@RequestMapping("/admin-command/v1")
class V1AdminCommandController {
    private static final Set<String> RESOURCES=Set.of("cities","directory-entries","holidays","news","products","product-mappings","channels","price-versions");
    private static final Set<String> CONTENT_RESOURCES=Set.of("directory-entries","holidays","news");
    // Legacy CONTENT is intentionally mapped to the operator side only.
    private static final Set<String> CONTENT_OPERATOR_ROLES=Set.of("CONTENT","CONTENT_OPERATOR","SUPER_ADMIN");
    private static final Set<String> ACTIONS=Set.of("submit","publish","unpublish","enable","disable");
    private final JdbcTemplate jdbc;
    private final PricingCalculator pricingCalculator=new PricingCalculator();
    V1AdminCommandController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @PostMapping("/{resource}") @Transactional ResponseEntity<?> create(
            @PathVariable String resource,@RequestHeader("Idempotency-Key") String idempotency,
            @RequestBody JsonNode body,HttpServletRequest request) {
        if(!RESOURCES.contains(resource))return ResponseEntity.notFound().build();
        if(!allowedForCommand(request,resource,"create"))return ResponseEntity.status(403).build();
        String ref=required(body,"ref"); String reason=required(body,"reason");
        validatePayload(resource,body,true); textMax(reason,500,"reason");
        if(!safe(ref)||!safeIdempotency(idempotency)) return ResponseEntity.badRequest().build();
        String action="CREATE",requestDigest=digest(body.toString());
        ResponseEntity<?> prior=claim(idempotency,resource,ref,action,requestDigest);
        if(prior!=null)return prior;
        insert(resource,ref,body);
        return complete(resource,ref,action,idempotency,requestDigest,0,1,reason,body.toString(),request);
    }

    @PutMapping("/{resource}/{ref}") @Transactional ResponseEntity<?> update(
            @PathVariable String resource,@PathVariable String ref,@RequestHeader("Idempotency-Key") String idempotency,
            @RequestBody JsonNode body,HttpServletRequest request) {
        if(!RESOURCES.contains(resource))return ResponseEntity.notFound().build();
        if(!allowedForCommand(request,resource,"update"))return ResponseEntity.status(403).build();
        long expected=requiredLong(body,"expectedVersion"); String reason=required(body,"reason");
        validatePayload(resource,body,false); textMax(reason,500,"reason");
        if(!safe(ref)||!safeIdempotency(idempotency)||expected<1) return ResponseEntity.badRequest().build();
        String action="UPDATE",requestDigest=digest(body.toString());
        ResponseEntity<?> prior=claim(idempotency,resource,ref,action,requestDigest);
        if(prior!=null)return prior;
        if(updateDraft(resource,ref,expected,body)!=1)throw new CommandConflict("VERSION_CONFLICT");
        return complete(resource,ref,action,idempotency,requestDigest,expected,expected+1,reason,body.toString(),request);
    }

    @PostMapping("/{resource}/{ref}/{action}") @Transactional ResponseEntity<?> transition(
            @PathVariable String resource,@PathVariable String ref,@PathVariable String action,
            @RequestHeader("Idempotency-Key") String idempotency,@RequestBody JsonNode body,HttpServletRequest request) {
        if(!RESOURCES.contains(resource)||!ACTIONS.contains(action))return ResponseEntity.notFound().build();
        if(!allowedForCommand(request,resource,action))return ResponseEntity.status(403).build();
        long expected=requiredLong(body,"expectedVersion"); String reason=required(body,"reason");
        textMax(reason,500,"reason");
        if(!safe(ref)||!safeIdempotency(idempotency)||expected<1)return ResponseEntity.badRequest().build();
        String actionCode=action.toUpperCase(Locale.ROOT),requestDigest=digest(body.toString());
        ensureActionAllowed(resource,action);
        ResponseEntity<?> prior=claim(idempotency,resource,ref,actionCode,requestDigest);
        if(prior!=null)return prior;
        String state=switch(action){case"submit"->"UNDER_REVIEW";case"publish","enable"->activeState(resource);default->inactiveState(resource);};
        if(transition(resource,ref,expected,action,state,actor(request))!=1)throw new CommandConflict("STATE_OR_VERSION_CONFLICT");
        return complete(resource,ref,actionCode,idempotency,requestDigest,expected,expected+1,reason,body.toString(),request);
    }

    @PostMapping("/price-versions/trial") ResponseEntity<?> trial(@RequestBody JsonNode body,HttpServletRequest request) {
        if(!superAdmin(request))return ResponseEntity.status(403).build();
        PriceEvaluation evaluation=evaluatePrice(required(body,"productRef"),body,Instant.now());
        PricingCalculator.PriceResult result=evaluation.result();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.ofEntries(
                Map.entry("supplierCost",evaluation.spec().supplierCost()),Map.entry("finalAmountCny",result.finalAmountCny()),
                Map.entry("fxSnapshotRef",evaluation.spec().fxSnapshotRef()),Map.entry("fxRate",evaluation.spec().fxRate()),
                Map.entry("marginCny",result.marginAmountCny()),Map.entry("marginRate",result.marginRate()),
                Map.entry("roundingRule",evaluation.spec().roundingRule()),Map.entry("minimumMarginSatisfied",result.minimumMarginSatisfied()),Map.entry("steps",result.steps()),
                Map.entry("formula","V1_SEQUENTIAL_PRICING_KERNEL"),Map.entry("persistent",false)));
    }

    private void insert(String r,String ref,JsonNode b){ Instant now=Instant.now(); switch(r){
        case"cities"->jdbc.update("INSERT INTO hz_city(city_code,country_code,display_name,local_name,timezone_id,city_state,sort_order,aggregate_version,updated_at) VALUES(?,?,?,?,?,'DRAFT',?,1,?)",ref,required(b,"countryCode"),required(b,"displayName"),required(b,"localName"),required(b,"timezoneId"),requiredInt(b,"sortOrder"),Timestamp.from(now));
        case"directory-entries"->jdbc.update("INSERT INTO hz_directory_entry(entry_ref,city_code,category_code,display_name,summary,local_address,phone,source_label,verified_at,valid_until,publish_state,aggregate_version,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,'DRAFT',1,?)",ref,required(b,"cityCode"),required(b,"category"),required(b,"displayName"),required(b,"summary"),required(b,"localAddress"),required(b,"phone"),required(b,"sourceLabel"),Timestamp.from(now),timestamp(b,"validUntil"),Timestamp.from(now));
        case"holidays"->jdbc.update("INSERT INTO hz_holiday_rule(rule_ref,country_code,rule_type,display_name,start_date,end_date,weekend_days,source_label,publish_state,aggregate_version,effective_from,effective_until,updated_at) VALUES(?,?,?,?,?,?,?,?,'DRAFT',1,?,?,?)",ref,required(b,"countryCode"),required(b,"ruleType"),required(b,"displayName"),date(b,"startDate"),date(b,"endDate"),optional(b,"weekendDays"),required(b,"sourceLabel"),Timestamp.from(now),timestamp(b,"effectiveUntil"),Timestamp.from(now));
        case"news"->jdbc.update("INSERT INTO hz_news_article(article_ref,category_code,title,summary,body_text,source_label,author_name,publish_state,aggregate_version,published_at,valid_until,updated_at) VALUES(?,?,?,?,?,?,?,'DRAFT',1,?,?,?)",ref,required(b,"category"),required(b,"title"),required(b,"summary"),required(b,"bodyText"),required(b,"sourceLabel"),required(b,"editor"),Timestamp.from(now),timestamp(b,"validUntil"),Timestamp.from(now));
        case"products"->jdbc.update("INSERT INTO hz_platform_product(platform_product_ref,country_code,operator_code,product_type,display_name,benefit_text,denomination_bdt,data_allowance_mb,voice_minutes,sms_count,validity_text,provider_code,provider_sku,channel_priority,phone_rule,sale_start_at,sale_end_at,enable_state,source_mode,aggregate_version,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT','MANUAL_ADMIN_INPUT',1,?)",ref,required(b,"countryCode"),required(b,"operatorCode"),required(b,"productType"),required(b,"displayName"),required(b,"benefitText"),decimalNullable(b,"denominationBdt"),longNullable(b,"dataAllowanceMb"),intNullable(b,"voiceMinutes"),intNullable(b,"smsCount"),optional(b,"validityText"),required(b,"providerCode"),required(b,"providerSku"),requiredInt(b,"channelPriority"),optional(b,"phoneRule"),timestampNullable(b,"saleStartAt"),timestampNullable(b,"saleEndAt"),Timestamp.from(now));
        case"channels"->jdbc.update("INSERT INTO hz_provider_channel(channel_ref,provider_code,display_name,channel_priority,channel_state,aggregate_version,updated_at) VALUES(?,?,?,?,'DRAFT',1,?)",ref,required(b,"providerCode"),required(b,"displayName"),requiredInt(b,"channelPriority"),Timestamp.from(now));
        case"price-versions"->insertPriceVersion(ref,b,now);
        default->throw new IllegalArgumentException("RESOURCE_INVALID");
    }}

    private int updateDraft(String r,String ref,long v,JsonNode b){Timestamp now=Timestamp.from(Instant.now());return switch(r){
        case"cities"->jdbc.update("UPDATE hz_city SET display_name=?,local_name=?,timezone_id=?,sort_order=?,aggregate_version=aggregate_version+1,updated_at=? WHERE city_code=? AND aggregate_version=? AND city_state='DRAFT'",required(b,"displayName"),required(b,"localName"),required(b,"timezoneId"),requiredInt(b,"sortOrder"),now,ref,v);
        case"directory-entries"->jdbc.update("UPDATE hz_directory_entry SET city_code=?,category_code=?,display_name=?,summary=?,local_address=?,phone=?,source_label=?,valid_until=?,aggregate_version=aggregate_version+1,updated_at=? WHERE entry_ref=? AND aggregate_version=? AND publish_state='DRAFT'",required(b,"cityCode"),required(b,"category"),required(b,"displayName"),required(b,"summary"),required(b,"localAddress"),required(b,"phone"),required(b,"sourceLabel"),timestamp(b,"validUntil"),now,ref,v);
        case"holidays"->jdbc.update("UPDATE hz_holiday_rule SET display_name=?,start_date=?,end_date=?,weekend_days=?,source_label=?,effective_until=?,aggregate_version=aggregate_version+1,updated_at=? WHERE rule_ref=? AND aggregate_version=? AND publish_state='DRAFT'",required(b,"displayName"),date(b,"startDate"),date(b,"endDate"),optional(b,"weekendDays"),required(b,"sourceLabel"),timestamp(b,"effectiveUntil"),now,ref,v);
        case"news"->jdbc.update("UPDATE hz_news_article SET category_code=?,title=?,summary=?,body_text=?,source_label=?,author_name=?,valid_until=?,aggregate_version=aggregate_version+1,updated_at=? WHERE article_ref=? AND aggregate_version=? AND publish_state='DRAFT'",required(b,"category"),required(b,"title"),required(b,"summary"),required(b,"bodyText"),required(b,"sourceLabel"),required(b,"editor"),timestamp(b,"validUntil"),now,ref,v);
        case"products"->jdbc.update("UPDATE hz_platform_product SET country_code=?,operator_code=?,product_type=?,display_name=?,benefit_text=?,denomination_bdt=?,data_allowance_mb=?,voice_minutes=?,sms_count=?,validity_text=?,provider_code=?,provider_sku=?,channel_priority=?,phone_rule=?,sale_start_at=?,sale_end_at=?,aggregate_version=aggregate_version+1,updated_at=? WHERE platform_product_ref=? AND aggregate_version=? AND enable_state='DRAFT'",required(b,"countryCode"),required(b,"operatorCode"),required(b,"productType"),required(b,"displayName"),required(b,"benefitText"),decimalNullable(b,"denominationBdt"),longNullable(b,"dataAllowanceMb"),intNullable(b,"voiceMinutes"),intNullable(b,"smsCount"),optional(b,"validityText"),required(b,"providerCode"),required(b,"providerSku"),requiredInt(b,"channelPriority"),optional(b,"phoneRule"),timestampNullable(b,"saleStartAt"),timestampNullable(b,"saleEndAt"),now,ref,v);
        case"product-mappings"->jdbc.update("UPDATE hz_platform_product SET catalog_batch_ref=?,raw_sku_name=?,raw_benefit_text=?,supplier_cost=?,settlement_currency=?,supplier_availability=?,catalog_synced_at=?,normalized_type=?,normalized_operator=?,mapping_state=?,mapping_failure_reason=?,aggregate_version=aggregate_version+1,updated_at=? WHERE platform_product_ref=? AND aggregate_version=? AND enable_state='DRAFT'",required(b,"catalogBatchRef"),required(b,"rawSkuName"),required(b,"rawBenefitText"),decimal(b,"supplierCost"),required(b,"settlementCurrency"),required(b,"supplierAvailability"),timestamp(b,"catalogSyncedAt"),optional(b,"normalizedType"),required(b,"normalizedOperator"),required(b,"mappingState"),optional(b,"mappingFailureReason"),now,ref,v);
        case"channels"->jdbc.update("UPDATE hz_provider_channel SET display_name=?,channel_priority=?,aggregate_version=aggregate_version+1,updated_at=? WHERE channel_ref=? AND aggregate_version=? AND channel_state='DRAFT'",required(b,"displayName"),requiredInt(b,"channelPriority"),now,ref,v);
        case"price-versions"->updatePriceVersion(ref,v,b,now);
        default->0;};}

    private void insertPriceVersion(String ref,JsonNode b,Instant now){
        PriceEvaluation price=evaluatePrice(required(b,"productRef"),b,now);
        jdbc.update("INSERT INTO hz_price_version(price_version_ref,platform_product_ref,final_amount_cny,supplier_cost,settlement_currency,supplier_source_ref,fx_source,fx_snapshot_ref,fx_direction,fx_rate,fx_updated_at,fx_valid_until,buffer_rate,markup_rate,wechat_fee_rate,tax_rate,minimum_margin_rate,rounding_rule,promotion_bearer,pricing_scope,price_state,effective_from,effective_until,enabled_by,aggregate_version,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?,1,?)",
                ref,required(b,"productRef"),price.result().finalAmountCny(),price.spec().supplierCost(),price.spec().settlementCurrency(),price.spec().supplierSourceRef(),price.spec().fxSource(),price.spec().fxSnapshotRef(),price.spec().fxDirection(),price.spec().fxRate(),price.spec().observedAt(),price.spec().validUntil(),price.spec().bufferRate(),price.spec().markupRate(),price.spec().wechatFeeRate(),price.spec().taxRate(),price.spec().minimumMarginRate(),price.spec().roundingRule(),required(b,"promotionBearer"),required(b,"pricingScope"),timestamp(b,"effectiveFrom"),timestamp(b,"effectiveUntil"),"SUPER_ADMIN",Timestamp.from(now));
    }
    private int updatePriceVersion(String ref,long version,JsonNode b,Timestamp now){
        List<String> productRefs=jdbc.queryForList("SELECT platform_product_ref FROM hz_price_version WHERE price_version_ref=? AND aggregate_version=? AND price_state='DRAFT'",String.class,ref,version);
        if(productRefs.size()!=1) return 0;
        String productRef=productRefs.get(0);
        PriceEvaluation price=evaluatePrice(productRef,b,now.toInstant());
        return jdbc.update("UPDATE hz_price_version SET final_amount_cny=?,supplier_cost=?,settlement_currency=?,supplier_source_ref=?,fx_source=?,fx_snapshot_ref=?,fx_direction=?,fx_rate=?,fx_updated_at=?,fx_valid_until=?,buffer_rate=?,markup_rate=?,wechat_fee_rate=?,tax_rate=?,minimum_margin_rate=?,rounding_rule=?,promotion_bearer=?,pricing_scope=?,effective_from=?,effective_until=?,aggregate_version=aggregate_version+1,updated_at=? WHERE price_version_ref=? AND aggregate_version=? AND price_state='DRAFT'",
                price.result().finalAmountCny(),price.spec().supplierCost(),price.spec().settlementCurrency(),price.spec().supplierSourceRef(),price.spec().fxSource(),price.spec().fxSnapshotRef(),price.spec().fxDirection(),price.spec().fxRate(),price.spec().observedAt(),price.spec().validUntil(),price.spec().bufferRate(),price.spec().markupRate(),price.spec().wechatFeeRate(),price.spec().taxRate(),price.spec().minimumMarginRate(),price.spec().roundingRule(),required(b,"promotionBearer"),required(b,"pricingScope"),timestamp(b,"effectiveFrom"),timestamp(b,"effectiveUntil"),now,ref,version);
    }

    private int transition(String r,String ref,long v,String action,String state,String actor){
        String table=switch(r){case"cities"->"hz_city";case"directory-entries"->"hz_directory_entry";case"holidays"->"hz_holiday_rule";case"news"->"hz_news_article";case"products"->"hz_platform_product";case"channels"->"hz_provider_channel";case"price-versions"->"hz_price_version";default->throw new IllegalArgumentException("RESOURCE_INVALID");};
        String id=switch(r){case"cities"->"city_code";case"directory-entries"->"entry_ref";case"holidays"->"rule_ref";case"news"->"article_ref";case"products"->"platform_product_ref";case"channels"->"channel_ref";default->"price_version_ref";};
        String column=switch(r){case"cities"->"city_state";case"products"->"enable_state";case"channels"->"channel_state";case"price-versions"->"price_state";default->"publish_state";};
        String before=switch(action){case"submit"->"DRAFT";case"publish"->"UNDER_REVIEW";case"unpublish"->"PUBLISHED";case"enable"->"products".equals(r)?"UNDER_REVIEW":"DRAFT";case"disable"->activeState(r);default->throw new IllegalArgumentException("ACTION_INVALID");};
        if("price-versions".equals(r)&&"enable".equals(action))ensurePriceCanActivate(ref);
        if("publish".equals(action))ensureContentApproved(r,ref,v,actor);
        if("products".equals(r)&&"enable".equals(action))ensureContentApproved(r,ref,v,actor);
        return jdbc.update("UPDATE "+table+" SET "+column+"=?,aggregate_version=aggregate_version+1,updated_at=? WHERE "+id+"=? AND aggregate_version=? AND "+column+"=?",state,Timestamp.from(Instant.now()),ref,v,before);
    }

    private ResponseEntity<?> claim(String key,String resource,String ref,String action,String requestDigest){
        try {
            jdbc.update("INSERT INTO hz_v1_admin_command(command_ref,idempotency_key,object_type,object_ref,action_type,request_digest,command_status,resulting_version,audit_ref,created_at) VALUES(?,?,?,?,?,?,'CLAIMED',NULL,NULL,?)",
                    "CMD-"+UUID.randomUUID(),key,resource,ref,action,requestDigest,Timestamp.from(Instant.now()));
            return null;
        } catch(DuplicateKeyException duplicate) {
            List<Map<String,Object>> rows=jdbc.queryForList("SELECT object_type,object_ref,action_type,request_digest,command_status,resulting_version,audit_ref FROM hz_v1_admin_command WHERE idempotency_key=?",key);
            if(rows.isEmpty())throw new CommandConflict("IDEMPOTENCY_CONFLICT");
            Map<String,Object> row=rows.get(0);
            if(!Objects.equals(resource,row.get("object_type"))||!Objects.equals(ref,row.get("object_ref"))||!Objects.equals(action,row.get("action_type"))||!Objects.equals(requestDigest,row.get("request_digest")))
                throw new CommandConflict("IDEMPOTENCY_CONFLICT");
            if(!"COMPLETED".equals(row.get("command_status")))throw new CommandConflict("IDEMPOTENCY_IN_PROGRESS");
            return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("objectRef",ref,"version",row.get("resulting_version"),"auditRef",row.get("audit_ref"),"replayed",true));
        }
    }

    private ResponseEntity<?> complete(String resource,String ref,String action,String key,String requestDigest,long before,long after,String reason,String snapshot,HttpServletRequest request){
        String audit="AUD-"+UUID.randomUUID(); Instant now=Instant.now();
        jdbc.update("INSERT INTO hz_v1_admin_audit(audit_ref,object_type,object_ref,action_type,actor_ref,reason,before_version,after_version,occurred_at) VALUES(?,?,?,?,?,?,?,?,?)",audit,resource,ref,action,String.valueOf(request.getAttribute(AdminSessionFilter.TRUSTED_USER)),reason,before,after,Timestamp.from(now));
        if(Set.of("directory-entries","holidays","news","products").contains(resource))
            jdbc.update("INSERT INTO hz_content_version_history(history_ref,object_type,object_ref,object_version,snapshot_json,action_type,actor_ref,reason,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    "HIS-"+UUID.randomUUID(),resource,ref,after,snapshot,action,String.valueOf(request.getAttribute(AdminSessionFilter.TRUSTED_USER)),reason,Timestamp.from(now));
        int finished=jdbc.update("UPDATE hz_v1_admin_command SET command_status='COMPLETED',resulting_version=?,audit_ref=? WHERE idempotency_key=? AND object_type=? AND object_ref=? AND action_type=? AND request_digest=? AND command_status='CLAIMED'",after,audit,key,resource,ref,action,requestDigest);
        if(finished!=1)throw new CommandConflict("IDEMPOTENCY_COMPLETION_CONFLICT");
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("objectRef",ref,"version",after,"auditRef",audit,"replayed",false));
    }

    private static void ensureActionAllowed(String resource,String action){
        boolean content=Set.of("directory-entries","holidays","news").contains(resource);
        boolean configurable=Set.of("cities","products","channels","price-versions").contains(resource);
        boolean productSubmit="products".equals(resource)&&"submit".equals(action);
        if((Set.of("submit","publish","unpublish").contains(action)&&!content&&!productSubmit)||(Set.of("enable","disable").contains(action)&&!configurable))
            throw new IllegalArgumentException("ACTION_RESOURCE_INVALID");
    }
    private void ensurePriceCanActivate(String ref){
        List<String> productRefs=jdbc.queryForList("SELECT platform_product_ref FROM hz_price_version WHERE price_version_ref=?",String.class,ref);
        if(productRefs.size()!=1)throw new CommandConflict("PRICE_ACTIVATION_PRECONDITION_FAILED");
        // Serializes activation for the whole product price set inside the endpoint transaction.
        List<String> locked=jdbc.queryForList("SELECT platform_product_ref FROM hz_platform_product WHERE platform_product_ref=? FOR UPDATE",String.class,productRefs.get(0));
        if(locked.size()!=1)throw new CommandConflict("PRICE_ACTIVATION_PRECONDITION_FAILED");
        Integer valid=jdbc.queryForObject("SELECT COUNT(*) FROM hz_price_version v JOIN hz_platform_product p ON p.platform_product_ref=v.platform_product_ref JOIN hz_provider_channel c ON c.provider_code=p.provider_code WHERE v.price_version_ref=? AND v.effective_from<v.effective_until AND v.effective_until>CURRENT_TIMESTAMP(3) AND p.mapping_state='MAPPED' AND p.supplier_availability='AVAILABLE' AND c.channel_state='ENABLED' AND p.enable_state IN ('DRAFT','ENABLED') AND NOT EXISTS (SELECT 1 FROM hz_price_version x WHERE x.platform_product_ref=v.platform_product_ref AND x.price_state='ACTIVE' AND x.price_version_ref<>v.price_version_ref AND x.effective_from<v.effective_until AND x.effective_until>v.effective_from)",Integer.class,ref);
        if(valid==null||valid!=1)throw new CommandConflict("PRICE_ACTIVATION_PRECONDITION_FAILED");
        Map<String,Object> row=jdbc.queryForMap("SELECT v.platform_product_ref,v.supplier_cost,v.settlement_currency,v.supplier_source_ref,v.fx_source,v.fx_snapshot_ref,v.fx_direction,v.buffer_rate,v.markup_rate,v.wechat_fee_rate,v.tax_rate,v.minimum_margin_rate,v.rounding_rule,v.promotion_bearer,v.final_amount_cny FROM hz_price_version v WHERE v.price_version_ref=?",ref);
        PriceEvaluation evaluation=evaluatePriceFromRow(row,Instant.now());
        if(evaluation.result().finalAmountCny().compareTo((BigDecimal)row.get("final_amount_cny"))!=0)throw new CommandConflict("PRICE_CALCULATION_DRIFT");
    }

    private void ensureContentApproved(String resource,String ref,long version,String actor){
        List<Map<String,Object>> approvals=jdbc.queryForList("SELECT submitter_ref,reviewer_ref FROM hz_content_review_task WHERE object_type=? AND object_ref=? AND object_version=? AND review_state='APPROVED'",resource,ref,version);
        if(approvals.size()!=1)throw new CommandConflict("CONTENT_APPROVAL_REQUIRED");
        Map<String,Object> approval=approvals.get(0);
        if(actor.equals(String.valueOf(approval.get("submitter_ref")))||actor.equals(String.valueOf(approval.get("reviewer_ref"))))
            throw new CommandConflict("PUBLISH_DUTY_SEPARATION_REQUIRED");
    }

    private PriceEvaluation evaluatePrice(String productRef,JsonNode body,Instant quotedAt){
        SupplierSnapshot supplier=loadSupplierSnapshot(productRef);
        compareSupplierRequest(body,supplier);
        return calculatePrice(new PriceSpec(productRef,supplier.cost(),supplier.currency(),supplier.sourceRef(),
                required(body,"fxSource"),required(body,"fxSnapshotRef"),required(body,"fxDirection"),null,null,null,
                decimal(body,"bufferRate"),decimal(body,"markupRate"),decimal(body,"wechatFeeRate"),decimal(body,"taxRate"),
                decimal(body,"minimumMarginRate"),required(body,"roundingRule"),required(body,"promotionBearer")),quotedAt);
    }

    private PriceEvaluation evaluatePriceFromRow(Map<String,Object> row,Instant quotedAt){
        String productRef=String.valueOf(row.get("platform_product_ref"));SupplierSnapshot supplier=loadSupplierSnapshot(productRef);
        if(supplier.cost().compareTo(money(row,"supplier_cost"))!=0||!supplier.currency().equals(String.valueOf(row.get("settlement_currency")))
                ||!supplier.sourceRef().equals(String.valueOf(row.get("supplier_source_ref"))))throw new CommandConflict("SUPPLIER_SNAPSHOT_DRIFT");
        return calculatePrice(new PriceSpec(productRef,supplier.cost(),supplier.currency(),supplier.sourceRef(),
                String.valueOf(row.get("fx_source")),String.valueOf(row.get("fx_snapshot_ref")),String.valueOf(row.get("fx_direction")),null,null,null,
                money(row,"buffer_rate"),money(row,"markup_rate"),money(row,"wechat_fee_rate"),money(row,"tax_rate"),
                money(row,"minimum_margin_rate"),String.valueOf(row.get("rounding_rule")),String.valueOf(row.get("promotion_bearer"))),quotedAt);
    }

    private PriceEvaluation calculatePrice(PriceSpec requested,Instant quotedAt){
        List<Map<String,Object>> snapshots=jdbc.queryForList("SELECT source_code,base_currency,quote_currency,rate_value,observed_at,valid_until FROM hz_fx_rate_snapshot WHERE fx_snapshot_ref=?",requested.fxSnapshotRef());
        if(snapshots.size()!=1)throw new CommandConflict("FX_SNAPSHOT_NOT_FOUND");
        Map<String,Object> fx=snapshots.get(0);String source=String.valueOf(fx.get("source_code"));
        String base=String.valueOf(fx.get("base_currency")),quote=String.valueOf(fx.get("quote_currency"));
        if(!requested.fxSource().equals(source)||!requested.settlementCurrency().equals(base)||!"CNY".equals(quote)
                ||!(base+"_TO_CNY").equals(requested.fxDirection()))throw new CommandConflict("FX_SNAPSHOT_DIRECTION_MISMATCH");
        Timestamp observed=sqlTimestamp(fx,"observed_at"),valid=sqlTimestamp(fx,"valid_until");BigDecimal rate=money(fx,"rate_value");
        PriceSpec spec=new PriceSpec(requested.productRef(),requested.supplierCost(),requested.settlementCurrency(),requested.supplierSourceRef(),source,
                requested.fxSnapshotRef(),requested.fxDirection(),rate,observed,valid,requested.bufferRate(),requested.markupRate(),
                requested.wechatFeeRate(),requested.taxRate(),requested.minimumMarginRate(),requested.roundingRule(),requested.promotionBearer());
        BigDecimal increment=spec.roundingRule().endsWith("0_10")?new BigDecimal("0.10"):new BigDecimal("0.01");
        PricingCalculator.PriceResult result=pricingCalculator.calculate(new PricingCalculator.PriceInput(
                spec.supplierCost(),spec.settlementCurrency(),spec.fxRate(),spec.fxSnapshotRef(),valid.toInstant(),quotedAt,List.of(
                new PricingCalculator.Adjustment("FX_BUFFER",spec.bufferRate(),PricingCalculator.Basis.CONVERTED_COST,spec.fxSnapshotRef()),
                new PricingCalculator.Adjustment("WECHAT_FEE",spec.wechatFeeRate(),PricingCalculator.Basis.RUNNING_SUBTOTAL,"WECHAT_FEE"),
                new PricingCalculator.Adjustment("TAX",spec.taxRate(),PricingCalculator.Basis.RUNNING_SUBTOTAL,"TAX_POLICY"),
                new PricingCalculator.Adjustment("PLATFORM_MARKUP",spec.markupRate(),PricingCalculator.Basis.RUNNING_SUBTOTAL,"PLATFORM_POLICY")),
                BigDecimal.ZERO,spec.promotionBearer(),spec.minimumMarginRate(),increment,spec.roundingRule()));
        if(!result.minimumMarginSatisfied())throw new CommandConflict("MINIMUM_MARGIN_NOT_SATISFIED");
        return new PriceEvaluation(spec,result);
    }

    private SupplierSnapshot loadSupplierSnapshot(String productRef){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT i.supplier_cost,i.settlement_currency,i.batch_ref,i.provider_sku FROM hz_platform_product p JOIN hz_supplier_catalog_batch_snapshot b ON b.batch_ref=p.catalog_batch_ref AND b.provider_code=p.provider_code JOIN hz_supplier_catalog_item_snapshot i ON i.batch_ref=b.batch_ref AND i.provider_sku=p.provider_sku JOIN hz_provider_channel c ON c.provider_code=p.provider_code WHERE p.platform_product_ref=? AND p.mapping_state='MAPPED' AND p.supplier_availability='AVAILABLE' AND i.mapping_state='MAPPED' AND i.availability='AVAILABLE' AND c.channel_state='ENABLED'",productRef);
        if(rows.size()!=1)throw new CommandConflict("SUPPLIER_SNAPSHOT_BINDING_INVALID");
        Map<String,Object> row=rows.get(0);String sourceRef=String.valueOf(row.get("batch_ref"))+"/"+String.valueOf(row.get("provider_sku"));
        return new SupplierSnapshot(money(row,"supplier_cost"),String.valueOf(row.get("settlement_currency")),sourceRef);
    }
    private static void compareSupplierRequest(JsonNode body,SupplierSnapshot snapshot){
        if(body.has("supplierCost")){
            if(!body.path("supplierCost").isNumber())throw new IllegalArgumentException("supplierCost invalid");
            if(body.path("supplierCost").decimalValue().compareTo(snapshot.cost())!=0)throw new CommandConflict("SUPPLIER_COST_MISMATCH");
        }
        if(body.has("settlementCurrency")&&!snapshot.currency().equals(required(body,"settlementCurrency")))throw new CommandConflict("SUPPLIER_CURRENCY_MISMATCH");
        if(body.has("supplierSourceRef")&&!snapshot.sourceRef().equals(required(body,"supplierSourceRef")))throw new CommandConflict("SUPPLIER_SOURCE_MISMATCH");
    }

    private static BigDecimal money(Map<String,Object> row,String key){Object value=row.get(key);if(value instanceof BigDecimal decimal)return decimal;if(value instanceof Number number)return new BigDecimal(number.toString());throw new CommandConflict("PRICE_DATA_INVALID");}
    private static Timestamp sqlTimestamp(Map<String,Object> row,String key){Object value=row.get(key);if(value instanceof Timestamp timestamp)return timestamp;if(value instanceof java.time.LocalDateTime local)return Timestamp.valueOf(local);throw new CommandConflict("PRICE_DATA_INVALID");}
    private record SupplierSnapshot(BigDecimal cost,String currency,String sourceRef){}
    private record PriceSpec(String productRef,BigDecimal supplierCost,String settlementCurrency,String supplierSourceRef,String fxSource,String fxSnapshotRef,
                             String fxDirection,BigDecimal fxRate,Timestamp observedAt,Timestamp validUntil,BigDecimal bufferRate,
                             BigDecimal markupRate,BigDecimal wechatFeeRate,BigDecimal taxRate,BigDecimal minimumMarginRate,
                             String roundingRule,String promotionBearer){}
    private record PriceEvaluation(PriceSpec spec,PricingCalculator.PriceResult result){}

    @ExceptionHandler(CommandConflict.class) ResponseEntity<?> conflict(CommandConflict conflict){return ResponseEntity.status(409).body(Map.of("projectCode",conflict.getMessage()));}
    @ExceptionHandler(DuplicateKeyException.class) ResponseEntity<?> duplicate(DuplicateKeyException error){return ResponseEntity.status(409).body(Map.of("projectCode","ADMIN_COMMAND_DUPLICATE"));}
    @ExceptionHandler({IllegalArgumentException.class,DataIntegrityViolationException.class}) ResponseEntity<?> badInput(RuntimeException error){return ResponseEntity.badRequest().body(Map.of("projectCode","ADMIN_COMMAND_INVALID"));}
    private static final class CommandConflict extends RuntimeException { CommandConflict(String code){super(code);} }
    private static boolean superAdmin(HttpServletRequest r){return"SUPER_ADMIN".equals(r.getAttribute(AdminSessionFilter.TRUSTED_ROLE));}
    private static boolean allowedForCommand(HttpServletRequest request,String resource,String operation){
        if(CONTENT_RESOURCES.contains(resource)&&Set.of("create","update","submit").contains(operation))
            return CONTENT_OPERATOR_ROLES.contains(String.valueOf(request.getAttribute(AdminSessionFilter.TRUSTED_ROLE)));
        // Publishing and unpublishing are privileged release actions, never reviewer/operator actions.
        if(CONTENT_RESOURCES.contains(resource)&&Set.of("publish","unpublish").contains(operation))return superAdmin(request);
        return superAdmin(request);
    }
    private static String actor(HttpServletRequest request){return String.valueOf(request.getAttribute(AdminSessionFilter.TRUSTED_USER));}
    private static String activeState(String r){return switch(r){case"cities"->"ACTIVE";case"products","channels"->"ENABLED";case"price-versions"->"ACTIVE";default->"PUBLISHED";};}
    private static String inactiveState(String r){return switch(r){case"cities"->"INACTIVE";case"products","channels"->"DISABLED";case"price-versions"->"INACTIVE";default->"UNPUBLISHED";};}
    private static String required(JsonNode n,String k){if(!n.path(k).isTextual()||n.path(k).textValue().isBlank())throw new IllegalArgumentException(k+" required");return n.path(k).textValue();}
    private static String optional(JsonNode n,String k){return n.path(k).isTextual()?n.path(k).textValue():null;}
    private static long requiredLong(JsonNode n,String k){return n.path(k).isIntegralNumber()?n.path(k).longValue():-1;}
    private static int requiredInt(JsonNode n,String k){if(!n.path(k).canConvertToInt())throw new IllegalArgumentException(k+" required");return n.path(k).intValue();}
    private static Integer intNullable(JsonNode n,String k){return n.path(k).canConvertToInt()?n.path(k).intValue():null;}
    private static Long longNullable(JsonNode n,String k){return n.path(k).isIntegralNumber()?n.path(k).longValue():null;}
    private static BigDecimal decimal(JsonNode n,String k){if(!n.path(k).isNumber())throw new IllegalArgumentException(k+" required");return n.path(k).decimalValue();}
    private static BigDecimal decimalNullable(JsonNode n,String k){return n.path(k).isNumber()?n.path(k).decimalValue():null;}
    private static Timestamp timestamp(JsonNode n,String k){return Timestamp.from(Instant.parse(required(n,k)));}
    private static Timestamp timestampNullable(JsonNode n,String k){String v=optional(n,k);return v==null||v.isBlank()?null:Timestamp.from(Instant.parse(v));}
    private static java.sql.Date date(JsonNode n,String k){String v=optional(n,k);return v==null?null:java.sql.Date.valueOf(v);}
    private static boolean safe(String v){return v!=null&&v.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");}
    private static boolean safeIdempotency(String v){return v!=null&&v.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");}
    private static String digest(String v){try{byte[]d=MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));return java.util.HexFormat.of().withUpperCase().formatHex(d);}catch(Exception e){throw new IllegalStateException(e);}}

    private static void validatePayload(String resource,JsonNode b,boolean creating){
        switch(resource){
            case"cities"->{if(creating)code(required(b,"countryCode"),2,"countryCode");textMax(required(b,"displayName"),96,"displayName");textMax(required(b,"localName"),96,"localName");textMax(required(b,"timezoneId"),64,"timezoneId");}
            case"directory-entries"->{textMax(required(b,"cityCode"),32,"cityCode");textMax(required(b,"category"),48,"category");textMax(required(b,"displayName"),160,"displayName");textMax(required(b,"summary"),500,"summary");textMax(required(b,"localAddress"),500,"localAddress");textMax(required(b,"phone"),48,"phone");textMax(required(b,"sourceLabel"),160,"sourceLabel");}
            case"holidays"->{if(creating){code(required(b,"countryCode"),2,"countryCode");enumText(required(b,"ruleType"),Set.of("HOLIDAY","WEEKEND"),"ruleType");}textMax(required(b,"displayName"),120,"displayName");nullableMax(b,"weekendDays",24);textMax(required(b,"sourceLabel"),160,"sourceLabel");}
            case"news"->{textMax(required(b,"category"),48,"category");textMax(required(b,"title"),160,"title");textMax(required(b,"summary"),500,"summary");textMax(required(b,"bodyText"),65535,"bodyText");textMax(required(b,"sourceLabel"),160,"sourceLabel");textMax(required(b,"editor"),96,"editor");}
            case"products"->{code(required(b,"countryCode"),2,"countryCode");textMax(required(b,"operatorCode"),64,"operatorCode");enumText(required(b,"productType"),Set.of("BALANCE","DATA","BUNDLE"),"productType");textMax(required(b,"displayName"),160,"displayName");textMax(required(b,"benefitText"),500,"benefitText");nullableMax(b,"validityText",96);textMax(required(b,"providerCode"),32,"providerCode");textMax(required(b,"providerSku"),128,"providerSku");nullableMax(b,"phoneRule",160);}
            case"product-mappings"->{textMax(required(b,"catalogBatchRef"),64,"catalogBatchRef");textMax(required(b,"rawSkuName"),255,"rawSkuName");textMax(required(b,"rawBenefitText"),500,"rawBenefitText");code(required(b,"settlementCurrency"),3,"settlementCurrency");enumText(required(b,"supplierAvailability"),Set.of("AVAILABLE","UNAVAILABLE","UNKNOWN"),"supplierAvailability");nullableEnum(b,"normalizedType",Set.of("BALANCE","DATA","BUNDLE"),"normalizedType");textMax(required(b,"normalizedOperator"),32,"normalizedOperator");enumText(required(b,"mappingState"),Set.of("UNMAPPED","PENDING","MAPPED","FAILED"),"mappingState");nullableMax(b,"mappingFailureReason",500);}
            case"channels"->{textMax(required(b,"providerCode"),32,"providerCode");textMax(required(b,"displayName"),120,"displayName");}
            case"price-versions"->{if(creating)textMax(required(b,"productRef"),64,"productRef");code(required(b,"settlementCurrency"),3,"settlementCurrency");textMax(required(b,"fxSource"),32,"fxSource");textMax(required(b,"fxSnapshotRef"),96,"fxSnapshotRef");enumText(required(b,"fxDirection"),Set.of("BDT_TO_CNY","CNY_TO_BDT"),"fxDirection");enumText(required(b,"roundingRule"),Set.of("CEILING_0_01","CEILING_0_10","HALF_UP_0_01","HALF_UP_0_10"),"roundingRule");enumText(required(b,"promotionBearer"),Set.of("PLATFORM","PROVIDER","SHARED","NONE"),"promotionBearer");textMax(required(b,"pricingScope"),64,"pricingScope");}
            default->throw new IllegalArgumentException("RESOURCE_INVALID");
        }
    }
    private static void nullableMax(JsonNode n,String key,int max){String value=optional(n,key);if(value!=null)textMax(value,max,key);}
    private static void nullableEnum(JsonNode n,String key,Set<String> allowed,String label){String value=optional(n,key);if(value!=null&&!value.isBlank())enumText(value,allowed,label);}
    private static void textMax(String value,int max,String key){if(value.length()>max)throw new IllegalArgumentException(key+" too long");}
    private static void code(String value,int size,String key){if(value.length()!=size||!value.matches("[A-Z]+"))throw new IllegalArgumentException(key+" invalid");}
    private static void enumText(String value,Set<String> allowed,String key){if(!allowed.contains(value))throw new IllegalArgumentException(key+" invalid");}
}
