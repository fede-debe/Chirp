package com.project.chirp.api.util

import jakarta.validation.Constraint
import jakarta.validation.Payload
import jakarta.validation.constraints.Pattern
import kotlin.reflect.KClass

/***
 * Validates that a password is at least 8 characters long and contains at least one digit or special character.
 *
 * @param message: The error message to display if the validation fails.
 * @param groups: The validation groups to which this constraint belongs.
 * @param payload: Additional payload information for this constraint.
 *
 * @Target: Specifies the locations where this annotation can be used. In this case, it can be used on fields and property getters.
 * @Retention: Specifies the retention policy for this annotation. In this case, it is set to RUNTIME, meaning the annotation will be available at runtime.
 * @Constraint: Specifies that this annotation is a constraint. In this case, it is used to validate that a password meets certain criteria.
 * @Pattern: Specifies that this constraint is a pattern match. In this case, it checks that the password matches a regular expression.
 * It is validated by Jakarta Validation and it is responsible to let validation know that it should automatically run the validation.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
@Pattern(
    regexp = "^(?=.*[\\d!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?])(.{8,})$",
    message = "Password must be at least 8 characters and contain at least one digit or special character"
)
annotation class Password(
    val message: String = "Password must be at least 8 characters and contain at least one digit or special character",
    val groups: Array<KClass<out Any>> = [],
    val payload: Array<KClass<out Payload>> = []
)