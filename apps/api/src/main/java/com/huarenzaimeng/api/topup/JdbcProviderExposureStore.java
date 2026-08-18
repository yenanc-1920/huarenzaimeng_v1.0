package com.huarenzaimeng.api.topup;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@Profile("release-mysql")
class JdbcProviderExposureStore {
    private final JdbcTemplate jdbc;

    JdbcProviderExposureStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void reserve(String orderRef, String provider, String channel, String operator, String currency, long amount) {
        requireText(orderRef, "TOPUP_EXPOSURE_ORDER_REQUIRED");
        requireText(provider, "TOPUP_EXPOSURE_PROVIDER_REQUIRED");
        requireText(channel, "TOPUP_EXPOSURE_CHANNEL_REQUIRED");
        requireText(operator, "TOPUP_EXPOSURE_OPERATOR_REQUIRED");
        requireText(currency, "TOPUP_EXPOSURE_CURRENCY_REQUIRED");
        if (amount <= 0) throw new TopupCoordinator.Conflict("TOPUP_EXPOSURE_AMOUNT_INVALID");

        Limit limit = jdbc.query("SELECT limit_ref,max_inflight_count,max_inflight_minor,max_single_minor,enabled,aggregate_version,reserved_count,reserved_minor FROM hz_provider_limit WHERE provider_code=? AND channel_ref=? AND operator_code=? AND currency=? FOR UPDATE",
                (rs, n) -> new Limit(rs.getString(1), unsigned(rs.getBigDecimal(2)), unsigned(rs.getBigDecimal(3)),
                        unsigned(rs.getBigDecimal(4)), rs.getBoolean(5), unsigned(rs.getBigDecimal(6)),
                        unsigned(rs.getBigDecimal(7)), unsigned(rs.getBigDecimal(8))),
                provider, channel, operator, currency).stream().findFirst()
                .orElseThrow(() -> new TopupCoordinator.Conflict("TOPUP_EXPOSURE_LIMIT_MISSING"));
        Existing existing = current(orderRef);
        if (existing != null) {
            if (existing.matches(provider, channel, operator, currency, amount)) return;
            throw new TopupCoordinator.Conflict("TOPUP_EXPOSURE_IDEMPOTENCY_CONFLICT");
        }
        if (!limit.enabled()) throw new TopupCoordinator.Conflict("TOPUP_EXPOSURE_LIMIT_DISABLED");
        if (amount > limit.maxSingleMinor()) throw new TopupCoordinator.Conflict("TOPUP_EXPOSURE_SINGLE_LIMIT");
        long nextCount = addExact(limit.reservedCount(), 1);
        long nextMinor = addExact(limit.reservedMinor(), amount);
        if (nextCount > limit.maxInflightCount()) throw new TopupCoordinator.Conflict("TOPUP_EXPOSURE_COUNT_LIMIT");
        if (nextMinor > limit.maxInflightMinor()) throw new TopupCoordinator.Conflict("TOPUP_EXPOSURE_AMOUNT_LIMIT");
        jdbc.update("UPDATE hz_provider_limit SET reserved_count=?,reserved_minor=?,updated_at=CURRENT_TIMESTAMP(3) WHERE limit_ref=?",
                nextCount,nextMinor,limit.ref());
        jdbc.update("INSERT INTO hz_provider_exposure(exposure_ref,merchant_order_ref,provider_code,channel_ref,operator_code,currency,amount_minor,exposure_state,limit_version,aggregate_version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'RESERVED',?,1,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))",
                orderRef, orderRef, provider, channel, operator, currency, amount, limit.version());
    }

    void release(String orderRef) { transition(orderRef, "RELEASED"); }
    void settle(String orderRef) { transition(orderRef, "SETTLED"); }

