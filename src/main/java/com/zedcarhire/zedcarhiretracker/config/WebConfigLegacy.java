package com.zedcarhire.zedcarhiretracker.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfigLegacy {
    @Bean
    public FilterRegistrationBean<ApiKeyFilterConfig> apiKeyFilterRegistration(ApiKeyFilterConfig filter) {
        FilterRegistrationBean<ApiKeyFilterConfig> reg = new FilterRegistrationBean<>();
        reg.setFilter(filter);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(1);
        return reg;
    }
}
