package com.example.bookborrowing.controller;

import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.config.CloudProps;
import com.example.bookborrowing.service.HdfsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * HDFS 分布式归档接口（仅管理员）：借阅记录 CSV 归档写入 HDFS、归档文件列表与预览。
 * <p>
 * app.hdfs.enabled=false 时接口返回友好提示；Hadoop 集群不可连接时返回具体错误信息。
 */
@RestController
@RequestMapping("/api/hdfs")
public class HdfsController {

    /** 懒注入：hdfs.enabled=false 时容器中没有实现 Bean，避免启动失败 */
    private final ObjectProvider<HdfsService> hdfsProvider;

    private final CloudProps cloudProps;

    public HdfsController(ObjectProvider<HdfsService> hdfsProvider, CloudProps cloudProps) {
        this.hdfsProvider = hdfsProvider;
        this.cloudProps = cloudProps;
    }

    /**
     * 执行归档：把全部借阅记录导出 CSV 写入 HDFS。
     *
     * @param request HTTP 请求
     * @return 归档结果（文件路径、记录数、大小）
     */
    @PostMapping("/archive/run")
    public Result<Map<String, Object>> runArchive(HttpServletRequest request) {
        HdfsService hdfs = requireAdminAndEnabled(request);
        return Result.success(hdfs.archiveBorrowRecords());
    }

    /**
     * 归档文件列表。
     *
     * @param request HTTP 请求
     * @return 文件列表
     */
    @GetMapping("/archive/files")
    public Result<List<Map<String, Object>>> listFiles(HttpServletRequest request) {
        HdfsService hdfs = requireAdminAndEnabled(request);
        return Result.success(hdfs.listArchiveFiles());
    }

    /**
     * 预览归档文件前 N 行。
     *
     * @param fileName 文件名
     * @param lines    行数，默认 20
     * @param request  HTTP 请求
     * @return 文件内容
     */
    @GetMapping("/archive/preview")
    public Result<String> preview(@RequestParam String fileName,
                                  @RequestParam(defaultValue = "20") int lines,
                                  HttpServletRequest request) {
        HdfsService hdfs = requireAdminAndEnabled(request);
        return Result.success(hdfs.previewArchiveFile(fileName, Math.min(Math.max(lines, 1), 100)));
    }

    /**
     * 校验管理员身份与 HDFS 开关，返回可用服务。
     *
     * @param request HTTP 请求
     * @return HDFS 服务
     */
    private HdfsService requireAdminAndEnabled(HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("currentRole"))) {
            throw new com.example.bookborrowing.common.BusinessException("仅管理员可操作 HDFS 归档");
        }
        if (!cloudProps.getHdfs().isEnabled()) {
            throw new com.example.bookborrowing.common.BusinessException(
                    "HDFS 功能未启用：请在 application.yml 中将 app.hdfs.enabled 改为 true 并对接 Hadoop 集群");
        }
        HdfsService hdfs = hdfsProvider.getIfAvailable();
        if (hdfs == null) {
            throw new com.example.bookborrowing.common.BusinessException("HDFS 组件未加载，请检查 hadoop-client 依赖");
        }
        return hdfs;
    }
}
