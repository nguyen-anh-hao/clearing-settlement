# clearing-settlement

## Purpose

The financial source of truth: it books every cash and asset movement as a balanced double-entry journal and derives balances, positions and profit and loss from that journal. It owns settlement, deposits and withdrawals, and it publishes accounting events. It does not match orders, hold buying power or decide fees.

## Domain knowledge

Every operation produces equal debits and credits, including deposits and withdrawals which post against the exchange treasury account zero. A trade creates four user legs, buyer cash out and stock in plus seller stock out and cash in, and a fee leg to the treasury or a rebate leg when the combined fee is negative. A trade identifier is the idempotency key, and the trades table primary key prevents a second booking even after a restart.

Balances are never updated in place; the journal is append-only and balances are a derived materialized view, with entry identifiers seeded from the persisted maximum on restart. Cost basis uses a moving average, a sell realizes profit against that basis, and the last traded price drives unrealized profit. Amounts use exact decimals, so no binary floating point enters the ledger.

Settlement is synchronous over HTTP today, which means the caller waits for the database transaction to commit. The accounting outbox is written in that same transaction, and Debezium relays it to Kafka, so no event exists without its ledger rows. A failed transaction leaves neither rows nor events.

## Decisions and consequences

**Double-entry bookkeeping.** Every movement has a balancing leg, which makes the journal auditable and prevents money creation. The cost is roughly twice the rows and a treasury account that must be modeled explicitly.

**Append-only ledger with derived balances.** History is immutable and balances can always be rebuilt. The trade-off is that reads need aggregation or maintained views rather than a single row update.

**Transactional outbox with CDC.** Ledger rows, trades, balances and accounting events commit together, eliminating the dual-write failure mode. The cost is an outbox table, a Debezium connector and schema registry availability.

**Synchronous settlement endpoint.** The current contract is simple to reason about and easy to retry because trade identifiers are idempotent. Throughput is bounded by request round trips and database commits, which is why batch settlement is planned.

**Moving-average cost basis.** Realized profit is stable and cheap to compute, but it is not tax-lot accounting and cannot answer jurisdiction-specific lot questions.

## Operations and known limits

PostgreSQL is the intended system of record, and the service can still boot when the database is unreachable, but it cannot settle anything in that state. The outbox path additionally needs Kafka and a schema registry, and it can be disabled for a database-only run. On restart, the entry sequence is seeded from the journal, and a database outage is logged rather than fatal.

Debezium must not use the created-at column as the event timestamp, because the column is a zoned timestamp rather than the required 64-bit integer and the connector task fails. The Connect REST port is published as 18083 to avoid colliding with the settlement service on 8083. Schema registry subjects use the record's fully qualified name rather than topic-value naming, so consumers must resolve schemas by record name.

The ledger is append-only, so corrections are compensating entries, never updates or deletes. Trade settlement is synchronous, and the settlement endpoint is exposed directly on port 8083 rather than through the edge. The standalone development stack in the deployments directory provides PostgreSQL, Redpanda, schema registry, Connect and Kafka UI without any other platform service.

## Future direction

Business: settlement cycles, corporate actions and tax-lot reporting are outside today's scope and would need explicit product decisions.

Technical: asynchronous batch settlement from Kafka, ledger partitioning by date or account hash, and optimistic concurrency for hot treasury accounts. A read-write split with a rebuildable analytical replica is the long-term shape.

Boundaries: position and profit reporting may move to a dedicated portfolio service, leaving this service as the journal and settlement authority.
