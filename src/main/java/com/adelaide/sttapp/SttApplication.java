package com.adelaide.sttapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Speech-to-Text assignment web application.
 *
 * Packaged as a single executable (fat/uber) JAR by the
 * spring-boot-maven-plugin configured in pom.xml.
 */
@SpringBootApplication
public class SttApplication {

    public static void main(String[] args) {
        SpringApplication.run(SttApplication.class, args);
    }
}
