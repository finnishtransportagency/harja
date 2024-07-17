FROM apache/activemq-artemis:2.35.0-alpine

# Yhdistä image harjan repositoryyn
LABEL org.opencontainers.image.source=https://github.com/finnishtransportagency/harja

ENV TCP_PORT=61616 UI_PORT=8161
ENV ARTEMIS_USER=artemis ARTEMIS_PASSWORD=artemis
ENV EXTRA_ARGS="--http-host 0.0.0.0 --relax-jolokia --default-port $TCP_PORT --http-port $UI_PORT"

