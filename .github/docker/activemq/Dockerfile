FROM eclipse-temurin:17-alpine

# Yhdistä image harjan repositoryyn
LABEL org.opencontainers.image.source https://github.com/finnishtransportagency/harja

ENV ACTIVEMQ_VERSION 5.15.9
ENV ACTIVEMQ apache-activemq-$ACTIVEMQ_VERSION
ENV TCP_PORT=61626 AMQP_PORT=5672 STOMP_PORT=61613 MQTT_PORT=1883 WS_PORT=61614 UI_PORT=8161

ENV ACTIVEMQ_HOME /opt/activemq

RUN apk add curl

COPY lataa-ja-asenna-activemq.sh /
RUN /lataa-ja-asenna-activemq.sh

COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh

WORKDIR $ACTIVEMQ_HOME

ENTRYPOINT /entrypoint.sh
