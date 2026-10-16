package com.exchange.settlement.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for {@code POST /settle}. Carries both the exchange-facing
 * maker/taker identity (persisted in {@code trades}) and the settlement-facing
 * buyer/seller identity (used for the double-entry ledger rows).
 */
@Schema(name = "SettleRequest", description = "A matched trade to clear and settle.")
public record SettleRequest(
        @JsonProperty("trade_id")
        @Schema(description = "Unique trade id; replays are idempotent no-ops", example = "10001",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long tradeId,

        @Schema(description = "Instrument symbol", example = "AAPL",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String symbol,

        @Schema(description = "Execution price as a fixed-point string", example = "150.00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String price,

        @Schema(description = "Executed quantity as a fixed-point string", example = "10",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String quantity,

        @JsonProperty("buyer_fee")
        @Schema(description = "Buyer-side fee (defaults to 0)", example = "1.50")
        String buyerFee,

        @JsonProperty("seller_fee")
        @Schema(description = "Seller-side fee (defaults to 0)", example = "1.50")
        String sellerFee,

        @JsonProperty("maker_order_id")
        @Schema(description = "Maker order id", example = "1")
        long makerOrderId,

        @JsonProperty("taker_order_id")
        @Schema(description = "Taker order id", example = "2")
        long takerOrderId,

        @JsonProperty("maker_account_id")
        @Schema(description = "Maker account id", example = "1002")
        long makerAccountId,

        @JsonProperty("taker_account_id")
        @Schema(description = "Taker account id", example = "1001")
        long takerAccountId,

        @JsonProperty("buyer_account_id")
        @Schema(description = "Buyer account id (credited with the asset)", example = "1001")
        long buyerAccountId,

        @JsonProperty("seller_account_id")
        @Schema(description = "Seller account id (debited of the asset)", example = "1002")
        long sellerAccountId) {
}
