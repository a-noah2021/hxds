package com.example.hxds.nebula.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "hxdsdr",
                description = "华夏代驾司机子系统",
                version = "1.0"
        )
)
@Configuration
public class SpringDocConfig {


}