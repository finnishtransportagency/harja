# Ohjeet imagen buildaamiseen ja ajaamiseen:
# 1. Build: docker build -t harjadb .
# 2. Run:
#   Tässä oletetaan, että olet Harjan projektihakemistossa ja haluat mountata harjan tietokanta-hakemiston containeriin.
#   Tietokanta-hakemisto sisältää pom.xml tiedoston ja migraatiotiedostot, joita tarvitaan tietokannan alustamiseen.
#   Itse image sisältää vain välttämättömimmät riippuvuudet ja pohjan tietokannalle.
#   Aja esimerkiksi: docker run --rm -p "127.0.0.1:5432:5432" -e HARJA_TIETOKANTA_PORTTI=5432 -v ./tietokanta/:/var/lib/postgresql/harja/tietokanta --name harjadb -d harjadb
# 3. Aja migraatiot ja testidata kantaan:
#  $ sudo docker exec --user postgres harjadb /bin/bash -c "~/aja-migraatiot.sh"
#  $ sudo docker exec --user postgres harjadb /bin/bash -c "~/aja-testidata.sh"

# Käytetään pohjana postgresql 13 ja postgis 3.1
# Käyttöjärjestelmänä on debian:bullseye (11)
# https://registry.hub.docker.com/r/postgis/postgis/

FROM postgres:13.12-bullseye

# Yhdistä image harjan repositoryyn
LABEL org.opencontainers.image.source https://github.com/finnishtransportagency/harja \
      org.opencontainers.image.description="PostGIS 3.4.0+dfsg-1.pgdg110+1 extension with PostgreSQL 13.12 bullseye"

# --- Asenna riippuvuudet --- #

# Asenna PostGIS
## https://tracker.debian.org/pkg/postgis
ENV POSTGIS_MAJOR=3
ENV POSTGIS_VERSION=3.4.0+dfsg-1.pgdg110+1

# Huom: JOS versiota 3.1 tarvitaan, huomioi seuraava asia:
# En saanut vanhempaa PostGIS 3.1.1-versiota toimimaan oikein.
# Virheilmoitus tulee siinä kohtaa, kun PostGIS laajennuksen ottaa käyttöön "CREATE EXTENSION postgis" kyselyllä:
# -> Failed with. ERROR: could not find function "gserialized_gist_sortsupport_2d" in file "/usr/lib/postgresql/13/lib/postgis-3.
#ENV POSTGIS_VERSION=3.1.1+dfsg-1+deb11u1


RUN apt-get update \
      && apt-cache showpkg postgresql-$PG_MAJOR-postgis-$POSTGIS_MAJOR \
      && apt-get install -y --no-install-recommends \
           # ca-certificates: for accessing remote raster files;
           #   fix: https://github.com/postgis/docker-postgis/issues/307
           ca-certificates \
           \
           postgresql-$PG_MAJOR-postgis-$POSTGIS_MAJOR=$POSTGIS_VERSION \
           postgresql-$PG_MAJOR-postgis-$POSTGIS_MAJOR-scripts \
      # Tulosta asennetun paketin tiedot
      && apt-cache policy postgresql-$PG_MAJOR-postgis-$POSTGIS_MAJOR

