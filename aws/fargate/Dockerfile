# -- Build vaihe --
# Käytetään build vaiheessa mahdollisimman kevyttä alpine linuxia ja openjdk 11
FROM clojure:openjdk-11-lein-2.10.0-alpine as builder

# Luo build hakemisto
RUN mkdir -p /build
WORKDIR /build


# Cacheta riippuvuudet
COPY project.clj /build/
RUN lein deps

# Kopioi lähdekoodi ja resurssit (Ignorataan epärelevantit tiedostot .dockerignore:ssa)
# Kopioimalla lähdekoodin eriin overlayhin kuin riippuvuudet, varmistetaan että muutokset lähdekoodissa eivät aiheuta
# riippuvuuksien uudelleen lataamista
COPY . /build/

# Tee uberjar tuotantoa varten
RUN lein tuotanto


# -- Run-time vaihe --
# Käytetään kevyttä Alpine linux Eclipse Temurin 11 (OpenJDK) imagea
FROM eclipse-temurin:11-alpine

# Lisää labeleita AWS ECR:ää varten
LABEL description="Harja app"
LABEL version="1.0"

# Lisää apureita (curl ja jq)
RUN apk add --no-cache curl jq

# Luo Non-root ryhmä ja käyttäjä, jotta sovellus ei pyöri roottina
RUN addgroup -S harja && adduser -S harja -G harja

# Luo hakemisto sovellukselle ja aseta oikeudet
RUN mkdir -p /app && chown -R harja:harja /app
USER harja

# Kopioi uberjar build vaiheesta
COPY --from=builder --chown=harja:harja /build/target/uberjar/harja-0.0.1-SNAPSHOT-standalone.jar /app/harja.jar

# Aseta ENV muuttujat
# ENV JAVA_JAVA_OPTIONS=

# Aseta portti
EXPOSE 3000

# Aseta entrypoint
ENTRYPOINT ["java", "-jar", "/app/harja.jar"]
