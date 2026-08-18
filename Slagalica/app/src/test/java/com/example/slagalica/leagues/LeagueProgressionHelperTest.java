package com.example.slagalica.leagues;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LeagueProgressionHelperTest {

    @Test
    public void resolvesEveryThresholdBoundary() {
        int[] stars = {-1, 0, 99, 100, 199, 200, 399, 400, 799, 800, 1599, 1600};
        int[] levels = {0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5};

        for (int index = 0; index < stars.length; index++) {
            assertEquals(levels[index], LeagueProgressionHelper.resolveLeagueLevel(stars[index]));
        }
    }

    @Test
    public void dailyGrantIncludesCurrentLeagueLevel() {
        assertEquals(5, LeagueProgressionHelper.dailyTokenGrant(0));
        assertEquals(8, LeagueProgressionHelper.dailyTokenGrant(400));
        assertEquals(10, LeagueProgressionHelper.dailyTokenGrant(1600));
    }

    @Test
    public void detectsPromotionAndDemotion() {
        LeagueChange promotion = LeagueProgressionHelper.detectChange(95, 104);
        assertNotNull(promotion);
        assertEquals(0, promotion.fromLevel);
        assertEquals(1, promotion.toLevel);
        assertEquals("up", promotion.direction);

        LeagueChange demotion = LeagueProgressionHelper.detectChange(104, 95);
        assertNotNull(demotion);
        assertEquals(1, demotion.fromLevel);
        assertEquals(0, demotion.toLevel);
        assertEquals("down", demotion.direction);

        assertNull(LeagueProgressionHelper.detectChange(120, 150));
    }
}
