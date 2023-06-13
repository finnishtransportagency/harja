# -- Run-time vaihe --
# Käytetään kevyttä Alpine linux Eclipse Temurin 11 (OpenJDK) imagea
FROM eclipse-temurin:11-alpine

# Lisää labeleita AWS ECR:ää varten
LABEL description="Harja app"
LABEL version="1.0"

# Asenna apurit
# Huom! busybox-extras sisältää mm. telnetin
RUN apk add --no-cache bash curl jq nano unzip vim busybox-extras

# Asenna AWS CLI v2
# FIXME: AWS CLI v2 ei toimi Alpine linuxissa suorilta ja sen asentaminen vaikuttaa hankalalta
#        Jos tätä tarvitaan, niin pitää ehkä käyttää jotain muuta kuin Alpine linuxia pohjana tai
#        asentaa AWS CLI v2 jotenkin muuten.
#RUN curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && \
# unzip awscliv2.zip && \
# ./aws/install && \
# rm -rf awscliv2.zip aws


# Luo Non-root ryhmä ja käyttäjä, jotta sovellus ei pyöri roottina
RUN addgroup -S harja && adduser -S harja -G harja

# Luo hakemisto sovellukselle ja aseta oikeudet
RUN mkdir -p /app && chown -R harja:harja /app
USER harja
WORKDIR /app

# Kopioi uberjar pipelinesta
# Tässä oleteetaan, että uberjar on jo buildattu ja se on hakemistossa "target"
COPY ./target/harja-0.0.1-SNAPSHOT-standalone.jar harja.jar

# Kopioi asetusten templaatti
# Asetuksien parametrit ja salaisuudet asetetaan AWS Fargate palvelun kautta ympäristömuuttujina
COPY ./aws/fargate/asetukset.edn asetukset.edn

# Harja-app tarkastaa tällä hetkellä jotkin asetetut ENV muuttujat .harja_env tiedoston mukaisesti
# harja.palvelin.asetukset/tarkista-ymparisto! -funktiolla
COPY ./aws/fargate/.harja_env .harja_env

# Aseta ENV muuttujat

# Java agent
ENV HARJA_JAVA_AGENT=""

# TODO: Tarkista mitkä JAVA optionsit tarvitaan
# Käytä -XshowSettings:all, jos haluat nähdä mitä asetuksia käytetään
ENV HARJA_JVM_OPTS="-Xmx6g"
# TODO: Tämä kirjasto ei ole opensourcea. Miten otetaan konttiin mukaan?
#ENV LIBS="sonic-client.jar:sonic-xmessage.jar:sonic-crypto.jar"
ENV HARJA_LIBS=""

# Aseta portti
EXPOSE 3000

# Aseta entrypoint,
ENTRYPOINT java $HARJA_JAVA_AGENT $HARJA_JVM_OPTS -cp $HARJA_LIBS:harja.jar harja.palvelin.main
