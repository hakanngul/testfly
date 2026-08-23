package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.email.EmailCriteria;
import io.testfly.email.MailboxClient;
import io.testfly.steps.StepLogger;

/**
 * Shared email helpers — single source of truth for {@code mailbox()} / {@code to()}.
 *
 * <p>Implemented by {@code BaseTest} and {@code BaseJUnit5Test} so the delegation
 * to {@link MailboxClient} lives in one place. Unifies the previous inconsistency where
 * {@code BaseTest} logged via {@link StepLogger} and {@code BaseJUnit5Test} did not.
 */
@TestFlyApi(since = "1.10.0")
public interface EmailSupport {

    /** Returns an email inbox client configured from {@code email.*} in {@code testfly.yml}. */
    default MailboxClient mailbox() {
        StepLogger.step("Open mailbox client");
        return MailboxClient.create();
    }

    /** Shorthand for {@link EmailCriteria#to(String)}. */
    default EmailCriteria to(String address) {
        return EmailCriteria.to(address);
    }
}
