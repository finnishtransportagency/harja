FROM solita/napote-circleci:latest
RUN mkdir -p /tmp/cypress-run && cd /tmp/cypress-run && npm i cypress@1.x && $(npm bin)/cypress verify

