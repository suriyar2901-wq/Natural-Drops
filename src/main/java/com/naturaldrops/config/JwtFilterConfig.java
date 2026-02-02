package com.naturaldrops.config;

import com.naturaldrops.repository.UserRepository;
import com.naturaldrops.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class JwtFilterConfig {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    
    @Bean
    public JwtTokenFilter jwtTokenFilter() {
        return new JwtTokenFilter(jwtTokenProvider, userRepository);
    }
    
    @Bean
    public FilterRegistrationBean<JwtTokenFilter> jwtFilterRegistration() {
        FilterRegistrationBean<JwtTokenFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(jwtTokenFilter());
        registration.addUrlPatterns("/api/*");
        // Set order to run after CORS filter but before other filters
        // CORS filter typically runs at order 0, so we use 1
        registration.setOrder(1);
        registration.setEnabled(true);
        return registration;
    }
}

