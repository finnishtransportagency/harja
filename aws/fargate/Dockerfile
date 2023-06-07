# -- Build vaihe --
# Käytetään build vaiheessa mahdollisimman kevyttä alpine linuxia ja openjdk 11
FROM clojure:temurin-11-lein-2.10.0-alpine as builder

# Luo build hakemisto
RUN mkdir -p /build
WORKDIR /build

# Asenna git gitlogin generointia varten
# TODO: Tutkitaan tarvitaanko gitlogin generointia enää Harjan about-sivua varten
RUN apk add --no-cache git

# Cacheta riippuvuudet
COPY project.clj /build/
RUN lein deps

# Kopioi lähdekoodi ja resurssit (Ignorataan epärelevantit tiedostot .dockerignore:ssa)
# Kopioimalla lähdekoodin eriin overlayhin kuin riippuvuudet, varmistetaan että muutokset lähdekoodissa eivät aiheuta
# riippuvuuksien uudelleen lataamista
COPY . /build/

# Tee uberjar tuotantoa varten
# TODO: Gitlogia käytetään vain about-sivulla ja sen compile on hidasta. Tarvitaanko tätä ollenkaan enää?
#RUN lein do gitlog, compile
# Käännä backend
RUN lein do compile

# Käännä frontend (less, cljs)
#   Tarvitsee yhteyden PostgreSQL tietokantaan Specql interospektiota varten
#   Tietokannassa on oltava viimeisimmät migraatiot ajettuna
#   Docker buildillevoi antaa --network host parametrin, jolloin se voi käyttää hostin tietokantaa
RUN lein do less once, with-profile prod-cljs compile-prod

# Käännä laadunseuranta (TODO: Enabloi käännös myöhemmin)
#   Tarvitsee yhteyden PostgreSQL tietokantaan Specql interospektiota varten
#   Tietokannassa on oltava viimeisimmät migraatiot ajettuna
#   Docker buildille voi antaa --network host parametrin, jolloin se voi käyttää hostin tietokantaa
#RUN lein do with-profile laadunseuranta-prod compile-laadunseuranta-prod

# Luo uberjar ja dokumentaatio
RUN lein do uberjar, codox


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

# Kopioi uberjar build vaiheesta
COPY --from=builder --chown=harja:harja /build/target/harja-0.0.1-SNAPSHOT-standalone.jar harja.jar
COPY ./aws/fargate/asetukset.edn asetukset.edn

# Harja-app tarkastaa tällä hetkellä jotkin asetetut ENV muuttujat .harja_env tiedoston mukaisesti
# harja.palvelin.asetukset/tarkista-ymparisto! -funktiolla
# TODO: Tarvitaaanko tätä enää?
COPY ./aws/fargate/.harja_env .harja_env

# Aseta ENV muuttujat
# TODO: Tarkista mitkä JAVA optionsit tarvitaan
# ENV JAVA_JAVA_OPTIONS=

# Aseta portti
EXPOSE 3000

# Aseta entrypoint
ENTRYPOINT ["java", "-jar", "harja.jar"]
