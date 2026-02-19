package com.project.chirp.api.config

import com.project.chirp.domain.exception.RateLimitException
import com.project.chirp.infra.rate_limiting.IpRateLimiter
import com.project.chirp.infra.rate_limiting.IpResolver
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Duration

/**
 * Interceptor class that has a function that is being called with each and every request on our server and can
 * intercept an incoming request and do something wth it (change something about it, extract headers to wrap it
 * inside this IP rate limiter block ).
 *
 * This interceptor applies IP-based rate limiting to requests based on the IP address.
 *
 * @param ipRateLimiter The IP rate limiter service.
 * @param ipResolver The IP resolver service.
 * @param applyLimit Whether to apply the rate limit.
 *
 * Using Redis to store rate limit information makes sure that rate limits are consistent across multiple instances of the application
 * without depend on the server configuration since the Redis instance is stored outside the application.
 * */
@Component
class IpRateLimitInterceptor(
    private val ipRateLimiter: IpRateLimiter,
    private val ipResolver: IpResolver,
    @param:Value("\${chirp.rate-limit.ip.apply-limit}")
    private val applyLimit: Boolean
) : HandlerInterceptor {

    /***
     * Need to check if the root function of this interceptor is annotated with our custom annotation with the IP rate limit,
     * and if so we need to interpret that annotation and properly apply the IP rate limit.
     * Intercepts an incoming request and applies IP-based rate limiting if enabled.
     * @param request The incoming HTTP request.
     * @param response The outgoing HTTP response.
     * @param handler The handler method for the request.
     * @return True if the request should proceed, false if the rate limit has been exceeded.
     */
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler is HandlerMethod && applyLimit) {
            val annotation = handler.getMethodAnnotation(IpRateLimit::class.java)
            if (annotation != null) {
                val clientIp = ipResolver.getClientIp(request)

                return try {
                    ipRateLimiter.withIpRateLimit(
                        ipAddress = clientIp,
                        resetsIn = Duration.of(
                            annotation.duration,
                            annotation.unit.toChronoUnit()
                        ),
                        maxRequestsPerIp = annotation.requests,
                        action = { true }
                    )
                } catch (e: RateLimitException) {
                    response.sendError(429)
                    false
                }
            }
        }

        return true
    }
}