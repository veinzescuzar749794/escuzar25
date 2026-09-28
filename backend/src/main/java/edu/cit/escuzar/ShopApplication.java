package edu.cit.escuzar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Root application class. Living in edu.cit.escuzar (the parent package) lets
 * component scanning pick up edu.cit.escuzar.shop, edu.cit.escuzar.inventory,
 * edu.cit.escuzar.notification, and edu.cit.escuzar.supplier.
 */
@SpringBootApplication
@EnableScheduling
public class ShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
