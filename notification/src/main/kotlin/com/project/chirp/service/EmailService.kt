package com.project.chirp.service

import com.project.chirp.domain.type.UserId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration

/***
 * Service for sending emails.
 *
 * @param javaMailSender: Spring mail sender.
 * @param templateService: Service for processing Thymeleaf templates.
 * @param emailFrom: The email address to use as the sender.
 * @param baseUrl: The base URL for the application.
 *
 * This service is responsible for sending emails using Spring mail sender and Thymeleaf templates
 * we declared within the project.
 *
 * @see sendVerificationEmail Sends a verification email to the given email address.
 * @see sendPasswordResetEmail Sends a password reset email to the given email address.
 */
@Service
class EmailService(
    private val javaMailSender: JavaMailSender,
    private val templateService: EmailTemplateService,
    @param:Value("\${chirp.email.from}")
    private val emailFrom: String,
    @param:Value("\${chirp.email.url}")
    private val baseUrl: String,
) {

    /***
     * Logger for this service to check what went wrong or what went right.
     */
    private val logger = LoggerFactory.getLogger(javaClass)

    /***
     * Sends a verification email to the given email address.
     *
     * @param email The email address to send the email to.
     * @param username The username of the user.
     * @param userId The ID of the user.
     * @param token The verification token.
     */
    fun sendVerificationEmail(
        email: String,
        username: String,
        userId: UserId,
        token: String
    ) {
        logger.info("Sending verification email for user $userId")

        val verificationUrl = UriComponentsBuilder
            .fromUriString("$baseUrl/api/auth/verify")
            .queryParam("token", token)
            .build()
            .toUriString()

        val htmlContent = templateService.processTemplate(
            templateName = "emails/account-verification",
            variables = mapOf(
                "username" to username,
                "verificationUrl" to verificationUrl
            )
        )

        sendHtmlEmail(
            to = email,
            subject = "Verify your Chirp account",
            html = htmlContent
        )
    }

    /***
     * Sends a password reset email to the given email address.
     *
     * @param email The email address to send the email to.
     * @param username The username of the user.
     * @param userId The ID of the user.
     * @param token The token for the password reset.
     * @param expiresIn The duration after which the token expires.
     */
    fun sendPasswordResetEmail(
        email: String,
        username: String,
        userId: UserId,
        token: String,
        expiresIn: Duration
    ) {
        logger.info("Sending password reset email for user $userId")

        val resetPasswordUrl = UriComponentsBuilder
            .fromUriString("$baseUrl/api/auth/reset-password")
            .queryParam("token", token)
            .build()
            .toUriString()

        val htmlContent = templateService.processTemplate(
            templateName = "emails/reset-password",
            variables = mapOf(
                "username" to username,
                "resetPasswordUrl" to resetPasswordUrl,
                "expiresInMinutes" to expiresIn.toMinutes()
            )
        )

        sendHtmlEmail(
            to = email,
            subject = "Reset your Chirp password",
            html = htmlContent
        )
    }

    /***
     * Sends an HTML email.
     *
     * @param to The email address to send the email to.
     * @param subject The subject of the email.
     * @param html The HTML content of the email.
     */
    private fun sendHtmlEmail(
        to: String,
        subject: String,
        html: String
    ) {
        val message = javaMailSender.createMimeMessage()
        MimeMessageHelper(message, true, "UTF-8").apply {
            setFrom(emailFrom)
            setTo(to)
            setSubject(subject)
            setText(html, true)
        }

        try {
            javaMailSender.send(message)
        } catch (e: MailException) {
            logger.error("Could not send email", e)
        }
    }
}