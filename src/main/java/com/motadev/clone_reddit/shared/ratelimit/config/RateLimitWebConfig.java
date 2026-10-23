package com.motadev.clone_reddit.shared.ratelimit.config;

import com.motadev.clone_reddit.shared.ratelimit.logging.RateLimitEventLog;
import com.motadev.clone_reddit.shared.ratelimit.store.RateLimitStore;
import com.motadev.clone_reddit.shared.ratelimit.web.RateLimitFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
public class RateLimitWebConfig {

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitProperties properties,
            RateLimitStore rateLimitStore,
            RateLimitEventLog rateLimitEventLog,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver
    ) {
        RateLimitFilter rateLimitFilter = new RateLimitFilter(
                properties,
                rateLimitStore,
                rateLimitEventLog,
                handlerExceptionResolver
        );
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(rateLimitFilter);
        registration.setOrder(rateLimitFilter.getOrder());
        registration.addUrlPatterns("/*");
        return registration;
    }
}
