FROM apache/artemis:2.57.0-alpine@sha256:c691a1fe64eeda556db8b550822caa6c50cd8088c70b38bc22c26cf2aa7312bb

# Yhdistä image harjan repositoryyn
LABEL org.opencontainers.image.source=https://github.com/finnishtransportagency/harja

ENV TCP_PORT=61616 UI_PORT=8161
ENV ARTEMIS_USER=admin ARTEMIS_PASSWORD=admin
ENV EXTRA_ARGS="--http-host 0.0.0.0 --relax-jolokia --default-port $TCP_PORT --http-port $UI_PORT"

