package com.exchange.settlement.outbox;

import com.exchange.accounting.DepositEvent;
import com.exchange.accounting.LedgerEntryRecordedEvent;
import com.exchange.accounting.TradeSettledEvent;
import com.exchange.accounting.WithdrawalEvent;
import com.exchange.settlement.domain.LedgerEntry;
import com.exchange.settlement.domain.Trade;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Builds the accounting event records and writes them into the outbox table
 * (same transaction) for Debezium to relay to Kafka. Monetary values are kept
 * as fixed-point strings, mirroring the existing JSON convention.
 */
@Service
public class AccountingEventPublisher {

    private static final String TOPIC_TRADE = "accounting.trade";
    private static final String TOPIC_LEDGER = "accounting.ledger";
    private static final String TOPIC_DEPOSIT = "accounting.deposit";
    private static final String TOPIC_WITHDRAWAL = "accounting.withdrawal";

    private final OutboxRepository outbox;
    private final KafkaAvroSerializer serializer;
    private final boolean enabled;

    public AccountingEventPublisher(OutboxRepository outbox,
                                    KafkaAvroSerializer serializer,
                                    @Value("${app.accounting.outbox.enabled:true}") boolean enabled) {
        this.outbox = outbox;
        this.serializer = serializer;
        this.enabled = enabled;
    }

    public void publishTradeSettled(Trade t) {
        if (!enabled) {
            return;
        }
        TradeSettledEvent event = TradeSettledEvent.newBuilder()
                .setTradeId(t.tradeId())
                .setSymbol(t.symbol())
                .setPrice(t.price().toPlainString())
                .setQuantity(t.quantity().toPlainString())
                .setBuyerFee(t.buyerFee().toPlainString())
                .setSellerFee(t.sellerFee().toPlainString())
                .setMakerOrderId(t.makerOrderId())
                .setTakerOrderId(t.takerOrderId())
                .setMakerAccountId(t.makerAccountId())
                .setTakerAccountId(t.takerAccountId())
                .setBuyerAccountId(t.buyerAccountId())
                .setSellerAccountId(t.sellerAccountId())
                .setTimestampNs(System.nanoTime())
                .build();
        outbox.insert("trade", String.valueOf(t.tradeId()),
                TradeSettledEvent.class.getSimpleName(), serialize(TOPIC_TRADE, event));
    }

    public void publishLedgerEntries(List<LedgerEntry> entries) {
        if (!enabled) {
            return;
        }
        for (LedgerEntry e : entries) {
            LedgerEntryRecordedEvent event = LedgerEntryRecordedEvent.newBuilder()
                    .setEntryId(e.entryId())
                    .setAccountId(e.accountId())
                    .setAsset(e.asset())
                    .setDebit(e.debit().toPlainString())
                    .setCredit(e.credit().toPlainString())
                    .setReference(nullToEmpty(e.reference()))
                    .setReason(nullToEmpty(e.reason()))
                    .setTimestampNs(e.timestampNs())
                    .build();
            outbox.insert("ledger", String.valueOf(e.entryId()),
                    LedgerEntryRecordedEvent.class.getSimpleName(), serialize(TOPIC_LEDGER, event));
        }
    }

    public void publishDeposit(long accountId, String asset, BigDecimal amount, List<LedgerEntry> entries) {
        if (!enabled) {
            return;
        }
        DepositEvent event = DepositEvent.newBuilder()
                .setAccountId(accountId)
                .setAsset(asset)
                .setAmount(amount.toPlainString())
                .setReference("deposit")
                .setTimestampNs(System.nanoTime())
                .build();
        outbox.insert("deposit", String.valueOf(accountId),
                DepositEvent.class.getSimpleName(), serialize(TOPIC_DEPOSIT, event));
        publishLedgerEntries(entries);
    }

    public void publishWithdrawal(long accountId, String asset, BigDecimal amount, List<LedgerEntry> entries) {
        if (!enabled) {
            return;
        }
        WithdrawalEvent event = WithdrawalEvent.newBuilder()
                .setAccountId(accountId)
                .setAsset(asset)
                .setAmount(amount.toPlainString())
                .setReference("withdrawal")
                .setTimestampNs(System.nanoTime())
                .build();
        outbox.insert("withdrawal", String.valueOf(accountId),
                WithdrawalEvent.class.getSimpleName(), serialize(TOPIC_WITHDRAWAL, event));
        publishLedgerEntries(entries);
    }

    private byte[] serialize(String topic, SpecificRecord record) {
        return serializer.serialize(topic, record);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
