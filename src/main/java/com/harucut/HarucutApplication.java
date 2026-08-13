package com.harucut;

import com.harucut.auth.cookie.CookieProperties;
import com.harucut.auth.jwt.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties({JwtProperties.class, CookieProperties.class})
@SpringBootApplication
public class HarucutApplication {

    public static void main(String[] args) {
        SpringApplication.run(HarucutApplication.class, args);
    }

}
