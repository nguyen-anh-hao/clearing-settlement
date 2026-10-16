package com.exchange.settlement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) metadata. The interactive UI is served at
 * {@code /swagger-ui.html} and the raw spec at {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI settlementOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Clearing & Settlement API")
                .version("1.0.0")
                .description("""
                        Double-entry general ledger and trade settlement service.

                        Every operation writes balanced debit/credit ledger rows and — when the
                        transactional outbox is enabled — an accounting event that Debezium CDC
                        relays to the `accounting.*` Kafka topics.

                        Monetary values are exchanged as plain fixed-point strings to preserve
                        precision across languages."""));
    }
}
