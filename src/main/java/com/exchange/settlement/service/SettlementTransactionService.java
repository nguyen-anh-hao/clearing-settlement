package com.exchange.settlement.service;

import com.exchange.settlement.domain.LedgerEntry;
import com.exchange.settlement.domain.Trade;
import com.exchange.settlement.outbox.AccountingEventPublisher;
import com.exchange.settlement.repository.BalanceRepository;
import com.exchange.settlement.repository.LedgerRepository;
import com.exchange.settlement.repository.TradeRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Orchestrates the persistence of settlement operations inside a single DB
 * transaction, including the Transactional Outbox write. The ledger rows,
 * trades, balances and the outbox event are committed atomically; Debezium CDC
 * then tails the outbox table and relays the accounting events to Kafka.
 */
@Service
public class SettlementTransactionService {

    private final SettlementService settlement;
    private final TradeRepository tradeRepository;
    private final LedgerRepository ledgerRepository;
    private final BalanceRepository balanceRepository;
    private final AccountingEventPublisher accountingPublisher;
    private final Counter settled;
    private final Counter failed;

    public SettlementTransactionService(SettlementService settlement,
                                        TradeRepository tradeRepository,
                                        LedgerRepository ledgerRepository,
                                        BalanceRepository balanceRepository,
                                        AccountingEventPublisher accountingPublisher,
                                        MeterRegistry meters) {
        this.settlement = settlement;
        this.tradeRepository = tradeRepository;
        this.ledgerRepository = ledgerRepository;
        this.balanceRepository = balanceRepository;
        this.accountingPublisher = accountingPublisher;
        this.settled = meters.counter("trading_settlements_total", "status", "SETTLED");
        this.failed = meters.counter("trading_settlements_total", "status", "FAILED");
    }

    @Transactional
    public void settle(Trade t) {
        try {
            List<LedgerEntry> entries = settlement.settle(t);
            tradeRepository.insert(t);
            ledgerRepository.batchInsert(entries);
            balanceRepository.applyEntries(entries);
            accountingPublisher.publishTradeSettled(t);
            accountingPublisher.publishLedgerEntries(entries);
            settled.increment();
        } catch (RuntimeException ex) {
            failed.increment();
            throw ex;
        }
    }

    @Transactional
    public void deposit(long accountId, String asset, BigDecimal amount) {
        List<LedgerEntry> entries = settlement.deposit(accountId, asset, amount);
        ledgerRepository.batchInsert(entries);
        balanceRepository.applyEntries(entries);
        accountingPublisher.publishDeposit(accountId, asset, amount, entries);
    }

    @Transactional
    public void withdraw(long accountId, String asset, BigDecimal amount) {
        List<LedgerEntry> entries = settlement.withdraw(accountId, asset, amount);
        ledgerRepository.batchInsert(entries);
        balanceRepository.applyEntries(entries);
        accountingPublisher.publishWithdrawal(accountId, asset, amount, entries);
    }
}
