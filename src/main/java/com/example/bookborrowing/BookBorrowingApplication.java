package com.example.bookborrowing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 图书借阅管理系统启动类。
 */
@SpringBootApplication
@EnableScheduling
public class BookBorrowingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookBorrowingApplication.class, args);
    }
}
