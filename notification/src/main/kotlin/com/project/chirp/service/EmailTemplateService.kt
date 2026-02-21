package com.project.chirp.service

import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

/***
 * Processes Thymeleaf templates with given variables.
 * @see processTemplate Processes a Thymeleaf template with the given variables.
 * We call the function in the moment of sending an email, we get back the actual
 * email HTML as a String here after having processed that template anf that HTML
 * will be attached to our spring mail client which will send the email content
 * via Mailgun.
 */
@Service
class EmailTemplateService(
    private val templateEngine: TemplateEngine
) {
    /***
     * Processes a Thymeleaf template with the given variables.
     *
     * @param templateName The name of the template to process (either account-verification.html or reset-password.html).
     * @param variables The variables to use in the template. emptyMap() by default because we could have templates without variables.
     * @return The processed template as a String.
     */
    fun processTemplate(
        templateName: String,
        variables: Map<String, Any> = emptyMap()
    ): String {
        val context = Context().apply {
            variables.forEach { (key, value) ->
                setVariable(key, value)
            }
        }

        return templateEngine.process(templateName, context)
    }
}