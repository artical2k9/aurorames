package com.mes.workorder.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "mes.security")
@Validated
public class WorkOrderSecurityProperties {

    @NotBlank(message = "mes.security.webhook-token must not be blank — set MES_SECURITY_WEBHOOK_TOKEN env var")
    private String webhookToken;

    public String getWebhookToken() {
        return webhookToken;
    }
    public void setWebhookToken(String webhookToken) {
        this.webhookToken = webhookToken;
    }
}
