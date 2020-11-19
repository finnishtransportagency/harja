FROM solita/napote-circleci:latest
USER root
RUN npm install npm -g
RUN npm cache clean -f
RUN npm install -g n
RUN n stable
USER circleci
RUN mkdir -p /tmp/cypress-run && cd /tmp/cypress-run && npm i cypress@5.6.0 && $(npm bin)/cypress verify

