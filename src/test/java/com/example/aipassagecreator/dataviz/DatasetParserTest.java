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
}
