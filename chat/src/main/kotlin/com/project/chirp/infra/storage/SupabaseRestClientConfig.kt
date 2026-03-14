package com.project.chirp.infra.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/***
 * Configures the RestClient for Supabase API calls.
 *
 * default headers content/json is removed since we can't use it with delete requests.
 * It will be added manually to requests that need it.
 */
@Configuration
class SupabaseRestClientConfig(
    @param:Value("\${supabase.url}") private val supabaseUrl: String,
    @param:Value("\${supabase.service-key}") private val supabaseServiceKey: String,
) {

    @Bean
    fun supabaseRestClient(): RestClient {
        return RestClient.builder()
            .baseUrl(supabaseUrl)
            .defaultHeader("Authorization", "Bearer $supabaseServiceKey")
            .build()
    }
}