# =============================================================================
# Kafka Connect image with the Debezium PostgreSQL connector.
#
# The connector is fetched straight from Maven Central instead of via
# `confluent-hub install`. Confluent Hub no longer publishes Debezium 2.7.x
# (its catalogue jumps from 2.5.x to 3.x), so `confluent-hub install ...
# :2.7.0.Final` fails with "Component not found". Debezium 2.7.x also matches
# the Kafka version of this base image (cp-kafka-connect:7.7.1 -> Kafka 3.7),
# whereas Debezium 3.x targets Kafka 3.9+.
#
# The `-plugin.tar.gz` archive expands to a single `debezium-connector-postgres/`
# directory that already contains every runtime jar (debezium-core,
# debezium-api, the PostgreSQL JDBC driver, ...), which is exactly the layout
# Kafka Connect expects under `plugin.path`.
#
# The base image already ships the Confluent AvroConverter used to relay the
# Schema-Registry-framed outbox payloads verbatim to Kafka.
# =============================================================================

ARG DEBEZIUM_VERSION=2.7.4.Final

FROM alpine:3.20 AS connector
ARG DEBEZIUM_VERSION
RUN apk add --no-cache curl tar \
    && curl -fsSL -o /tmp/debezium-connector-postgres.tar.gz \
        "https://repo1.maven.org/maven2/io/debezium/debezium-connector-postgres/${DEBEZIUM_VERSION}/debezium-connector-postgres-${DEBEZIUM_VERSION}-plugin.tar.gz" \
    && mkdir -p /plugins \
    && tar -xzf /tmp/debezium-connector-postgres.tar.gz -C /plugins \
    && rm /tmp/debezium-connector-postgres.tar.gz

FROM confluentinc/cp-kafka-connect:7.7.1
COPY --from=connector /plugins/debezium-connector-postgres/ /usr/share/confluent-hub-components/debezium-connector-postgres/
