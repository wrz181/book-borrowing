package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.dto.HotBookVO;
import com.example.bookborrowing.entity.Book;
import com.example.bookborrowing.mapper.BookMapper;
import com.example.bookborrowing.service.HotBookService;
import com.example.bookborrowing.service.support.RedisSupportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 热门图书排行实现：
 * <ul>
 *   <li>计数：ZSet key = bookborrowing:rank:hot，成员为图书ID；浏览 +1，借阅申请 +10（借阅行为权重更高）；</li>
 *   <li>读取：ZREVRANGE 取前 N 名，再回表补充图书名称、作者、分类等信息；</li>
 *   <li>降级：Redis 关闭或不可用时，改用数据库统计每本图书的借阅记录数取前 N 名，接口返回结构不变，
 *       并通过 source 字段标注统计来源，便于演示与排查。</li>
 * </ul>
 */
@Service
public class HotBookServiceImpl implements HotBookService {

    private static final Logger log = LoggerFactory.getLogger(HotBookServiceImpl.class);

    /** 热度排行 ZSet key（成员：图书ID字符串） */
    private static final String HOT_RANK_KEY = "bookborrowing:rank:hot";

    /** 浏览一次加 1 分 */
    private static final double SCORE_VIEW = 1.0;

    /** 提交借阅申请一次加 10 分（借阅意愿比浏览更能代表热度） */
    private static final double SCORE_BORROW = 10.0;

    /** 统计来源标识：Redis 实时排行 */
    private static final String SOURCE_REDIS = "redis";

    /** 统计来源标识：数据库降级统计 */
    private static final String SOURCE_DB = "db";

    private final RedisSupportService redisSupport;

    private final BookMapper bookMapper;

    public HotBookServiceImpl(RedisSupportService redisSupport, BookMapper bookMapper) {
        this.redisSupport = redisSupport;
        this.bookMapper = bookMapper;
    }

    @Override
    public void recordView(Long bookId) {
        redisSupport.incrScore(HOT_RANK_KEY, String.valueOf(bookId), SCORE_VIEW);
    }

    @Override
    public void recordBorrow(Long bookId) {
        redisSupport.incrScore(HOT_RANK_KEY, String.valueOf(bookId), SCORE_BORROW);
    }

    @Override
    public List<HotBookVO> top(int top) {
        int limit = Math.min(Math.max(top, 1), 50);
        Map<String, Double> scores = redisSupport.topScores(HOT_RANK_KEY, limit);
        if (!scores.isEmpty()) {
            List<HotBookVO> result = buildFromRedis(scores);
            if (!result.isEmpty()) {
                return result;
            }
        }
        // Redis 无数据（不可用/刚清库）时降级：按借阅记录数统计
        log.info("热门排行使用数据库降级统计（Redis 无数据），取前 {} 名", limit);
        return buildFromDb(limit);
    }

    /**
     * 按 Redis 排行组装 VO：保持 ZSet 返回的名次顺序，回表补全图书信息。
     *
     * @param scores 有序 Map：图书ID → 热度分
     * @return 排行列表
     */
    private List<HotBookVO> buildFromRedis(Map<String, Double> scores) {
        List<Long> ids = scores.keySet().stream().map(Long::valueOf).toList();
        List<Book> books = bookMapper.selectByIds(ids);
        if (books.isEmpty()) {
            return List.of();
        }
        Map<Long, Book> bookMap = books.stream()
                .collect(java.util.stream.Collectors.toMap(Book::getId, b -> b, (a, b) -> a, java.util.LinkedHashMap::new));
        List<HotBookVO> result = new ArrayList<>(scores.size());
        int rank = 1;
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            Book book = bookMap.get(Long.valueOf(entry.getKey()));
            if (book == null) {
                // 图书已删除，排行中跳过
                continue;
            }
            result.add(buildVO(book, entry.getValue(), rank++, SOURCE_REDIS));
        }
        return result;
    }

    /**
     * 数据库降级统计：按未删除借阅记录数倒序取前 N 名。
     *
     * @param limit 名次数
     * @return 排行列表
     */
    private List<HotBookVO> buildFromDb(int limit) {
        List<Book> books = bookMapper.selectTopBorrowed(limit);
        List<HotBookVO> result = new ArrayList<>(books.size());
        int rank = 1;
        for (Book book : books) {
            result.add(buildVO(book, null, rank++, SOURCE_DB));
        }
        return result;
    }

    /**
     * 组装单个排行条目。
     *
     * @param book     图书实体（含分类名称）
     * @param hotScore 热度分（数据库降级时为 null）
     * @param rank     名次
     * @param source   统计来源
     * @return 排行条目
     */
    private HotBookVO buildVO(Book book, Double hotScore, int rank, String source) {
        HotBookVO vo = new HotBookVO();
        vo.setBookId(book.getId());
        vo.setBookName(book.getBookName());
        vo.setAuthor(book.getAuthor());
        vo.setCategoryName(book.getCategoryName());
        vo.setHotScore(hotScore);
        vo.setRank(rank);
        vo.setSource(source);
        return vo;
    }
}
