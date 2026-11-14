package com.extantape.onevoid;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CommandGuardTest {
    @Test
    void catchesEveryFormOfOb() {
        assertTrue(CommandGuard.isOneBlock("/ob"));
        assertTrue(CommandGuard.isOneBlock("/ob join"));
        assertTrue(CommandGuard.isOneBlock("/OB visit someone"));
        assertTrue(CommandGuard.isOneBlock("/oneblock:oneblock j"));
        assertFalse(CommandGuard.isOneBlock("/obey"));
        assertFalse(CommandGuard.isOneBlock("/onevoid"));
    }
}
