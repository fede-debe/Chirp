package com.project.chirp.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/***
 * Configuration for Nginx proxy settings.
 *
 * Nginx is used as a reverse proxy to handle incoming requests and forward them to our backend.
 * This configuration allows specifying trusted IP addresses and whether to require a proxy for requests.
 *
 * @property trustedIps List of trusted IP addresses or CIDR ranges.
 * @property requireProxy Whether to require a proxy for incoming requests.
 *
 * @ConfigurationProperties(prefix = "nginx") is used to bind properties from the application configuration
 * (yml files) to this class.
 *
 */
@Configuration
@ConfigurationProperties(prefix = "nginx")
class NginxConfig(
    var trustedIps: List<String> = emptyList(),
    var requireProxy: Boolean = true
)