package com.project.chirp

import com.project.chirp.config.ActiveProfileGuard
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/***
 * Main application class for the Chirp application.
 * Enables scheduling for periodic tasks.
 * */
@SpringBootApplication
@EnableScheduling
class ChirpApplication

fun main(args: Array<String>) {
	runApplication<ChirpApplication>(*args) {
		// Registered as a listener rather than a bean so it can abort the boot *before* the context —
		// and any database/cache/broker connection — is created. See ActiveProfileGuard for why.
		addListeners(ActiveProfileGuard())
	}
}
