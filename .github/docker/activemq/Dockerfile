FROM eclipse-temurin:17-alpine

# Yhdistä image harjan repositoryyn
LABEL org.opencontainers.image.source https://github.com/finnishtransportagency/harja

# Katso saatavilla oleva versiot: https://archive.apache.org/dist/activemq/
# SHA512 löytyy halutun version polusta tiedostosta päätteellä '.sha512'. Kopioi SHA512 alle.
ENV ACTIVEMQ_VERSION=5.15.16
ENV ACTIVEMQ_SHA512="5aaa73047b3f80f1a79984fe1a4b84645bd1d31ae2de3b41b774616d8290e5ff9647a0e38fd4a753e472c56efba42025be67561ac9f652442e7b226aa62236af  apache-activemq-5.15.16-bin.tar.gz"
ENV ACTIVEMQ=apache-activemq-$ACTIVEMQ_VERSION
ENV TCP_PORT=61616 AMQP_PORT=5672 STOMP_PORT=61613 MQTT_PORT=1883 WS_PORT=61614 UI_PORT=8161

ENV ACTIVEMQ_HOME /opt/activemq

RUN apk add curl

COPY lataa-ja-asenna-activemq.sh /
RUN /lataa-ja-asenna-activemq.sh

COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh

WORKDIR $ACTIVEMQ_HOME

ENTRYPOINT /entrypoint.sh
