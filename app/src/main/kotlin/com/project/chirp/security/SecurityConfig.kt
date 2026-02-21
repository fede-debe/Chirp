package com.project.chirp.security

import com.project.chirp.api.config.JwtAuthFilter
import jakarta.servlet.DispatcherType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/***
 * Configures security settings for the application.
 *
 * @see filterChain: Configures the security filter chain.
 */
@Configuration
class SecurityConfig {

    /***
     * Configures the security filter chain.
     *
     * @param httpSecurity The HTTP security configuration.
     * @param jwtAuthFilter The JWT authentication filter.
     * @return The configured security filter chain.
     *
     * @Bean is used to define a bean for dependency injection, and thanks to this annotation,
     * the Spring container will automatically create and manage instances of this bean.
     *
     * Adds the jwtAuthFilter before the UsernamePasswordAuthenticationFilter
     * to ensure that the JWT token is validated before processing authenticated requests.
     *
     * CSRF (Cross-Site Request Forgery) is disabled because we are using JWT tokens for authentication.
     * Session management is set to STATELESS in order to avoid creating and managing user sessions. This is what we want for JWT-based authentication.
     * authorizeHttpRequests is where most of the security configuration happens, and it is used to configure the authorization rules for different request paths.
     * We need a requestMatcher for the change-password endpoint to ensure that only authenticated users can access it.
     * dispatcherTypeMatchers is used to permit error responses in a non-authenticated way by using .permitAll()
     * .anyRequest() is used to configure the authorization rule for all other requests that are not explicitly covered here.
     * .exceptionHandling -> HttpStatus.UNAUTHORIZED will be the status if something goes wrong within the authorizeHttpRequests (our policy)
     */
    @Bean
    fun filterChain(httpSecurity: HttpSecurity, jwtAuthFilter: JwtAuthFilter): SecurityFilterChain {
        return httpSecurity
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/auth/**")
                    .permitAll()
                    .requestMatchers("/api/auth/change-password")
                    .authenticated()
                    .dispatcherTypeMatchers(
                        DispatcherType.ERROR,
                        DispatcherType.FORWARD
                    )
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { configurer ->
                configurer
                    .authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .build()
    }
}