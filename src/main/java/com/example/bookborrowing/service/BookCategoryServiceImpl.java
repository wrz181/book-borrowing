package com.example.bookborrowing.service;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.entity.BookCategory;
import com.example.bookborrowing.mapper.BookCategoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图书分类业务实现。
 */
@Service
public class BookCategoryServiceImpl implements BookCategoryService {

    private static final Logger log = LoggerFactory.getLogger(BookCategoryServiceImpl.class);

    /**
     * 排序方式白名单：key=前端传入的 sortBy，value=SQL 排序子句。
     * 仅允许这几个固定值进入 SQL，防止排序字段注入。
     */
    private static final Map<String, String> SORT_CLAUSE = new LinkedHashMap<>();

    static {
        // 默认：按创建顺序，新增分类自然排在最后
        SORT_CLAUSE.put("default", "id ASC");
        SORT_CLAUSE.put("name", "category_name ASC");
        SORT_CLAUSE.put("updateTime", "update_time DESC");
    }

    private final BookCategoryMapper bookCategoryMapper;

    public BookCategoryServiceImpl(BookCategoryMapper bookCategoryMapper) {
        this.bookCategoryMapper = bookCategoryMapper;
    }

    @Override
    public List<BookCategory> listAll(String sortBy) {
        String orderBy = SORT_CLAUSE.getOrDefault(sortBy == null ? "default" : sortBy, SORT_CLAUSE.get("default"));
        List<BookCategory> list = bookCategoryMapper.selectAll(orderBy);
        log.info("查询图书分类列表：排序={}，共 {} 条", sortBy, list.size());
        return list;
    }

    @Override
    public void create(BookCategory category) {
        bookCategoryMapper.insert(category);
        log.info("新增图书分类成功：id={}，分类名={}", category.getId(), category.getCategoryName());
    }

    @Override
    public void update(BookCategory category) {
        BookCategory exist = bookCategoryMapper.selectById(category.getId());
        if (exist == null) {
            log.warn("修改图书分类失败：分类不存在，id={}", category.getId());
            throw new BusinessException("图书分类不存在");
        }
        bookCategoryMapper.updateById(category);
        log.info("修改图书分类成功：id={}，分类名={}", category.getId(), category.getCategoryName());
    }

    @Override
    public void remove(Long id) {
        BookCategory exist = bookCategoryMapper.selectById(id);
        if (exist == null) {
            log.warn("删除图书分类失败：分类不存在，id={}", id);
            throw new BusinessException("图书分类不存在");
        }
        // 关联删除限制：分类下存在图书时不允许删除
        if (bookCategoryMapper.countBookByCategoryId(id) > 0) {
            log.warn("删除图书分类失败：分类下存在图书，id={}，分类名={}", id, exist.getCategoryName());
            throw new BusinessException("该分类下存在图书，不能删除");
        }
        bookCategoryMapper.deleteById(id);
        log.info("删除图书分类成功：id={}，分类名={}", id, exist.getCategoryName());
    }
}
