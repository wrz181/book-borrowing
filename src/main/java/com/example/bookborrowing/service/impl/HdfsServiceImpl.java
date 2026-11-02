package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.config.CloudProps;
import com.example.bookborrowing.entity.BorrowRecord;
import com.example.bookborrowing.mapper.BorrowRecordMapper;
import com.example.bookborrowing.service.HdfsService;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HDFS 分布式文件服务实现（app.hdfs.enabled=true 时装配）：
 * 把全部借阅记录导出为 CSV 归档文件写入 HDFS，用于演示分布式存储与数据备份。
 * <p>
 * FileSystem 采用懒加载：首次调用时连接 NameNode，连接失败抛出业务异常并在下次调用时重试，
 * 避免 Hadoop 集群未就绪拖垮应用启动。
 */
@Service
@ConditionalOnProperty(name = "app.hdfs.enabled", havingValue = "true")
public class HdfsServiceImpl implements HdfsService {

    private static final Logger log = LoggerFactory.getLogger(HdfsServiceImpl.class);

    /** CSV 归档目录（相对 app.hdfs.base-path） */
    private static final String ARCHIVE_DIR = "/archive";

    /** 归档文件名时间戳格式 */
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** 单次归档最多导出的记录条数（防护值，实际业务量远小于该值） */
    private static final int MAX_EXPORT_ROWS = 100_000;

    private final CloudProps cloudProps;

    private final BorrowRecordMapper borrowRecordMapper;

    /** 懒加载的 HDFS 文件系统句柄；连接失败时置 null 以便下次重试 */
    private volatile FileSystem fileSystem;

    public HdfsServiceImpl(CloudProps cloudProps, BorrowRecordMapper borrowRecordMapper) {
        this.cloudProps = cloudProps;
        this.borrowRecordMapper = borrowRecordMapper;
    }

