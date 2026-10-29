package com.posapp.logging;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class LoggingWebConfig implements WebMvcConfigurer {
    private final RequestUserInterceptor interceptor;

    public LoggingWebConfig(RequestUserInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**").order(Integer.MIN_VALUE);
    }
}
