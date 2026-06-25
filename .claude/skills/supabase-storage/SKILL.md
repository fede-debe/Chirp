---
name: supabase-storage
description: Use when working with file uploads/storage in this backend — generating Supabase signed upload URLs, the direct-to-storage upload flow for profile pictures or message attachments, the Supabase RestClient bean, or deleting stored files.
---

# File Storage (Supabase)

## Overview

Files are uploaded **directly from the client to Supabase Storage** using short-lived
**signed upload URLs** the backend generates — file bytes never pass through the backend. The
backend's job is: mint a signed URL, then later record the resulting public URL. A single Spring
`RestClient` bean talks to the Supabase Storage REST API with the service key.

## RestClient bean

```kotlin
@Configuration
class SupabaseRestClientConfig(
    @param:Value("\${supabase.url}") private val supabaseUrl: String,
    @param:Value("\${supabase.service-key}") private val supabaseServiceKey: String,
) {
    @Bean
    fun supabaseRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(supabaseUrl)
            .defaultHeader("Authorization", "Bearer $supabaseServiceKey")
            .build()
    // NOTE: no default Content-Type — DELETE can't carry one; set it per-request where needed.
}
```

Config: `supabase.url` and `supabase.service-key` (from `SUPABASE_SERVICE_KEY` env). See
[[configuration]]. The service key is a privileged credential — keep it server-side only.

## The upload flow (three steps)

```
1. Client → backend:  POST /api/participants/profile-picture-upload?mimeType=image/jpeg
   backend → Supabase: create signed upload URL
   backend → client:   { uploadUrl, publicUrl, headers, expiresAt }
2. Client → Supabase:  PUT the bytes to uploadUrl directly (never touches the backend)
3. Client → backend:  POST /api/participants/confirm-profile-picture { publicUrl }
   backend: store publicUrl on the participant, broadcast ProfilePictureUpdatedEvent
```

Step 3's broadcast goes out over websockets via an in-process event — see
[[websocket-realtime]] / [[spring-application-events]].

## Generating a signed URL (SupabaseStorageService)

```kotlin
@Service
class SupabaseStorageService(
    @param:Value("\${supabase.url}") private val supabaseUrl: String,
    private val supabaseRestClient: RestClient,
) {
    companion object {
        private val allowedMimeTypes = mapOf(
            "image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp",
            "audio/mpeg" to "mp3", "audio/m4a" to "m4a", /* ... */
        )
    }

    fun generateSignedUploadUrl(userId: UserId, mimeType: String): ProfilePictureUploadCredentials {
        val extension = allowedMimeTypes[mimeType] ?: throw InvalidProfilePictureException("Invalid mime type $mimeType")
        val path = "profile-pictures/user_${userId}_${UUID.randomUUID()}.$extension"
        return ProfilePictureUploadCredentials(
            uploadUrl = createSignedUrl(path, expiresInSeconds = 300),
            publicUrl = "$supabaseUrl/storage/v1/object/public/$path",
            headers = mapOf("Content-Type" to mimeType),
            expiresAt = Instant.now().plusSeconds(300),
        )
    }

    private fun createSignedUrl(path: String, expiresInSeconds: Int): String {
        val response = supabaseRestClient.post()
            .uri("/storage/v1/object/upload/sign/$path")
            .header("Content-Type", "application/json")          // set per-request (no default)
            .body("""{ "expiresIn": $expiresInSeconds }""")
            .retrieve().body(SignedUploadResponse::class.java)
            ?: throw StorageException("Failed to create signed URL")
        return "$supabaseUrl/storage/v1${response.url}"
    }
}
```

Rules embedded here:
- **MIME allowlist** maps accepted content types to file extensions; anything else throws
  `InvalidProfilePictureException`. This is the validation gate — extend the map to allow a new type.
- **Signed URLs are short-lived** (300s) — long enough for a direct upload, short enough to limit
  exposure.
- **Structured storage paths** group files for easy bulk operations:
  `profile-pictures/user_<id>_<uuid>.<ext>` and
  `message-attachments/<chatId>/<userId>/<uuid>.<ext>`.
- The credentials DTO (`ProfilePictureUploadCredentials`) is reused for attachments too — same
  shape, no need for a separate type.

## Deleting a file

Derive the storage path from the public URL and issue a DELETE; treat any error status as a
failure:

```kotlin
fun deleteFile(url: String) {
    val path = if (url.contains("/object/public/")) url.substringAfter("/object/public/")
               else throw StorageException("Invalid file URL format")
    val response = supabaseRestClient.delete().uri("/storage/v1/object/$path").retrieve().toBodilessEntity()
    if (response.statusCode.isError) throw StorageException("Unable to delete file: ${response.statusCode.value()}")
}
```

## Adding a new upload type

1. Add the accepted MIME types (and extensions) to `allowedMimeTypes`.
2. Add a `generate…UploadUrl` method choosing a structured `path`, reuse
   `ProfilePictureUploadCredentials`.
3. Expose an endpoint that returns the credentials and (if needed) a confirm endpoint that
   records the public URL. See [[rest-api]].

## Common mistakes

- Proxying file bytes through the backend instead of using the signed-URL direct upload.
- Skipping the MIME allowlist (lets clients store arbitrary content types).
- Long-lived signed URLs.
- Setting a default `Content-Type` on the RestClient (breaks DELETE) — set it per request.
- Leaking the Supabase service key to clients — it's server-side only.
