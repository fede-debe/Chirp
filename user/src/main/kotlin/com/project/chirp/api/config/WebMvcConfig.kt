package com.project.chirp.api.config

import org.springframework.stereotype.Component
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/***
 * Configures web MVC settings for the application.
 *
 * @param ipRateLimitInterceptor The IP rate limit interceptor.
 */
@Component
class WebMvcConfig(
    private val ipRateLimitInterceptor: IpRateLimitInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(ipRateLimitInterceptor)
            .addPathPatterns("/api/**")
    }
}