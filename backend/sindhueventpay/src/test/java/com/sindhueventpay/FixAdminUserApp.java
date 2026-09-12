package com.sindhueventpay;

import com.sindhueventpay.Repository.UserRepository;
import com.sindhueventpay.models.User;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.List;

@SpringBootApplication
public class FixAdminUserApp {
    public static void main(String[] args) {
        new SpringApplicationBuilder(FixAdminUserApp.class)
            .web(WebApplicationType.NONE)
            .run(args);
    }

    @Bean
    public CommandLineRunner run(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            System.out.println("============== FIXING DB ==============");
            User admin = userRepository.findByEmail("admin@sindhurunners.com");
            if (admin != null) {
                System.out.println("Found admin user. Current hash: " + admin.getPassword());
                String newHash = passwordEncoder.encode("admin123");
                admin.setPassword(newHash);
                userRepository.save(admin);
                System.out.println("Admin password successfully updated to 'admin123'. New hash: " + newHash);
            } else {
                System.out.println("Admin user NOT FOUND!");
            }
            System.out.println("=======================================");
            System.exit(0);
        };
    }
}
