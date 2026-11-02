package com.example.bookborrowing.service;

import java.util.List;
import java.util.Map;

/**
 * HDFS 分布式文件服务：借阅记录归档备份、归档文件列表与内容预览。
 * 仅在 app.hdfs.enabled=true 时由 Spring 装配实现类。
 */
public interface HdfsService {

    /**
     * 将全部借阅记录导出为 CSV 归档文件并写入 HDFS。
     *
     * @return 归档结果描述（文件路径、大小、记录条数）
     */
    Map<String, Object> archiveBorrowRecords();

    /**
     * 列出 HDFS 归档目录下的全部文件。
     *
     * @return 文件列表（名称、大小、修改时间）
     */
    List<Map<String, Object>> listArchiveFiles();

    /**
     * 预览归档文件前 N 行内容。
     *
     * @param fileName 文件名（不含路径，防止目录穿越）
     * @param maxLines 最多读取行数
     * @return 文本内容
     */
    String previewArchiveFile(String fileName, int maxLines);
}
