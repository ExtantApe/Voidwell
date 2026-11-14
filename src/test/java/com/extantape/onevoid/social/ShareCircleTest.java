package com.extantape.onevoid.social;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Kids first: travel and teams only connect two online players who share a circle; anything else is no. */
class ShareCircleTest {
    static final List<String> CIRCLES = List.of("mayor.circle.kids", "mayor.circle.adults");
    static final Set<String> KID = Set.of("mayor.circle.kids"), OTHER_KID = Set.of("mayor.circle.kids"), ADULT = Set.of("mayor.circle.adults");

    @Test void controlTwoOnlineKidsInOneCircleShareIt() {
        assertTrue(Social.shareCircle(CIRCLES, true, KID::contains, true, OTHER_KID::contains));
    }

    @Test void aKidAndAnAdultNever() {
        assertFalse(Social.shareCircle(CIRCLES, true, KID::contains, true, ADULT::contains));
    }

    @Test void noCirclesAtAllMeansNo() {
        assertFalse(Social.shareCircle(List.of(), true, n -> true, true, n -> true), "even players who'd match anything");
    }

    @Test void someoneOfflineMeansNo() {
        assertFalse(Social.shareCircle(CIRCLES, false, KID::contains, true, OTHER_KID::contains));
        assertFalse(Social.shareCircle(CIRCLES, true, KID::contains, false, OTHER_KID::contains));
    }

    @Test void nobodyInAnyCircleMeansNo() {
        assertFalse(Social.shareCircle(CIRCLES, true, n -> false, true, n -> false));
    }
}
