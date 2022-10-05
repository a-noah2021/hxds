package com.example.hxds.odr;

import com.codingapi.txlcn.tc.config.EnableDistributedTransaction;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@ServletComponentScan
@MapperScan({"com.example.hxds.odr.db.dao"})
@ComponentScan({"com.example.*"})
@EnableDistributedTransaction
public class HxdsOdrApplication {

    public static void main(String[] args) {
        SpringApplication.run(HxdsOdrApplication.class, args);

    }

}
