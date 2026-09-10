package edu.cit.escuzar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Root application class. Living in edu.cit.escuzar (the parent package) lets
 * component scanning pick up both edu.cit.escuzar.shop (Order module) and
 * edu.cit.escuzar.inventory (Inventory module) without either module needing
 * to know about the other's package.
 */
@SpringBootApplication
public class ShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
