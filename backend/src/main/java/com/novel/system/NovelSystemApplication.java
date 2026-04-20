package com.novel.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableScheduling
@EnableMongoRepositories(basePackages = "com.novel.system.repository.mongo")
@MapperScan("com.novel.system.mapper")
public class NovelSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovelSystemApplication.class, args);
    }
}
