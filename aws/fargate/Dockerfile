# -- Run-time vaihe --
# Käytetään kevyttä Alpine linux Eclipse Temurin 11 (OpenJDK) imagea
FROM eclipse-temurin:11-alpine

# Lisää labeleita AWS ECR:ää varten
LABEL description="Harja app"
LABEL version="1.0"

# Asenna apurit (curl, jq, nano, unzip, vim ja busybox-extras)
# Huom! busybox-extras sisältää mm. telnetin
RUN apk add --no-cache curl jq nano unzip vim busybox-extras

# Asenna AWS CLI v2
RUN curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && \
 unzip awscliv2.zip && \
 ./aws/install && \
 rm -rf awscliv2.zip aws


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
# TODO: Tarkista mitkä JAVA optionsit tarvitaan
# ENV JAVA_JAVA_OPTIONS=

# Aseta portti
EXPOSE 3000

# Aseta entrypoint
ENTRYPOINT ["java", "-jar", "harja.jar"]
