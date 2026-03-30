package com.example.aipassagecreator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication
public class AiPassageCreatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiPassageCreatorApplication.class, args);
	}

}
