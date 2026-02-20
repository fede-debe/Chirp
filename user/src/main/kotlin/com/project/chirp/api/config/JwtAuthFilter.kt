package com.project.chirp.api.config

import com.project.chirp.api.controllers.AuthController
import com.project.chirp.service.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * It is created to intercept requests, and properly include the auth information of the user
 * (JWT token for authenticated endpoint).
 * It will include them into the request context, so we can easily extract them into a controller
 * @see AuthController -> changePassword needs the userID coming from the JWT token
 *
 * @see IpRateLimitInterceptor It is very similar to what we did with the Interceptor, but we don't
 * need the interceptor since we don't need to process any annotations.
 * */
@Component
class JwtAuthFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    /***
     * Intercepts an incoming request and extracts the user ID from the JWT token if present.
     * @param request The incoming HTTP request.
     * @param response The outgoing HTTP response.
     * @param filterChain The filter chain for processing the request.
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            if (jwtService.validateAccessToken(authHeader)) {
                val userId = jwtService.getUserIdFromToken(authHeader)
                val auth = UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    emptyList()
                )
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }
}