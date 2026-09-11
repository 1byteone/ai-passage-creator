package com.example.aipassagecreator.dataviz;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsServiceTest {

    private final DatasetParser parser = new DatasetParser();
    private final DatasetValidator validator = new DatasetValidator();
    private final StatsService stats = new StatsService();

    @Test
    void computeFieldStats_growthRateCalculated() {
        Dataset ds = parser.parse("csv",
                "month,revenue\n2026-01,100\n2026-02,150\n2026-03,120\n");
        DataQualityReport p = validator.validate(ds);
        DatasetStats s = stats.computeFieldStats(ds, p, "revenue");
        assertEquals(100.0, s.min());
        assertEquals(150.0, s.max());
        assertEquals(370.0, s.sum());
        assertEquals(123.33333333333333, s.avg(), 1e-9);
        assertEquals(100.0, s.first());
        assertEquals(120.0, s.last());
        assertEquals(0.2, s.changeRate(), 1e-9);
        assertEquals(3, s.validCount());
    }

    @Test
    void computeFieldStats_illegalValueCountsAsZero() {
        Dataset ds = parser.parse("csv", "month,revenue\n2026-01,abc\n2026-02,100\n");
        DataQualityReport p = validator.validate(ds);
        DatasetStats s = stats.computeFieldStats(ds, p, "revenue");
        assertEquals(100.0, s.sum());
        assertEquals(0.0, s.first());
    }

    @Test
    void rankBy_descendingOrder() {
        Dataset ds = parser.parse("json",
                "[{\"t\":\"A\",\"v\":50},{\"t\":\"C\",\"v\":200},{\"t\":\"B\",\"v\":100}]");
        DataQualityReport p = validator.validate(ds);
        List<Map<String, String>> ranked = stats.rankBy(ds, p, "t", "v", 10);
        assertEquals("C", ranked.get(0).get("t"));
        assertEquals("B", ranked.get(1).get("t"));
        assertEquals("A", ranked.get(2).get("t"));
    }

    @Test
    void rankBy_topNLimits() {
        Dataset ds = parser.parse("json",
                "[{\"t\":\"A\",\"v\":1},{\"t\":\"B\",\"v\":2},{\"t\":\"C\",\"v\":3}]");
        DataQualityReport p = validator.validate(ds);
        assertEquals(2, stats.rankBy(ds, p, "t", "v", 2).size());
    }
}
