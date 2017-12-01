package com.moglix.reports;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@ComponentScan({"com.moglix.reports"})
@SpringBootApplication
@EnableScheduling
//@EnableSpringDataWebSupport
public class ReportsApplication {
	

	public static void main(String[] args) {
		SpringApplication.run(ReportsApplication.class, args);
	}
}
