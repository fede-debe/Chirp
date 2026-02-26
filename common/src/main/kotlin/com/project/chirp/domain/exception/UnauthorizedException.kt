package com.project.chirp.domain.exception

/***
 * Exception thrown when authentication details are missing through all the modules.
 */
class UnauthorizedException : RuntimeException("Missing auth details")