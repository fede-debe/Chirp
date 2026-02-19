package com.project.chirp.infra.rate_limiting

import com.project.chirp.infra.config.NginxConfig
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.security.web.util.matcher.IpAddressMatcher
import org.springframework.stereotype.Component
import java.net.Inet4Address
import java.net.Inet6Address

/**
 * Resolves the client IP address from an HTTP request, considering proxy headers and trusted IPs.
 *
 * Nginx configuration primarily processes incoming requests to our server, and forward them to our backend.
 * We could also have multiple backend deployment, each running on a special port and engines that manages
 * the request.
 */
@Component
class IpResolver(
    private val nginxConfig: NginxConfig
) {
    companion object {
        private val PRIVATE_IP_RANGES = listOf(
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "127.0.0.0/8",
            "::1/128",
            "fc00::/7",
            "fe80::/10"
        ).map { IpAddressMatcher(it) }

        private val INVALID_IPS = listOf(
            "unknown",
            "unavailable",
            "0.0.0.0",
            "::"
        )
    }

    /**
     * Logger for this class which resolves client IP addresses from HTTP requests.
     */
    private val logger = LoggerFactory.getLogger(IpResolver::class.java)

    /***
     * List of trusted IP address matchers.
     *
     * These matchers are used to determine if a request comes from a trusted proxy.
     */
    private val trustedMatchers: List<IpAddressMatcher> = nginxConfig
        .trustedIps
        .filter { it.isNotBlank() }
        .map { proxy ->
            val cidr = when {
                proxy.contains("/") -> proxy // CIDR format
                proxy.contains(":") -> "$proxy/128" // IPv6 CIDR
                else -> "$proxy/32" // Default to IPv4 CIDR
            }
            IpAddressMatcher(cidr)
        }

    fun getClientIp(request: HttpServletRequest): String {
        /**
         * Resolves the client IP address from an HTTP request, considering proxy headers and trusted IPs.
         *
         * @param request The HTTP request.
         * @return The resolved client IP address.
         *
         * The remoteAddr is just the IP address of the last stop where the request is being forward to
         * your server as last resort. remoteAddr will almost never be the true client IP address.
         * If an attacker is able to bypass the proxy, they can spoof the remoteAddr anf it can be the true client IP.
         */
        val remoteAddr = request.remoteAddr

        /***
         * Checks if the request comes from a trusted proxy.
         *
         * If the request does not come from a trusted proxy and requireProxy is true, a SecurityException is thrown.
         * Otherwise, the remoteAddr is returned.
         */
        if (!isFromTrustedProxy(remoteAddr)) {
            if (nginxConfig.requireProxy) {
                logger.warn("Direct connection attempt from $remoteAddr")
                throw SecurityException("No valid client IP in proxy headers")
            }

            return remoteAddr
        }

        /**
         * If previous block is not fired, it means the request is coming from
         * a trusted proxy.
         *
         * If the remote address is one of the satisfied trusted IPs ranges coming from
         * nginxConfig.trustedIps, we can trust the X-Real-IP header since the request went
         * through nginx and nginx send this request to our backend.
         * */
        val clientIp = extractFromXRealIp(request, remoteAddr)

        if (clientIp == null) {
            logger.warn("No valid client IP in proxy headers")
            if (nginxConfig.requireProxy) {
                throw SecurityException("No valid client IP in proxy headers")
            }
        }

        return clientIp ?: remoteAddr // fallback to remoteAddr if no valid client IP found
    }

    /**
     * Extracts and validates the client IP from the X-Real-IP header.
     * Nginx will attach a http header which contains the true client
     * IP address.
     *
     * This differs based on which kind of reverse proxy is used, for nginx
     * will work. XRealIp is how nginx calls the header that set client IP.
     *
     * @param request The HTTP request.
     * @param proxyIp The IP address from nginx.
     * @return The validated and normalized client IP or null if invalid.
     */
    private fun extractFromXRealIp(
        request: HttpServletRequest,
        proxyIp: String
    ): String? {
        return request.getHeader("X-Real-IP")?.let { header ->
            validateAndNormalizeIp(header, "X-Real-IP", proxyIp)
        }
    }

    /***
     * Validates and normalizes an IP address from a header in order to always have
     * a consistent and valid IP address scheme.
     *
     * @param ip The IP address to validate and normalize.
     * @param headerName The name of the header.
     * @param proxyIp The IP address of the proxy.
     * @return The validated and normalized IP address or null if invalid.
     */
    private fun validateAndNormalizeIp(ip: String, headerName: String, proxyIp: String): String? {
        val trimmedIp = ip.trim()

        if (trimmedIp.isBlank() || INVALID_IPS.contains(trimmedIp)) {
            logger.debug("Invalid IP in $headerName: $ip from proxy $proxyIp")
            return null
        }

        return try {
            val inetAddr = when {
                trimmedIp.contains(":") -> Inet6Address.getByName(trimmedIp)
                trimmedIp.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) ->
                    Inet4Address.getByName(trimmedIp)

                else -> {
                    logger.warn("Invalid IP format in $headerName: $trimmedIp from proxy $proxyIp")
                    return null
                }
            }

            if (isPrivateIp(inetAddr.hostAddress)) {
                logger.debug("Private IP in $headerName: $trimmedIp from proxy $proxyIp")
            }

            inetAddr.hostAddress
        } catch (e: Exception) {
            logger.warn("Invalid IP format in $headerName: $trimmedIp from proxy $proxyIp", e)
            null
        }
    }

    private fun isPrivateIp(ip: String): Boolean {
        return PRIVATE_IP_RANGES.any { it.matches(ip) }
    }

    private fun isFromTrustedProxy(ip: String): Boolean {
        return trustedMatchers.any { matcher ->
            matcher.matches(ip)
        }
    }
}