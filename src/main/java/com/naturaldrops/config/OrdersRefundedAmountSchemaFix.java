package com.naturaldrops.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Additive column for cancel+Cashfree refund visibility on orders.
 */
@Component
@Order(4)
@RequiredArgsConstructor
@Slf4j
public class OrdersRefundedAmountSchemaFix implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE orders ADD COLUMN IF NOT EXISTS refunded_amount numeric(10,2)");
            log.info("orders.refunded_amount column ensured");
        } catch (Exception ex) {
            log.error("orders.refunded_amount schema fix failed: {}", ex.getMessage(), ex);
        }
    }
}
