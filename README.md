# Clearing & Settlement (`clearing-settlement`)

> **Double-Entry General Ledger & Trade Settlement Service** built with Java 21 & Spring Boot 3.

---

## 1. Primary Responsibility
This service is the financial "Source of Truth" for money and asset custody. It performs **strict double-entry bookkeeping**: every trade fill produces balanced debit and credit ledger rows. It guarantees idempotency (replaying the same `trade_id` is a safe no-op) and prevents unbacked asset generation.

$$\sum \text{Debits} = \sum \text{Credits}$$

---

## 2. Boundaries & I/O
- **Ingress:** Consumes settled trade batches (`POST /settle`), deposits (`POST /deposit`), and withdrawals (`POST /withdraw`).
- **Database:** PostgreSQL (`settlement` schema) for immutable append-only ledger entries and account balance tables.
- **Egress (Transactional Outbox):** Every settlement operation also writes accounting events to the `outbox` table (same DB transaction). Debezium CDC tails the table and relays the events to Kafka (`accounting.*` topics) for `accounting-tax-service`.
- **Contract Reference:** `trading-contracts/proto/ledger/settlement.proto` (HTTP) and `trading-contracts/avro/accounting/*.avsc` (Kafka events).

---

## 3. Quick Start (Run in 2 minutes)

### Prerequisites
- JDK 21+
- Maven 3.9+

```bash
# 1. Start local PostgreSQL
docker run -d --name settlement-db -p 5432:5432 \
  -e POSTGRES_DB=settlement -e POSTGRES_USER=settlement -e POSTGRES_PASSWORD=settlement \
  postgres:15-alpine

# 2. Build and run Spring Boot
mvn spring-boot:run
```

---

## 4. Executable Documentation (`requests.http`)

```http
### Apply Deposit (Credit Cash)
POST http://localhost:8083/deposit
Content-Type: application/json

{
  "account_id": 1001,
  "asset": "CASH",
  "amount": "10000.00"
}

### Settle Executed Trade (Double-entry transaction)
POST http://localhost:8083/settle
Content-Type: application/json

{
  "trade_id": 5001,
  "symbol": "AAPL",
  "price": "150.00",
  "quantity": "10",
  "buyer_fee": "1.50",
  "seller_fee": "1.50",
  "maker_order_id": 1,
  "taker_order_id": 2,
  "maker_account_id": 1002,
  "taker_account_id": 1001,
  "buyer_account_id": 1001,
  "seller_account_id": 1002
}

### Query Account Ledger (Audit Trail)
GET http://localhost:8083/ledger?account_id=1001

### Query Realized & Unrealized PnL
GET http://localhost:8083/pnl?account_id=1001
```

---

## 5. Engineering Roadmap (Database Throughput & Correctness)

| Version | Phase | Optimization Target & Database Problem |
| :--- | :--- | :--- |
| **v1.0.0** *(Current)* | **Baseline Ledger** | Spring Data JDBC / JPA with PostgreSQL transaction isolation. HTTP/JSON endpoints. |
| **v1.1.0** | **Asynchronous Batch Settlement** | Transition from synchronous HTTP `/settle` to Kafka Consumer group with batch commit. Process 5,000 trades/sec using JDBC batch insert (`rewriteBatchedStatements=true`). |
| **v1.2.0** | **Account Partitioning & Lock-Free Accounting** | Partition ledger tables by date / account hash. Introduce optimistic concurrency control with conflict retry algorithms to eliminate row-level lock contention on hot omnibus accounts (e.g., fee accounts). |
| **v2.0.0** | **LMAX Architecture / High-Speed CQRS** | Decouple write-side (Disruptor in-memory ledger with snapshotting) from read-side (read-only analytical replicas). Daily auto-reconciliation engine verifying $\Delta \text{Assets} = 0$ across all accounts in under 10 seconds. |

