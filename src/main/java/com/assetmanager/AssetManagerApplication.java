package com.assetmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point. Run this class's main() to start the website on http://localhost:8080 */
@SpringBootApplication
public class AssetManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssetManagerApplication.class, args);
    }
}
