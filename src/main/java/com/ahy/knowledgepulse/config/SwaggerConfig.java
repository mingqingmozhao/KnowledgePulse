package com.ahy.knowledgepulse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("知脉 - 轻量级个人知识库 API")
                        .version("1.0.0")
                        .description("知脉是一个专注于个人知识沉淀与小团队协作的轻量级笔记系统")
                        .contact(new Contact()
                                .name("KnowledgePulse Team")
                                .email("support@knowledgepulse.com")));
    }
}