    @Override
    public Map<String, Object> archiveBorrowRecords() {
        List<BorrowRecord> records = borrowRecordMapper.selectPage(0, MAX_EXPORT_ROWS, null, null);
        String fileName = "borrow_records_" + LocalDateTime.now().format(FILE_TS) + ".csv";
        String hdfsPath = archiveDirPath() + "/" + fileName;
        int lineCount;
        try {
            FileSystem fs = getFileSystem();
            Path dir = new Path(archiveDirPath());
            if (!fs.exists(dir)) {
                fs.mkdirs(dir);
            }
            try (FSDataOutputStream out = fs.create(new Path(hdfsPath), true);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
                writer.write("记录ID,图书名称,作者,分类,借阅人,学工号,借阅日期,应还日期,归还日期,状态,备注");
                writer.newLine();
                for (BorrowRecord record : records) {
                    writer.write(toCsvLine(record));
                    writer.newLine();
                }
            }
            lineCount = records.size();
            FileStatus status = fs.getFileStatus(new Path(hdfsPath));
            log.info("借阅记录归档完成：path={}，记录数={}，大小={} 字节", hdfsPath, lineCount, status.getLen());
            Map<String, Object> result = new LinkedHashMap<>(4);
            result.put("path", hdfsPath);
            result.put("records", lineCount);
            result.put("sizeBytes", status.getLen());
            return result;
        } catch (IOException e) {
            // 连接失败时释放句柄，下次调用重新连接
            fileSystem = null;
            log.error("HDFS 归档失败：{}", e.getMessage());
            throw new BusinessException("HDFS 归档失败：" + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> listArchiveFiles() {
        List<Map<String, Object>> files = new ArrayList<>();
        try {
            FileSystem fs = getFileSystem();
            Path dir = new Path(archiveDirPath());
            if (!fs.exists(dir)) {
                return files;
            }
            for (FileStatus status : fs.listStatus(dir)) {
                if (status.isDirectory()) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>(4);
                item.put("name", status.getPath().getName());
                item.put("sizeBytes", status.getLen());
                item.put("modifyTime", status.getModificationTime());
                files.add(item);
            }
            return files;
        } catch (IOException e) {
            fileSystem = null;
            log.error("HDFS 文件列表查询失败：{}", e.getMessage());
            throw new BusinessException("HDFS 文件列表查询失败：" + e.getMessage());
        }
    }

    @Override
    public String previewArchiveFile(String fileName, int maxLines) {
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("..")) {
            throw new BusinessException("非法文件名");
        }
        String hdfsPath = archiveDirPath() + "/" + fileName;
        try {
            FileSystem fs = getFileSystem();
            if (!fs.exists(new Path(hdfsPath))) {
                throw new BusinessException("归档文件不存在：" + fileName);
            }
            StringBuilder content = new StringBuilder();
            try (FSDataInputStream in = fs.open(new Path(hdfsPath));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                int count = 0;
                while (count < maxLines && (line = reader.readLine()) != null) {
                    if (count > 0) {
                        content.append('\n');
                    }
                    content.append(line);
                    count++;
                }
            }
            return content.toString();
        } catch (IOException e) {
            fileSystem = null;
            log.error("HDFS 文件预览失败：{}", e.getMessage());
            throw new BusinessException("HDFS 文件预览失败：" + e.getMessage());
        }
    }

    /**
     * 懒加载 HDFS 文件系统：首次调用时按配置连接 NameNode。
     *
     * @return FileSystem 句柄
     * @throws IOException 连接失败
     */
    private FileSystem getFileSystem() throws IOException {
        FileSystem fs = fileSystem;
        if (fs == null) {
            synchronized (this) {
                if (fileSystem == null) {
                    CloudProps.Hdfs hdfs = cloudProps.getHdfs();
                    Configuration conf = new Configuration();
                    conf.set("fs.defaultFS", hdfs.getNameNode());
                    conf.set("dfs.client.use.datanode.hostname", "true");
                    try {
                        fileSystem = FileSystem.get(URI.create(hdfs.getNameNode()), conf, hdfs.getUser());
                    } catch (InterruptedException e) {
                        // 恢复中断标记并统一转为 IOException，调用方按连接失败处理
                        Thread.currentThread().interrupt();
                        throw new IOException("HDFS 连接被中断", e);
                    }
                    log.info("HDFS 连接成功：{}（用户 {}）", hdfs.getNameNode(), hdfs.getUser());
                }
                fs = fileSystem;
            }
        }
        return fs;
    }

    /**
     * 归档目录完整路径。
     *
     * @return 形如 /bookborrowing/archive
     */
    private String archiveDirPath() {
        String base = cloudProps.getHdfs().getBasePath();
        return (base == null ? "/bookborrowing" : base) + ARCHIVE_DIR;
    }

    /**
     * 借阅记录实体转 CSV 行：包含逗号/引号/换行的字段自动加引号包裹。
     *
     * @param record 借阅记录
     * @return CSV 行
     */
    private String toCsvLine(BorrowRecord record) {
        return String.join(",",
                csvField(record.getId() == null ? "" : String.valueOf(record.getId())),
                csvField(record.getBookName()),
                csvField(record.getAuthor()),
                csvField(record.getCategoryName()),
                csvField(record.getBorrowerName()),
                csvField(record.getBorrowerNo()),
                csvField(formatDate(record.getBorrowDate())),
                csvField(formatDate(record.getDueDate())),
                csvField(formatDate(record.getReturnDate())),
                csvField(statusText(record.getStatus())),
                csvField(record.getRemark()));
    }

    /**
     * CSV 字段转义：为空返回空串；包含逗号/引号/换行时加双引号并转义内部引号。
     *
     * @param value 原始值
     * @return 转义后的字段
     */
    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 日期格式化（空值返回空串）。
     *
     * @param date 日期
     * @return yyyy-MM-dd 文本
     */
    private String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * 借阅状态码转中文。
     *
     * @param status 状态码
     * @return 中文描述
     */
    private String statusText(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case 0 -> "借阅待确认";
            case 1 -> "借阅中";
            case 2 -> "已归还";
            case 3 -> "已驳回";
            case 4 -> "归还待确认";
            default -> "未知";
        };
    }
}
