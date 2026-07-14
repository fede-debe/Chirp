package com.project.chirp.config

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.context.ApplicationListener

/**
 * Refuses to start the application when no Spring profile is active.
 *
 * ## Why this exists
 * The base `application.yml` carries the **dev** backing services, and `application-prod.yml` *overrides*
 * them with production ones. Spring declares **no default profile**, so an app started without
 * `-Dspring.profiles.active=...` silently falls back to that base config.
 *
 * In production that is a data disaster with no error attached: the server would serve real traffic
 * against the **dev** database, with relaxed rate limits and long-lived JWTs — and nothing would look
 * wrong. Typically the only thing standing between prod and that outcome is a single `-D` flag in a
 * systemd unit (see `deploy/`), which is easy to lose on a server rebuild or a unit edit.
 *
 * This guard converts that silent misconfiguration into an immediate, loud boot failure.
 *
 * ## How it works
 * Listens for [ApplicationEnvironmentPreparedEvent], which fires once the `Environment` is resolved but
 * **before** the application context is created — so it trips before any datasource, cache or broker
 * connection is attempted, and the reported cause is the missing profile rather than some downstream
 * connection error.
 *
 * ## Alternatives / Why not
 * - **A `@Component` with a `@PostConstruct` check:** rejected. It runs during context refresh, by which
 *   point Hikari and friends may already have tried (and failed) to connect, burying the real cause under
 *   a confusing stack trace.
 * - **Defaulting to `dev` when unset:** rejected. A default that silently works is exactly the failure
 *   mode we're removing — the environment must always be an explicit, deliberate choice.
 */
class ActiveProfileGuard : ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    override fun onApplicationEvent(event: ApplicationEnvironmentPreparedEvent) {
        if (event.environment.activeProfiles.isEmpty()) {
            throw IllegalStateException(NO_PROFILE_MESSAGE)
        }
    }

    companion object {
        const val NO_PROFILE_MESSAGE: String =
            "No Spring profile is active — refusing to start.\n" +
                "The base configuration points at the DEV backing services, so booting without a " +
                "profile would run this application against dev infrastructure. In production that " +
                "means serving real traffic from the dev database, silently.\n" +
                "Start with an explicit profile, e.g. -Dspring.profiles.active=prod " +
                "(see deploy/) or --spring.profiles.active=dev for local development."
    }
}
