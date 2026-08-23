package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.db.DbClient;
import io.testfly.steps.StepLogger;

/**
 * Shared database helpers — single source of truth for {@code db()}.
 *
 * <p>Implemented by {@code BaseTest} and {@code BaseJUnit5Test} so the delegation
 * to {@link DbClient} lives in one place. Unifies the previous inconsistency where
 * {@code BaseTest} logged via {@link StepLogger} and {@code BaseJUnit5Test} did not.
 */
@TestFlyApi(since = "1.10.0")
public interface DbSupport {

    /** Returns a {@link DbClient} backed by the default {@code database} config block. */
    default DbClient db() {
        StepLogger.step("Connect to database (default)");
        return DbClient.forDefault();
    }

    /** Returns a {@link DbClient} backed by the named entry under {@code database.datasources}. */
    default DbClient db(String datasource) {
        StepLogger.step("Connect to database: " + datasource);
        return DbClient.forNamed(datasource);
    }
}
