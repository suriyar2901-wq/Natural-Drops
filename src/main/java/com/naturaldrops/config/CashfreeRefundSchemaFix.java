package com.naturaldrops.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Additive columns for Cashfree order refunds on seller/buyer cancel.
 */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class CashfreeRefundSchemaFix implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE cashfree_payments ADD COLUMN IF NOT EXISTS refund_id varchar(64)");
            jdbcTemplate.execute(
                    "ALTER TABLE cashfree_payments ADD COLUMN IF NOT EXISTS refund_amount numeric(10,2)");
            jdbcTemplate.execute(
                    "ALTER TABLE cashfree_payments ADD COLUMN IF NOT EXISTS refund_status varchar(40)");
            jdbcTemplate.execute(
                    "ALTER TABLE cashfree_payments ADD COLUMN IF NOT EXISTS refunded_at timestamp");
            log.info("cashfree_payments refund columns ensured");
        } catch (Exception ex) {
            log.error("cashfree_payments refund schema fix failed: {}", ex.getMessage(), ex);
        }
    }
}
