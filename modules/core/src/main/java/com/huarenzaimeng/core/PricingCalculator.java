package com.huarenzaimeng.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure V1 pricing kernel. It never reads a network, clock or database. */
public final class PricingCalculator {
    private static final int SCALE = 8;
    private static final Set<String> ADJUSTMENT_CODES=Set.of("FX_BUFFER","WECHAT_FEE","TAX","PLATFORM_MARKUP");
    private static final Set<String> PROMOTION_BEARERS=Set.of("PLATFORM","PROVIDER","SHARED","NONE");
    private static final Pattern CONTROLLED_REF=Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}");
    private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999999.9999");
    private static final BigDecimal MAX_FX_RATE = new BigDecimal("1000000");
    private static final java.util.Map<String, RoundingPolicy> ROUNDING_POLICIES = java.util.Map.of(
            "CEILING_0_01", new RoundingPolicy(new BigDecimal("0.01"), RoundingMode.CEILING),
            "CEILING_0_10", new RoundingPolicy(new BigDecimal("0.10"), RoundingMode.CEILING),
            "HALF_UP_0_01", new RoundingPolicy(new BigDecimal("0.01"), RoundingMode.HALF_UP),
            "HALF_UP_0_10", new RoundingPolicy(new BigDecimal("0.10"), RoundingMode.HALF_UP));

    public PriceResult calculate(PriceInput input) {
        Objects.requireNonNull(input, "input");
        requirePositive(input.supplierCost(), "supplierCost");
        requirePositive(input.fxRateToCny(), "fxRateToCny");
        requirePositive(input.roundingIncrementCny(), "roundingIncrementCny");
        if (input.supplierCost().compareTo(MAX_MONEY) > 0) throw new IllegalArgumentException("SUPPLIER_COST_TOO_LARGE");
        if (input.fxRateToCny().compareTo(MAX_FX_RATE) > 0) throw new IllegalArgumentException("FX_RATE_TOO_LARGE");
        if (!input.settlementCurrency().matches("[A-Z]{3}")) throw new IllegalArgumentException("SETTLEMENT_CURRENCY_INVALID");
        if (input.adjustments().size() > 16) throw new IllegalArgumentException("TOO_MANY_ADJUSTMENTS");
        requireRate(input.minimumMarginRate(), "minimumMargin");
        RoundingPolicy policy = ROUNDING_POLICIES.get(input.roundingRuleRef());
        if (policy == null || policy.increment().compareTo(input.roundingIncrementCny()) != 0)
            throw new IllegalArgumentException("ROUNDING_RULE_INVALID");
        if (!input.fxValidUntil().isAfter(input.quotedAt())) throw new IllegalArgumentException("FX_SNAPSHOT_EXPIRED");

        BigDecimal converted = input.supplierCost().multiply(input.fxRateToCny()).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal running = converted;
        java.util.ArrayList<PriceStep> steps = new java.util.ArrayList<>();
        steps.add(new PriceStep("SUPPLIER_COST_CONVERTED", input.supplierCost(), converted, input.fxSnapshotRef()));
        for (Adjustment adjustment : input.adjustments()) {
            requireRate(adjustment.rate(), adjustment.code());
            BigDecimal basis = adjustment.basis() == Basis.CONVERTED_COST ? converted : running;
            BigDecimal amount = basis.multiply(adjustment.rate()).setScale(SCALE, RoundingMode.HALF_UP);
            running = running.add(amount);
            steps.add(new PriceStep(adjustment.code(), basis, amount, adjustment.sourceRef()));
        }
        if (input.promotionAmountCny().signum() < 0) throw new IllegalArgumentException("PROMOTION_AMOUNT_NEGATIVE");
        if (input.promotionAmountCny().compareTo(running) > 0) throw new IllegalArgumentException("PROMOTION_EXCEEDS_PRICE");
        running = running.subtract(input.promotionAmountCny());
        if (input.promotionAmountCny().signum() > 0) {
            steps.add(new PriceStep("PROMOTION", running.add(input.promotionAmountCny()), input.promotionAmountCny().negate(), input.promotionBearer()));
        }
        BigDecimal margin = running.subtract(converted);
        BigDecimal marginRate = margin.divide(converted, SCALE, RoundingMode.HALF_UP);
        boolean minimumMarginSatisfied = marginRate.compareTo(input.minimumMarginRate()) >= 0;

        BigDecimal rounded = round(running, policy).setScale(2, RoundingMode.UNNECESSARY);
        steps.add(new PriceStep("ROUNDING", running, rounded.subtract(running), input.roundingRuleRef()));
        BigDecimal finalMargin = rounded.subtract(converted);
        BigDecimal finalMarginRate=finalMargin.divide(converted, SCALE, RoundingMode.HALF_UP);
        return new PriceResult(rounded, converted, finalMargin,finalMarginRate,
                minimumMarginSatisfied&&finalMarginRate.compareTo(input.minimumMarginRate())>=0,List.copyOf(steps));
    }

    private static BigDecimal round(BigDecimal value, RoundingPolicy policy) {
        return value.divide(policy.increment(), 0, policy.mode()).multiply(policy.increment());
    }
    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(field + "_MUST_BE_POSITIVE");
    }
    private static void requireRate(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) >= 0)
            throw new IllegalArgumentException(field + "_RATE_INVALID");
    }

    public enum Basis { CONVERTED_COST, RUNNING_SUBTOTAL }
    private record RoundingPolicy(BigDecimal increment, RoundingMode mode) {}
    public record Adjustment(String code, BigDecimal rate, Basis basis, String sourceRef) {
        public Adjustment {
            Objects.requireNonNull(code); Objects.requireNonNull(basis); Objects.requireNonNull(sourceRef);
            if(!ADJUSTMENT_CODES.contains(code))throw new IllegalArgumentException("ADJUSTMENT_CODE_INVALID");
            if(sourceRef.isBlank()||!CONTROLLED_REF.matcher(sourceRef).matches())throw new IllegalArgumentException("ADJUSTMENT_SOURCE_REF_INVALID");
        }
    }
    public record PriceInput(BigDecimal supplierCost, String settlementCurrency, BigDecimal fxRateToCny,
                             String fxSnapshotRef, Instant fxValidUntil, Instant quotedAt,
                             List<Adjustment> adjustments, BigDecimal promotionAmountCny,
                             String promotionBearer, BigDecimal minimumMarginRate,
                             BigDecimal roundingIncrementCny, String roundingRuleRef) {
        public PriceInput {
            Objects.requireNonNull(settlementCurrency); Objects.requireNonNull(fxSnapshotRef);
            Objects.requireNonNull(fxValidUntil); Objects.requireNonNull(quotedAt);
            adjustments = List.copyOf(adjustments); Objects.requireNonNull(promotionAmountCny);
            Objects.requireNonNull(promotionBearer); Objects.requireNonNull(minimumMarginRate);
            Objects.requireNonNull(roundingRuleRef);
            if (fxSnapshotRef.isBlank() || fxSnapshotRef.length() > 96) throw new IllegalArgumentException("FX_SNAPSHOT_REF_INVALID");
            if(!PROMOTION_BEARERS.contains(promotionBearer))throw new IllegalArgumentException("PROMOTION_BEARER_INVALID");
        }
    }
    public record PriceStep(String code, BigDecimal basisAmount, BigDecimal resultAmount, String sourceRef) {}
    public record PriceResult(BigDecimal finalAmountCny, BigDecimal convertedCostCny,
                              BigDecimal marginAmountCny, BigDecimal marginRate, boolean minimumMarginSatisfied,
                              List<PriceStep> steps) {}
}
