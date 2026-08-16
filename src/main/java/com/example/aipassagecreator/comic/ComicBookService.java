package com.example.aipassagecreator.comic;

import com.example.aipassagecreator.mapper.ComicBookMapper;
import com.example.aipassagecreator.model.po.ComicBookPo;
import org.springframework.stereotype.Service;

import com.mybatisflex.core.query.QueryWrapper;

/** 漫画手帐档案查询/创建 */
@Service
public class ComicBookService {

    private final ComicBookMapper bookMapper;

    public ComicBookService(ComicBookMapper bookMapper) {
        this.bookMapper = bookMapper;
    }

    public ComicBookPo getOrCreateBook(Long userId, String bookName) {
        ComicBookPo book = bookMapper.selectOneByQuery(
                QueryWrapper.create().eq("user_id", userId).eq("book_name", bookName).eq("is_delete", 0));
        if (book != null) return book;
        book = new ComicBookPo();
        book.setUserId(userId);
        book.setBookName(bookName);
        book.setDefaultStyle("powder");
        book.setIsDelete(0);
        bookMapper.insert(book);
        return book;
    }

    public ComicBookPo getBookById(Long bookId) {
        return bookMapper.selectOneById(bookId);
    }

    public java.util.List<ComicBookPo> listBooks(Long userId) {
        return bookMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId).eq("is_delete", 0)
                        .orderBy("create_time", false));
    }
}