## Asenna muut riippuvuudet
RUN apt-get update && apt-get install -y openjdk-11-jdk wget \
    && rm -rf /var/lib/apt/lists/*


## Asenna maven
ENV MAVEN_VERSIO=3.9.4

RUN wget "https://downloads.apache.org/maven/maven-3/${MAVEN_VERSIO}/binaries/apache-maven-${MAVEN_VERSIO}-bin.tar.gz"; \
    if [ "$(sha512sum /apache-maven-${MAVEN_VERSIO}-bin.tar.gz )" != "deaa39e16b2cf20f8cd7d232a1306344f04020e1f0fb28d35492606f647a60fe729cc40d3cba33e093a17aed41bd161fe1240556d0f1b80e773abd408686217e  /apache-maven-${MAVEN_VERSIO}-bin.tar.gz" ]; then exit 1; fi; \
    tar xzvf "apache-maven-${MAVEN_VERSIO}-bin.tar.gz"; \
    rm "apache-maven-${MAVEN_VERSIO}-bin.tar.gz";

# Lisää maven bin kansio PATH:iin
ENV PATH="/apache-maven-${MAVEN_VERSIO}/bin/:${PATH}"


# --- Konfiguraatio --- #

## Postgresql konfiguraatio ##

# Aseta Harjaan liittyvät default ympäristömuuttujat
ENV HARJA_TIETOKANTA_PORTTI=5432

# Siirretään apuscriptit postgresl käyttäjän kotihakemistoon
WORKDIR /var/lib/postgresql/

ADD docker_aja-migraatiot.sh ./aja-migraatiot.sh
ADD docker_aja-testidata.sh ./aja-testidata.sh

RUN chmod +x aja-migraatiot.sh
RUN chmod +x aja-testidata.sh

USER postgres

# Initialisoidaan postgresql, jotta konfiguraatiotiedostot muodostuvat
# Samalla estetään base-imagen initialisointiscriptin ajaminen, koska data-hakemisto on jo olemassa
RUN initdb

# Konfiguroidaan postgresql kuuntelemaan kaikkia osoitteita ja porttia 5432
RUN sed -i "s/#listen_addresses.*/listen_addresses = '*'/g" /var/lib/postgresql/data/postgresql.conf
RUN sed -i "s/port = 5433/port = 5432/g" /var/lib/postgresql/data/postgresql.conf
RUN echo "local all all trust" > /var/lib/postgresql/data/pg_hba.conf
RUN echo "host    all             all             0.0.0.0/0            trust" >> /var/lib/postgresql/data/pg_hba.conf

# Konfiguroidaan postgresql käyttämään vähemmän muistia
# Fsync ja full_page_writes pois päältä, koska ne hidastavat tietokantaa
RUN sed -i "s/#fsync.*/fsync = off/g" /var/lib/postgresql/data/postgresql.conf
RUN sed -i "s/#full_page_writes.*/full_page_writes = off/g" /var/lib/postgresql/data/postgresql.conf
RUN sed -i "s/#shared_preload_libraries.*/shared_preload_libraries = 'pg_stat_statements'/g" /var/lib/postgresql/data/postgresql.conf

# Käynnistetään postgresql ja luodaan käyttäjät ja tietokannat
RUN pg_ctl start; \
    until pg_ctl status; \
    do \
      sleep 1; \
    done; \
    psql -c "CREATE USER harjatest WITH CREATEDB;" -U postgres && \
    psql -c "ALTER USER harjatest WITH SUPERUSER;" -U postgres && \
    psql -c "CREATE USER harja;" -U postgres && \
    psql -c "ALTER USER harja WITH SUPERUSER;" -U postgres && \
    psql -c "CREATE DATABASE harja OWNER harja;" -U postgres && \
    psql -c "CREATE DATABASE temp OWNER harjatest;" -U postgres && \
    psql -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO harjatest;" -U postgres && \
    psql -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO harja;" -U postgres  && \
    psql -c "CREATE EXTENSION postgis" -U postgres harja && \
    psql -c "CREATE EXTENSION postgis_topology" -U postgres harja && \
    psql -c "CREATE EXTENSION pg_trgm" -U postgres harja && \
    psql -c "CREATE EXTENSION pg_stat_statements" -U postgres harja \
    pg_ctl stop;

# Käynnistä postgresql ja pidä container käynnissä
# Jos ympäristömuuttujaa HARJA_TIETOKANTA_PORTTI ei ole asetettu, käytä oletusarvoa.
CMD sed -i "s/port = 5432/port = ${HARJA_TIETOKANTA_PORTTI}/g" ./data/postgresql.conf \
    && sed -i 's/#port =/port =/g' ./data/postgresql.conf \
    && pg_ctl start && tail -f /dev/null

