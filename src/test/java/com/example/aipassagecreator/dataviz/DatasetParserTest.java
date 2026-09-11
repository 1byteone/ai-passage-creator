package com.example.aipassagecreator.dataviz;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DatasetParserTest {

    private final DatasetParser parser = new DatasetParser();

    @Test
    void parse_csv_basic_headersAndRows() {
        Dataset ds = parser.parse("csv", "month,revenue\n2026-01,100\n2026-02,120\n");
        assertEquals(List.of("month", "revenue"), ds.headers());
        assertEquals(2, ds.rows().size());
        assertEquals("2026-01", ds.rows().get(0).get("month"));
        assertEquals("120", ds.rows().get(1).get("revenue"));
    }

    @Test
    void parse_json_arrayOfObjects() {
        String json = "[{\"title\":\"A\",\"views\":100},{\"title\":\"B\",\"views\":200}]";
        Dataset ds = parser.parse("json", json);
        assertEquals(List.of("title", "views"), ds.headers());
        assertEquals(2, ds.rows().size());
        assertEquals("200", ds.rows().get(1).get("views"));
    }

    @Test
    void parse_csv_quotedValueWithComma() {
        Dataset ds = parser.parse("csv", "title,tags\n\"A,B\",\"x\"\n");
        assertEquals("A,B", ds.rows().get(0).get("title"));
    }

    @Test
    void parse_csv_rowShorterThanHeader_padWithEmpty() {
        Dataset ds = parser.parse("csv", "a,b,c\n1,2\n");
        assertEquals("", ds.rows().get(0).get("c"));
    }

    @Test
    void parse_csv_tooManyRows_throws() {
        StringBuilder sb = new StringBuilder("a\n");
        for (int i = 0; i < 1001; i++) sb.append(i).append('\n');
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> parser.parse("csv", sb.toString()));
        assertTrue(ex.getMessage().contains("行数"));
    }

    @Test
    void parse_empty_throws() {
        assertThrows(DatasetParseException.class, () -> parser.parse("csv", "  "));
        assertThrows(DatasetParseException.class, () -> parser.parse("json", "not json"));
    }

    @Test
    void parse_unknownFormat_throws() {
        assertThrows(DatasetParseException.class, () -> parser.parse("xml", "<a/>"));
    }

    @Test
    void parse_json_notArrayOfObjects_throws() {
        assertThrows(DatasetParseException.class, () -> parser.parse("json", "{\"a\":1}"));
    }

    @Test
    void parse_json_literalNull_throws() {
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> parser.parse("json", "null"));
        assertTrue(ex.getMessage().contains("JSON 格式不正确，需为对象数组"));
    }

    @Test
    void parse_json_tooManyColumns_throws() {
        StringBuilder sb = new StringBuilder("[{");
        for (int i = 0; i < 51; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"col").append(i).append("\":1");
        }
        sb.append("}]");
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> parser.parse("json", sb.toString()));
        assertTrue(ex.getMessage().contains("列数"));
    }

    @Test
    void parse_csv_tooManyColumns_throws() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 51; i++) {
            if (i > 0) sb.append(",");
            sb.append("col").append(i);
        }
        sb.append("\n");
        for (int i = 0; i < 51; i++) {
            if (i > 0) sb.append(",");
            sb.append("v").append(i);
        }
        sb.append("\n");
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> parser.parse("csv", sb.toString()));
        assertTrue(ex.getMessage().contains("列数"));
    }

    @Test
    void parse_cellOver200Chars_throws() {
        String longVal = "a".repeat(201);
        DatasetParseException exJson = assertThrows(DatasetParseException.class,
                () -> parser.parse("json", "[{\"k\":\"" + longVal + "\"}]"));
        assertTrue(exJson.getMessage().contains("单元格超过 200 字符上限"));

        DatasetParseException exCsv = assertThrows(DatasetParseException.class,
                () -> parser.parse("csv", "k\n" + longVal + "\n"));
        assertTrue(exCsv.getMessage().contains("单元格超过 200 字符上限"));
    }

    @Test
    void parse_json_heterogeneousRows_mergesHeaders() {
        String json = "[{\"a\":1},{\"b\":2,\"c\":3}]";
        Dataset ds = parser.parse("json", json);
        assertEquals(List.of("a", "b", "c"), ds.headers());
        assertEquals(2, ds.rows().size());
    }

    @Test
    void dataset_innerMap_isImmutable() {
        Dataset ds = parser.parse("csv", "a,b\n1,2\n");
        assertThrows(UnsupportedOperationException.class, () -> ds.rows().get(0).put("a", "999"));
        assertThrows(UnsupportedOperationException.class, () -> ds.rows().add(java.util.Map.of("a", "3")));
        assertThrows(UnsupportedOperationException.class, () -> ds.headers().add("c"));
    }
}
