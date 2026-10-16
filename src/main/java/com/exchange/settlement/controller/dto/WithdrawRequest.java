package com.exchange.settlement.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for {@code POST /withdraw}. Monetary values are plain strings to
 * preserve fixed-point precision across languages.
 */
@Schema(name = "WithdrawRequest", description = "Debit cash/asset from an account.")
public record WithdrawRequest(
        @JsonProperty("account_id")
        @Schema(description = "Account to debit", example = "1001",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long accountId,

        @Schema(description = "Asset identifier: CASH or STOCK:<symbol>", example = "CASH",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String asset,

        @Schema(description = "Fixed-point amount as a string", example = "500.00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String amount) {
}
