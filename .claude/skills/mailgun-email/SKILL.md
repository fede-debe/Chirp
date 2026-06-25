---
name: mailgun-email
description: Use when working on transactional email in this backend — sending HTML email via Mailgun SMTP with JavaMailSender, rendering Thymeleaf email templates, or wiring an event to an email (verification, password reset).
---

# Transactional Email (Mailgun)

## Overview

Transactional email is sent over **Mailgun SMTP** using Spring's `JavaMailSender`, with HTML
bodies rendered from **Thymeleaf templates**. Emails are triggered by consuming `UserEvent`s from
RabbitMQ (registration → verification email, forgot-password → reset email), so the `user` module
never sends email directly. See [[rabbitmq-events]].

## SMTP config

`spring.mail` in `application.yml` (+ profiles): host `smtp.mailgun.org`, port `587`, STARTTLS,
auth on; password from the `MAILGUN_PASSWORD` env var, username set per environment. The "from"
address and the app base URL (for building links) come from `chirp.email.*`. See
[[configuration]].

```yaml
spring:
  mail:
    host: smtp.mailgun.org
    port: 587
    password: ${MAILGUN_PASSWORD}
    properties: { mail: { SMTP: { auth: true, STARTTLS: { enable: true } } } }
chirp:
  email:
    from: "mail@chirp.com"
    url: "https://<host>"          # base for verification/reset links
```

## Rendering templates (EmailTemplateService)

Thin wrapper over Thymeleaf's `TemplateEngine`. Templates live in
`notification/src/main/resources/templates/emails/` (`account-verification.html`,
`reset-password.html`, plus `layout.html` and `fragments/`). Those resources are bundled into the
app jar by the `bootJar` copy step — see [[gradle-build-system]].

```kotlin
@Service
class EmailTemplateService(private val templateEngine: TemplateEngine) {
    fun processTemplate(templateName: String, variables: Map<String, Any> = emptyMap()): String {
        val context = Context().apply { variables.forEach { (k, v) -> setVariable(k, v) } }
        return templateEngine.process(templateName, context)   // e.g. "emails/account-verification"
    }
}
```

## Sending (EmailService)

Build the action URL with `UriComponentsBuilder`, render the template, send a UTF-8 HTML
multipart message. SMTP failures are **caught and logged**, not propagated (a failed email
shouldn't break the consuming flow):

```kotlin
@Service
class EmailService(
    private val javaMailSender: JavaMailSender,
    private val templateService: EmailTemplateService,
    @param:Value("\${chirp.email.from}") private val emailFrom: String,
    @param:Value("\${chirp.email.url}") private val baseUrl: String,
) {
    fun sendVerificationEmail(email: String, username: String, userId: UserId, token: String) {
        val verificationUrl = UriComponentsBuilder.fromUriString("$baseUrl/api/auth/verify")
            .queryParam("token", token).build().toUriString()
        val html = templateService.processTemplate("emails/account-verification",
            mapOf("username" to username, "verificationUrl" to verificationUrl))
        sendHtmlEmail(to = email, subject = "Verify your Chirp account", html = html)
    }

    private fun sendHtmlEmail(to: String, subject: String, html: String) {
        val message = javaMailSender.createMimeMessage()
        MimeMessageHelper(message, true, "UTF-8").apply {
            setFrom(emailFrom); setTo(to); setSubject(subject); setText(html, true)  // true = HTML
        }
        try { javaMailSender.send(message) } catch (e: MailException) { logger.error("Could not send email", e) }
    }
}
```

## Event → email wiring

`NotificationUserEventListener` (RabbitMQ consumer) maps user events to email sends:

```kotlin
@RabbitListener(queues = [MessageQueues.NOTIFICATION_USER_EVENTS], containerFactory = "rabbitListenerContainerFactory")
fun handleUserEvent(event: UserEvent) = when (event) {
    is UserEvent.Created -> emailService.sendVerificationEmail(event.email, event.username, event.userId, event.verificationToken)
    is UserEvent.RequestResendVerification -> emailService.sendVerificationEmail(...)
    is UserEvent.RequestResetPassword -> emailService.sendPasswordResetEmail(..., expiresIn = Duration.ofMinutes(event.expiresInMinutes))
    else -> Unit
}
```

## Adding a new email

1. Add a Thymeleaf template under `resources/templates/emails/` (reuse `layout.html` /
   `fragments/`).
2. Add a `send…Email(...)` method to `EmailService` that builds variables (and any link URL) and
   calls `sendHtmlEmail`.
3. Trigger it: publish a `UserEvent` (or new event) from the producing service and handle it in
   the listener — keep producers decoupled from email. See [[rabbitmq-events]].

## Common mistakes

- Sending email directly from the `user` module instead of via an event the `notification` module
  consumes.
- Letting an SMTP failure propagate and fail the consumer — catch and log (`MailException`).
- Hardcoding links instead of building them from `chirp.email.url` + `UriComponentsBuilder`.
- Forgetting the `bootJar` resource copy so templates aren't on the classpath at runtime (404).
- `setText(html)` without the `true` HTML flag (sends raw HTML as plain text).
