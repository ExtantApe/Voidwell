package com.extantape.onevoid.meter;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MeterTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 8);

    @Test
    void gearIsWorthItsMaterial() {
        assertEquals(56, ValueTable.base("DIAMOND_CHESTPLATE", "COMMON"), 1e-9);
        assertEquals(14 + 40, ValueTable.base("NETHERITE_SWORD", "EPIC"), 1e-9);
        assertEquals(4, ValueTable.base("IRON_SWORD", "COMMON"), 1e-9);
        assertEquals(0.2, ValueTable.base("WOODEN_SWORD", "COMMON"), 1e-9);
        assertTrue(ValueTable.isGear("DIAMOND_HELMET"));
        assertTrue(ValueTable.isGear("TRIDENT"));
        assertFalse(ValueTable.isGear("COBBLESTONE"));
    }

    @Test
    void commonBlocksAreNearlyWorthless() {
        assertEquals(0.02, ValueTable.base("COBBLESTONE", "COMMON"), 1e-9);
        Meter m = new Meter();
        Meter.Charge c = m.charge("COBBLESTONE", 0.02, 64, DAY, 100, 200, 0.9);
        assertTrue(c.added() < 0.25, "a stack of cobblestone barely moves the meter: " + c.added());
    }

    @Test
    void repeatsAreWorthLess() {
        Meter m = new Meter();
        double first = m.charge("DIAMOND_SWORD", 14, 1, DAY, 100, 200, 0.9).added();
        double second = m.charge("DIAMOND_SWORD", 14, 1, DAY, 100, 200, 0.9).added();
        assertEquals(14, first, 1e-9);
        assertEquals(12.6, second, 1e-9);
        assertEquals(6.513, ValueTable.repeated(1, 10, 0, 0.9), 1e-3);
    }

    @Test
    void dailyCapStopsCharging() {
        Meter m = new Meter();
        m.charge("NETHERITE_BLOCK", 360, 1, DAY, 100, 50, 0.9);
        Meter.Charge c = m.charge("ELYTRA", 60, 1, DAY, 100, 50, 0.9);
        assertEquals(50, m.points, 1e-9);
        assertEquals(0, c.added(), 1e-9);
        assertTrue(c.capped());
        Meter.Charge tomorrow = m.charge("ELYTRA", 60, 1, DAY.plusDays(1), 100, 50, 0.9);
        assertEquals(50, tomorrow.added(), 1e-9);
    }

    @Test
    void meterHoldsTwiceFullAndCarriesOver() {
        Meter m = new Meter();
        Meter.Charge c = m.charge("NETHERITE_BLOCK", 360, 1, DAY, 100, 1000, 0.9);
        assertTrue(c.becameFull());
        assertEquals(200, m.points, 1e-9);
        m.spend(100);
        assertEquals(100, m.points, 1e-9);
        assertTrue(m.isFull(100));
    }

    @Test
    void wearAndEnchantments() {
        assertEquals(0.5, ValueTable.durabilityFactor(50, 100, 0.25), 1e-9);
        assertEquals(0.25, ValueTable.durabilityFactor(99, 100, 0.25), 1e-9);
        assertEquals(10, ValueTable.enchantment(5, false, false), 1e-9);
        assertEquals(4, ValueTable.enchantment(1, true, false), 1e-9);
        assertEquals(0, ValueTable.enchantment(1, false, true), 1e-9);
    }
}
