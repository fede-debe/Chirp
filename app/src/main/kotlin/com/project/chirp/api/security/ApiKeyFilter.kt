package com.project.chirp.api.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

@Component
class ApiKeyFilter(
    @Value("\${app.api.key}") private val expectedApiKey: String
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val apiKey = request.getHeader(API_KEY_HEADER)
        if (!isValidApiKey(apiKey)) {
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"error":"Unauthorized"}""")
            return
        }
        filterChain.doFilter(request, response)
    }

    // Constant-time comparison prevents timing attacks
    private fun isValidApiKey(apiKey: String?): Boolean {
        if (apiKey == null) return false
        return MessageDigest.isEqual(apiKey.toByteArray(), expectedApiKey.toByteArray())
    }

    companion object {
        const val API_KEY_HEADER = "X-Api-Key"
    }
}
