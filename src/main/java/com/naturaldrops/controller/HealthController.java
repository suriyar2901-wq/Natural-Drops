package com.naturaldrops.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    /**
     * Root info for browsers / Render URL checks.
     * Not used by the mobile app; keeps "/" from returning Whitelabel 404.
     */
    @GetMapping("/")
    public String root() {
        return "Natural Drops API is running";
    }

    /**
     * Health probe for Render and load balancers.
     * Public (listed in JwtTokenFilter PUBLIC_ENDPOINTS); returns plain "OK".
     */
    @GetMapping("/api/health")
    public String healthCheck() {
        return "OK";
    }
}
