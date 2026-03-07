package com.project.chirp

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
	runApplication<ChirpApplication>(*args)
}
