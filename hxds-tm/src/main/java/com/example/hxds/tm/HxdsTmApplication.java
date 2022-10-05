package com.example.hxds.tm;

import com.codingapi.txlcn.tm.config.EnableTransactionManagerServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableTransactionManagerServer
public class HxdsTmApplication {

    public static void main(String[] args) {
        SpringApplication.run(HxdsTmApplication.class, args);
    }

}
