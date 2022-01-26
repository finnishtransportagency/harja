FROM solita/napote-circleci:latest

USER root
RUN curl -fsSL https://deb.nodesource.com/setup_16.x | bash -
RUN apt-get install -y nodejs
RUN npm install npm -g
RUN npm cache clean -f
RUN npm install -g n
RUN n stable
USER circleci
RUN mkdir -p /tmp/cypress-run && cd /tmp/cypress-run && npm i cypress@^9.1.1 && $(npm bin)/cypress verify