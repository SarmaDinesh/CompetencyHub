package com.example.CompetencyHub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * @ConfigurationPropertiesScan finds every @ConfigurationProperties class in this
 * package tree and registers it as a bean — so new settings classes need no extra
 * wiring, only the annotation.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CompetencyHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(CompetencyHubApplication.class, args);
	}

}
