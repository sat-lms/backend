package com.sat.lms.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI satLmsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SAT-LMS API")
                        .description("스터디 과제 제출 사이트 API 명세서")
                        .version("v1.0.0"));
    }
}