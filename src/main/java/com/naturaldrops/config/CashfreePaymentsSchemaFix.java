package com.naturaldrops.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-shot safe schema patch for cashfree_payments after subscription support.
 * Hibernate ddl-auto=update cannot add {@code purpose NOT NULL} on a table that
 * already has rows — Postgres rejects nulls. We add the column nullable, backfill,
 * then enforce NOT NULL, and allow {@code order_id} to be null for subscriptions.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class CashfreePaymentsSchemaFix implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE cashfree_payments ADD COLUMN IF NOT EXISTS purpose varchar(32)");
            int updated = jdbcTemplate.update(
                    "UPDATE cashfree_payments SET purpose = 'ORDER_BILL' WHERE purpose IS NULL");
            if (updated > 0) {
                log.info("Backfilled purpose=ORDER_BILL on {} cashfree_payments row(s)", updated);
            }
            jdbcTemplate.execute(
                    "ALTER TABLE cashfree_payments ALTER COLUMN purpose SET DEFAULT 'ORDER_BILL'");
            try {
                jdbcTemplate.execute("ALTER TABLE cashfree_payments ALTER COLUMN purpose SET NOT NULL");
            } catch (Exception ex) {
                log.warn("Could not set purpose NOT NULL yet: {}", ex.getMessage());
            }

            jdbcTemplate.execute(
                    "ALTER TABLE cashfree_payments ADD COLUMN IF NOT EXISTS subscription_plan varchar(20)");

            try {
                jdbcTemplate.execute("ALTER TABLE cashfree_payments ALTER COLUMN order_id DROP NOT NULL");
            } catch (Exception ex) {
                log.debug("order_id already nullable or alter skipped: {}", ex.getMessage());
            }
        } catch (Exception ex) {
            log.error("cashfree_payments schema fix failed: {}", ex.getMessage(), ex);
        }
    }
}
