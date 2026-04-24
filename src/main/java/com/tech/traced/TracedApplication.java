package com.tech.traced;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class TracedApplication {

	public static void main(String[] args) {
		SpringApplication.run(TracedApplication.class, args);
	}

	@GetMapping("/hello")
	public String sayHello() {
		return "Hello from Traced!";
	}
}
