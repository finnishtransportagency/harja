FROM eclipse-temurin:17-alpine

# Yhdistä image harjan repositoryyn
LABEL org.opencontainers.image.source https://github.com/finnishtransportagency/harja

# Katso saatavilla oleva versiot: https://archive.apache.org/dist/activemq/
# SHA512 löytyy halutun version polusta tiedostosta päätteellä '.sha512'. Kopioi SHA512 alle.
ENV ACTIVEMQ_VERSION=5.15.9
ENV ACTIVEMQ_SHA512="35cae4258e38e47f9f81e785f547afc457fc331d2177bfc2391277ce24123be1196f10c670b61e30b43b7ab0db0628f3ff33f08660f235b7796d59ba922d444f  apache-activemq-5.15.9-bin.tar.gz"
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
