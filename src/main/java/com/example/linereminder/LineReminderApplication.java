package com.example.linereminder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // スケジューラを有効化
public class LineReminderApplication {

    public static void main(String[] args) {
        SpringApplication.run(LineReminderApplication.class, args);
    }
}
