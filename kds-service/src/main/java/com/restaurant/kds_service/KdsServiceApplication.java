package com.restaurant.kds_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KdsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(KdsServiceApplication.class, args);
	}

}
