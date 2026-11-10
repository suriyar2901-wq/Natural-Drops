package com.naturaldrops.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Safe additive schema patch for dual-payment Phase 1.
 * Adds nullable {@code orders.payment_timing} so historical rows stay valid.
 * Hibernate ddl-auto=update may also add the column; IF NOT EXISTS keeps this idempotent.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class OrdersPaymentTimingSchemaFix implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_timing varchar(30)");
            log.info("orders.payment_timing column ensured (nullable)");
        } catch (Exception ex) {
            log.error("orders.payment_timing schema fix failed: {}", ex.getMessage(), ex);
        }
    }
}
