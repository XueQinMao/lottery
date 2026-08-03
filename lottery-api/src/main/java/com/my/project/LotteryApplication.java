package com.my.project;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LotteryApplication
 *
 * @author 刘强
 * @version 2025/07/17 11:27
 **/
@SpringBootApplication
@MapperScan("com.my.project.persistence.mapper")
@EnableScheduling
public class LotteryApplication {
    public static void main(String[] args) {
        SpringApplication.run(LotteryApplication.class, args);
    }
}
