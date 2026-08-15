package com.example.aipassagecreator.comic;

import com.example.aipassagecreator.mapper.ComicBookMapper;
import com.example.aipassagecreator.model.po.ComicBookPo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ComicTableSchemaTest {

    @Autowired private DataSource dataSource;
    @Autowired private ComicBookMapper bookMapper;

    @Test
    void schema_containsComicTables() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "select count(*) from information_schema.tables where lower(table_name) in ('comic_book','comic_episode','comic_monthly_volume')")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt(1));
        }
    }

    @Test
    void bookPo_insertAndRead_back() {
        ComicBookPo po = new ComicBookPo();
        po.setUserId(1L);
        po.setBookName("我的生活手帐");
        po.setDefaultStyle("powder");
        bookMapper.insert(po);
        assertNotNull(po.getId());
        ComicBookPo loaded = bookMapper.selectOneById(po.getId());
        assertEquals("我的生活手帐", loaded.getBookName());
        assertEquals(1L, loaded.getUserId());
    }
}
