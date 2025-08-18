package com.paynow.agentassist.config;

import com.paynow.agentassist.interceptor.PerformanceInterceptor;
import com.paynow.agentassist.interceptor.RequestCachingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    private final PerformanceInterceptor performanceInterceptor;
    
    public WebConfig(PerformanceInterceptor performanceInterceptor) {
        this.performanceInterceptor = performanceInterceptor;
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(performanceInterceptor)
                .addPathPatterns("/api/**") // Only intercept API endpoints
                .excludePathPatterns("/actuator/**"); // Exclude actuator endpoints
    }
    
    @Bean
    public FilterRegistrationBean<RequestCachingFilter> requestCachingFilter() {
        FilterRegistrationBean<RequestCachingFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new RequestCachingFilter());
        registrationBean.addUrlPatterns("/api/*"); // Only cache request bodies for API endpoints
        registrationBean.setOrder(1);
        return registrationBean;
    }
}