    private void transition(String orderRef, String target) {
        Existing scope = jdbc.query("SELECT provider_code,channel_ref,operator_code,currency,amount_minor FROM hz_provider_exposure WHERE merchant_order_ref=?",
                (rs,n)->new Existing(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),unsigned(rs.getBigDecimal(5))),orderRef)
                .stream().findFirst().orElse(null);
        if(scope==null)return;
        Limit limit = jdbc.query("SELECT limit_ref,max_inflight_count,max_inflight_minor,max_single_minor,enabled,aggregate_version,reserved_count,reserved_minor FROM hz_provider_limit WHERE provider_code=? AND channel_ref=? AND operator_code=? AND currency=? FOR UPDATE",
                (rs,n)->new Limit(rs.getString(1),unsigned(rs.getBigDecimal(2)),unsigned(rs.getBigDecimal(3)),unsigned(rs.getBigDecimal(4)),rs.getBoolean(5),unsigned(rs.getBigDecimal(6)),unsigned(rs.getBigDecimal(7)),unsigned(rs.getBigDecimal(8))),
                scope.provider(),scope.channel(),scope.operator(),scope.currency()).stream().findFirst()
                .orElseThrow(()->new TopupCoordinator.Conflict("TOPUP_EXPOSURE_LIMIT_MISSING"));
        Existing current=current(orderRef);if(current==null)return;
        String state=jdbc.queryForObject("SELECT exposure_state FROM hz_provider_exposure WHERE merchant_order_ref=? FOR UPDATE",String.class,orderRef);
        if(!"RESERVED".equals(state))return;
        long nextCount=subtractExact(limit.reservedCount(),1);
        long nextMinor=subtractExact(limit.reservedMinor(),current.amount());
        jdbc.update("UPDATE hz_provider_limit SET reserved_count=?,reserved_minor=?,updated_at=CURRENT_TIMESTAMP(3) WHERE limit_ref=?",nextCount,nextMinor,limit.ref());
        jdbc.update("UPDATE hz_provider_exposure SET exposure_state=?,aggregate_version=aggregate_version+1,updated_at=CURRENT_TIMESTAMP(3) WHERE merchant_order_ref=? AND exposure_state='RESERVED'", target, orderRef);
    }

    private Existing current(String orderRef){return jdbc.query("SELECT provider_code,channel_ref,operator_code,currency,amount_minor FROM hz_provider_exposure WHERE merchant_order_ref=? FOR UPDATE",
            (rs,n)->new Existing(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),unsigned(rs.getBigDecimal(5))),orderRef).stream().findFirst().orElse(null);}

    private static long unsigned(BigDecimal value) {
        try {
            long result = value.longValueExact();
            if (result < 0) throw new ArithmeticException("negative");
            return result;
        } catch (ArithmeticException invalid) {
            throw new TopupCoordinator.Conflict("TOPUP_EXPOSURE_LIMIT_INVALID");
        }
    }

    private static long addExact(long left, long right) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException overflow) { throw new TopupCoordinator.Conflict("TOPUP_EXPOSURE_OVERFLOW"); }
    }
    private static long subtractExact(long left,long right){
        try { long value=Math.subtractExact(left,right);if(value<0)throw new ArithmeticException("negative");return value; }
        catch(ArithmeticException invalid){throw new TopupCoordinator.Conflict("TOPUP_EXPOSURE_COUNTER_INVALID");}
    }

    private static void requireText(String value, String code) {
        if (value == null || value.isBlank() || "UNSCOPED".equals(value)) throw new TopupCoordinator.Conflict(code);
    }

    private record Limit(String ref,long maxInflightCount,long maxInflightMinor,long maxSingleMinor,boolean enabled,long version,long reservedCount,long reservedMinor) {}
    private record Existing(String provider,String channel,String operator,String currency,long amount) {
        boolean matches(String p,String ch,String op,String c,long a) {
            return provider.equals(p) && channel.equals(ch) && operator.equals(op) && currency.equals(c) && amount == a;
        }
    }
}
