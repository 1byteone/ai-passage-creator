package com.example.aipassagecreator.dataviz;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DatasetValidatorTest {

    private final DatasetValidator validator = new DatasetValidator();

    private Dataset csv(String raw) {
        return new DatasetParser().parse("csv", raw);
    }

    @Test
    void validate_trendData_timeAndMeasureDetected() {
        Dataset ds = csv("month,revenue\n2026-01,100\n2026-02,120\n2026-03,150\n");
        DataQualityReport r = validator.validate(ds);
        assertEquals("time", r.fields().get(0).semantic());
        assertEquals("measure", r.fields().get(1).semantic());
        assertTrue(r.fields().get(1).numeric());
        assertEquals(0, r.duplicateRows());
    }

    @Test
    void validate_categoryFieldDetected() {
        Dataset ds = csv("title,views\nA,100\nB,200\n");
        DataQualityReport r = validator.validate(ds);
        assertEquals("category", r.fields().get(0).semantic());
        assertEquals("measure", r.fields().get(1).semantic());
    }

    @Test
    void validate_missingCellsCountedAndWarned() {
        Dataset ds = csv("month,revenue\n2026-01,100\n,120\n");
        DataQualityReport r = validator.validate(ds);
        assertEquals(1, r.missingCells());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("缺失")));
    }

    @Test
    void validate_duplicateRowsCountedAndWarned() {
        Dataset ds = csv("a,b\n1,2\n1,2\n1,3\n");
        DataQualityReport r = validator.validate(ds);
        assertEquals(1, r.duplicateRows());
    }

    @Test
    void validate_noValidMeasureColumns_blocked() {
        Dataset ds = csv("name,city\n张三,北京\n李四,上海\n");
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> validator.validate(ds));
        assertTrue(ex.getMessage().contains("数值"));
    }

    @Test
    void validate_duplicateHeader_blocked() {
        // JSON 侧同名键去重，CSV 侧同名表头阻断
        assertThrows(DatasetParseException.class,
                () -> validator.validate(csv("a,a\n1,2\n")));
    }

    @Test
    void validate_nonNumericMeasureWarning() {
        Dataset ds = csv("month,revenue\n2026-01,abc\n2026-02,120\n");
        DataQualityReport r = validator.validate(ds);
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("revenue")));
    }
}
