package com.exchange.settlement.outbox;

import com.exchange.accounting.TradeSettledEvent;
import com.exchange.settlement.domain.LedgerEntry;
import com.exchange.settlement.domain.Trade;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountingEventPublisherTest {

    @Mock
    private OutboxRepository outbox;

    @Mock
    private KafkaAvroSerializer serializer;

    private AccountingEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AccountingEventPublisher(outbox, serializer, true);
        when(serializer.serialize(anyString(), any())).thenReturn(new byte[]{1, 2, 3});
    }

    @Test
    void publishTradeSettled_serializesAndWritesOutboxRow() {
        Trade t = new Trade(5001L, "AAPL", new BigDecimal("150.00"), new BigDecimal("10"),
                new BigDecimal("1.50"), new BigDecimal("1.50"),
                1L, 2L, 1002L, 1001L, 1001L, 1002L);

        publisher.publishTradeSettled(t);

        ArgumentCaptor<Object> record = ArgumentCaptor.forClass(Object.class);
        verify(serializer).serialize(eq("accounting.trade"), record.capture());
        TradeSettledEvent event = (TradeSettledEvent) record.getValue();
        assertThat(event.getTradeId()).isEqualTo(5001L);
        assertThat(event.getSymbol()).isEqualTo("AAPL");
        assertThat(event.getPrice()).isEqualTo("150.00");
        assertThat(event.getQuantity()).isEqualTo("10");
        assertThat(event.getBuyerFee()).isEqualTo("1.50");
        assertThat(event.getSellerFee()).isEqualTo("1.50");
        assertThat(event.getBuyerAccountId()).isEqualTo(1001L);
        assertThat(event.getSellerAccountId()).isEqualTo(1002L);

        verify(outbox).insert(eq("trade"), eq("5001"), eq("TradeSettledEvent"), any(byte[].class));
    }

    @Test
    void publishLedgerEntries_writesOneRowPerEntry() {
        LedgerEntry e1 = new LedgerEntry(1L, 1001L, "CASH", new BigDecimal("150.00"), BigDecimal.ZERO, "trade:5001", "BUY", 123L);
        LedgerEntry e2 = new LedgerEntry(2L, 0L, "CASH", BigDecimal.ZERO, new BigDecimal("1.50"), "trade:5001", "FEE", 124L);

        publisher.publishLedgerEntries(List.of(e1, e2));

        verify(outbox, times(2)).insert(anyString(), anyString(), anyString(), any(byte[].class));
        verify(outbox).insert(eq("ledger"), eq("1"), eq("LedgerEntryRecordedEvent"), any(byte[].class));
        verify(outbox).insert(eq("ledger"), eq("2"), eq("LedgerEntryRecordedEvent"), any(byte[].class));
    }

    @Test
    void publishDeposit_writesDepositEventAndLedgerRows() {
        LedgerEntry e = new LedgerEntry(3L, 1001L, "CASH", BigDecimal.ZERO, new BigDecimal("10000.00"), "deposit", "DEPOSIT", 200L);

        publisher.publishDeposit(1001L, "CASH", new BigDecimal("10000.00"), List.of(e));

        verify(outbox).insert(eq("deposit"), eq("1001"), eq("DepositEvent"), any(byte[].class));
        verify(outbox).insert(eq("ledger"), eq("3"), eq("LedgerEntryRecordedEvent"), any(byte[].class));
    }

    @Test
    void publishWithdrawal_writesWithdrawalEventAndLedgerRows() {
        LedgerEntry e = new LedgerEntry(4L, 1001L, "CASH", new BigDecimal("500.00"), BigDecimal.ZERO, "withdraw", "WITHDRAWAL", 300L);

        publisher.publishWithdrawal(1001L, "CASH", new BigDecimal("500.00"), List.of(e));

        verify(outbox).insert(eq("withdrawal"), eq("1001"), eq("WithdrawalEvent"), any(byte[].class));
        verify(outbox).insert(eq("ledger"), eq("4"), eq("LedgerEntryRecordedEvent"), any(byte[].class));
    }

    @Test
    void whenDisabled_noSerializationOrOutboxWrites() {
        OutboxRepository localOutbox = mock(OutboxRepository.class);
        KafkaAvroSerializer localSerializer = mock(KafkaAvroSerializer.class);
        AccountingEventPublisher disabled = new AccountingEventPublisher(localOutbox, localSerializer, false);

        disabled.publishTradeSettled(new Trade(9L, "AAPL", new BigDecimal("1"), new BigDecimal("1"),
                BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L, 0L, 0L, 0L, 0L));

        verifyNoInteractions(localOutbox);
        verifyNoInteractions(localSerializer);
    }
}
