package com.rpe.cardforge.cardholder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CardholderServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(CardholderServiceApplication.class, args);
  }
}
