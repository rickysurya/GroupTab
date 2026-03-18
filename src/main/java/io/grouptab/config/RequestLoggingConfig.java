package io.grouptab.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@Configuration
public class RequestLoggingConfig {

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        var filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludePayload(false); // don't log passwords
        filter.setIncludeHeaders(false);
        filter.setIncludeClientInfo(true);
        filter.setMaxPayloadLength(1000);
        return filter;
    }
}