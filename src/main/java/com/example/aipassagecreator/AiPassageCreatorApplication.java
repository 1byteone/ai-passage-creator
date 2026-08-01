package com.example.aipassagecreator;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAspectJAutoProxy(exposeProxy = true)
@EnableScheduling
@SpringBootApplication
@MapperScan(basePackages = {"com.example.aipassagecreator.mapper",
        "com.example.aipassagecreator.card"})
public class AiPassageCreatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiPassageCreatorApplication.class, args);
	}

}
