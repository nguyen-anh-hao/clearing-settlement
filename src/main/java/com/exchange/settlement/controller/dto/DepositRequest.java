package com.exchange.settlement.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for {@code POST /deposit}. Monetary values are plain strings to
 * preserve fixed-point precision across languages.
 */
@Schema(name = "DepositRequest", description = "Credit cash/asset into an account.")
public record DepositRequest(
        @JsonProperty("account_id")
        @Schema(description = "Account to credit", example = "1001",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long accountId,

        @Schema(description = "Asset identifier: CASH or STOCK:<symbol>", example = "CASH",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String asset,

        @Schema(description = "Fixed-point amount as a string", example = "10000.00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String amount) {
}
