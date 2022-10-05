package com.example.hxds.odr.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "hxds-odr",
                description = "华夏代驾订单子系统",
                version = "1.0"
        )
)
@Configuration
public class SpringDocConfig {


}