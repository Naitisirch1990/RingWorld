package dev.ringworld.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class RingLandmarkPlanTest {
    @Test
    void planHasExactlyFifteenMegaLandmarks() {
        assertEquals(15, RingLandmarkPlan.overworld(1234L).size());
    }

    @Test
    void pyramidCityIsUnique() {
        long count = RingLandmarkPlan.overworld(987654321L).stream()
                .filter(landmark -> landmark.type() == RingLandmarkPlan.LandmarkType.PYRAMID_CITY)
                .count();
        assertEquals(1L, count);
    }

    @Test
    void startCityRemainsAtCanonicalZero() {
        RingLandmarkPlan.Landmark start = RingLandmarkPlan.overworld(42L).get(0);
        assertEquals(RingLandmarkPlan.LandmarkType.GRAND_START_CITY, start.type());
        assertEquals(0, start.canonicalX());
    }

    @Test
    void jitterStaysInsideConfiguredRadius() {
        List<RingLandmarkPlan.Landmark> plan = RingLandmarkPlan.overworld(112233L);
        int circumference = RingWorldLayouts.OVERWORLD.circumferenceBlocks();
        int spacing = RingWorldLayouts.MEGA_POI_SPACING_BLOCKS;
        int jitter = RingWorldLayouts.MEGA_POI_JITTER_BLOCKS;

        for (int i = 1; i < plan.size(); i++) {
            int base = i * spacing;
            int actual = plan.get(i).canonicalX();
            int delta = actual - base;
            if (delta > circumference / 2) delta -= circumference;
            if (delta < -circumference / 2) delta += circumference;
            assertTrue(Math.abs(delta) <= jitter,
                    "slot " + i + " exceeds jitter radius: " + delta);
        }
    }

    @Test
    void sameSeedProducesSamePlan() {
        assertEquals(RingLandmarkPlan.overworld(777L), RingLandmarkPlan.overworld(777L));
    }
}
