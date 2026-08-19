package com.huarenzaimeng.api;

import com.huarenzaimeng.core.PricingCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PricingCalculatorTest {
    private final PricingCalculator calculator = new PricingCalculator();

    @Test void calculatesVersionableStepsWithoutExternalInput() {
        Instant now=Instant.parse("2026-08-18T00:00:00Z");
        PricingCalculator.PriceResult result=calculator.calculate(new PricingCalculator.PriceInput(
                new BigDecimal("100"),"BDT",new BigDecimal("0.06000000"),"FX-FIXTURE-001",
                now.plusSeconds(86400),now,List.of(
                new PricingCalculator.Adjustment("FX_BUFFER",new BigDecimal("0.020000"), PricingCalculator.Basis.CONVERTED_COST,"CFG-1"),
                new PricingCalculator.Adjustment("WECHAT_FEE",new BigDecimal("0.006000"), PricingCalculator.Basis.RUNNING_SUBTOTAL,"CFG-1"),
                new PricingCalculator.Adjustment("PLATFORM_MARKUP",new BigDecimal("0.100000"), PricingCalculator.Basis.RUNNING_SUBTOTAL,"CFG-1")),
                BigDecimal.ZERO,"PLATFORM",new BigDecimal("0.100000"),new BigDecimal("0.10"),"CEILING_0_10"));
        assertEquals(new BigDecimal("6.80"),result.finalAmountCny());
        assertEquals("FX-FIXTURE-001",result.steps().get(0).sourceRef());
        assertEquals("ROUNDING",result.steps().get(result.steps().size()-1).code());
    }
    @Test void rejectsExpiredFxBeforePricing() {
        Instant now=Instant.parse("2026-08-18T00:00:00Z");
        IllegalArgumentException error=assertThrows(IllegalArgumentException.class,()->calculator.calculate(new PricingCalculator.PriceInput(
                BigDecimal.ONE,"BDT",BigDecimal.ONE,"FX-OLD",now.minusSeconds(1),now,List.of(),BigDecimal.ZERO,"NONE",
                BigDecimal.ZERO,new BigDecimal("0.01"),"CEILING_0_01")));
        assertEquals("FX_SNAPSHOT_EXPIRED",error.getMessage());
    }
    @Test void reportsPriceBelowConfiguredMinimumMargin() {
        Instant now=Instant.parse("2026-08-18T00:00:00Z");
        PricingCalculator.PriceResult result=calculator.calculate(new PricingCalculator.PriceInput(
                BigDecimal.TEN,"BDT",BigDecimal.ONE,"FX-1",now.plusSeconds(60),now,List.of(),BigDecimal.ZERO,"NONE",
                new BigDecimal("0.10"),new BigDecimal("0.01"),"CEILING_0_01"));
        assertFalse(result.minimumMarginSatisfied());
    }

    @Test void halfUpAndCeilingUseDifferentRealRoundingPolicies() {
        Instant now=Instant.parse("2026-08-18T00:00:00Z");
        PricingCalculator.PriceInput halfUp=new PricingCalculator.PriceInput(new BigDecimal("1.04"),"CNY",BigDecimal.ONE,
                "FX-CNY-001",now.plusSeconds(60),now,List.of(),BigDecimal.ZERO,"NONE",BigDecimal.ZERO,
                new BigDecimal("0.10"),"HALF_UP_0_10");
        PricingCalculator.PriceInput ceiling=new PricingCalculator.PriceInput(new BigDecimal("1.04"),"CNY",BigDecimal.ONE,
                "FX-CNY-001",now.plusSeconds(60),now,List.of(),BigDecimal.ZERO,"NONE",BigDecimal.ZERO,
                new BigDecimal("0.10"),"CEILING_0_10");
        assertEquals(new BigDecimal("1.00"),calculator.calculate(halfUp).finalAmountCny());
        assertEquals(new BigDecimal("1.10"),calculator.calculate(ceiling).finalAmountCny());
    }

    @Test void rejectsRuleIncrementMismatchAndPromotionBeyondPrice() {
        Instant now=Instant.parse("2026-08-18T00:00:00Z");
        assertThrows(IllegalArgumentException.class,()->calculator.calculate(new PricingCalculator.PriceInput(
                BigDecimal.ONE,"CNY",BigDecimal.ONE,"FX-1",now.plusSeconds(60),now,List.of(),BigDecimal.ZERO,"NONE",
                BigDecimal.ZERO,new BigDecimal("0.10"),"HALF_UP_0_01")));
        assertThrows(IllegalArgumentException.class,()->calculator.calculate(new PricingCalculator.PriceInput(
                BigDecimal.ONE,"CNY",BigDecimal.ONE,"FX-1",now.plusSeconds(60),now,List.of(),new BigDecimal("2"),"PLATFORM",
                BigDecimal.ZERO,new BigDecimal("0.01"),"CEILING_0_01")));
    }

    @Test void rejectsUncontrolledAdjustmentMetadataAndPromotionBearer() {
        Instant now=Instant.parse("2026-08-18T00:00:00Z");
        assertEquals("ADJUSTMENT_CODE_INVALID",assertThrows(IllegalArgumentException.class,()->new PricingCalculator.Adjustment(
                "FREE_FORM",BigDecimal.ZERO,PricingCalculator.Basis.CONVERTED_COST,"CFG-1")).getMessage());
        assertEquals("ADJUSTMENT_SOURCE_REF_INVALID",assertThrows(IllegalArgumentException.class,()->new PricingCalculator.Adjustment(
                "FX_BUFFER",BigDecimal.ZERO,PricingCalculator.Basis.CONVERTED_COST," ")).getMessage());
        assertEquals("PROMOTION_BEARER_INVALID",assertThrows(IllegalArgumentException.class,()->calculator.calculate(new PricingCalculator.PriceInput(
                BigDecimal.ONE,"CNY",BigDecimal.ONE,"FX-1",now.plusSeconds(60),now,List.of(),BigDecimal.ZERO,"ARBITRARY",
                BigDecimal.ZERO,new BigDecimal("0.01"),"CEILING_0_01"))).getMessage());
    }
}
