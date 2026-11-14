package com.vitalis.demo;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class Application {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    }

    public static void main(String[] args) {
        var context = SpringApplication.run(Application.class, args);
        if (context.getEnvironment().getProperty("vitalis.desktop", Boolean.class, false)) {
            // Electron owns the write end. EOF also arrives if Electron crashes.
            Thread.ofPlatform().daemon().name("desktop-lifecycle").start(() -> {
                try {
                    System.in.read();
                } catch (java.io.IOException ignored) {
                    // A broken pipe means the desktop process is gone.
                }
                context.close();
                System.exit(0);
            });
        }
    }
}