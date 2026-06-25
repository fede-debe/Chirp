package com.project.chirp.infra.social

import com.nimbusds.jwt.JWTClaimsSet

/***
 * Reads the `email_verified` claim, which providers may serialize as a boolean `true` or the
 * string `"true"` (Apple in particular uses the string form). Anything else is treated as false.
 */
internal fun JWTClaimsSet.readEmailVerified(): Boolean = when (val value = getClaim("email_verified")) {
    is Boolean -> value
    is String -> value.equals("true", ignoreCase = true)
    else -> false
}
