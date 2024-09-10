FROM apache/activemq-artemis:2.32.0-alpine

# Yhdistä image harjan repositoryyn
LABEL org.opencontainers.image.source=https://github.com/finnishtransportagency/harja

ENV TCP_PORT=61616 UI_PORT=8161
ENV ARTEMIS_USER=admin ARTEMIS_PASSWORD=admin
ENV EXTRA_ARGS="--http-host 0.0.0.0 --relax-jolokia --default-port $TCP_PORT --http-port $UI_PORT"

