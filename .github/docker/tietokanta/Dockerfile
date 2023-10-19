# Ohjeet imagen buildaamiseen ja ajaamiseen:
# 1. Build: Lue .github/docker/README.md
# 2. Run:
#   Tässä oletetaan, että olet Harjan projektihakemistossa ja haluat mountata harjan tietokanta-hakemiston containeriin.
#   Tietokanta-hakemisto sisältää pom.xml tiedoston ja migraatiotiedostot, joita tarvitaan tietokannan alustamiseen.
#   Itse image sisältää vain välttämättömimmät riippuvuudet ja pohjan tietokannalle.
#   Aja esimerkiksi: docker run --rm -p "127.0.0.1:5432:5432" -e HARJA_TIETOKANTA_PORTTI=5432 -v ./tietokanta/:/var/lib/postgresql/harja/tietokanta --name harjadb -d harjadb
# 3. Aja migraatiot ja testidata kantaan:
#  $ sudo docker exec --user postgres harjadb /bin/bash -c "~/aja-migraatiot.sh"
#  $ sudo docker exec --user postgres harjadb /bin/bash -c "~/aja-testidata.sh"
#
# Muuta materiaalia: https://github.com/postgis/docker-postgis

# https://hub.docker.com/_/postgres/tags
ARG POSTGRES_IMAGE_VERSION=15.3-bullseye
# https://docs.aws.amazon.com/AmazonRDS/latest/AuroraPostgreSQLReleaseNotes/AuroraPostgreSQL.Extensions.html#AuroraPostgreSQL.Extensions.13
ARG POSTGIS_VERSION=3.3.2


#-------------------------------#
#----- Rakenna pohja-image -----#
#-------------------------------#

FROM postgres:${POSTGRES_IMAGE_VERSION} as base
# https://docs.docker.com/engine/reference/builder/#understand-how-arg-and-from-interact
ARG POSTGIS_VERSION

# --- Asenna riippuvuudet --- #

# Asenna työkaluja
RUN apt-get update && apt-get install -y wget


RUN wget "https://download.osgeo.org/postgis/source/postgis-${POSTGIS_VERSION}.tar.gz" \
    && tar -xvzf "postgis-${POSTGIS_VERSION}.tar.gz" \
    && rm "postgis-${POSTGIS_VERSION}.tar.gz"

# Asenna PostGISin buildaamiseen vaadittavat riippuvuudet (https://docs.docker.com/build/cache/#use-the-dedicated-run-cache)
RUN --mount=type=cache,target=/var/cache/apt \
    set -ex \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
      libgdal-dev libgeos-dev libproj-dev \
      postgresql-server-dev-$PG_MAJOR \
      autoconf \
      automake \
      autotools-dev \
      bison \
      build-essential \
      ca-certificates \
      cmake \
      g++ \
      git \
      libboost-all-dev \
      libcgal-dev \
      libcurl4-gnutls-dev \
      libgmp-dev \
      libjson-c-dev \
      libmpfr-dev \
      libpcre3-dev \
      libprotobuf-c-dev \
      libsqlite3-dev \
      libtiff-dev \
      libtool \
      libxml2-dev \
      make \
      pkg-config \
      protobuf-c-compiler \
      xsltproc

# Buildaa PostGIS
RUN  cd postgis-${POSTGIS_VERSION} \
        && ./configure \
        && make clean \
        # Anna Makelle saatavilla olevat prosessointiyksiköt
        && make -j$(nproc) \
        && make install


## Asenna maven
ENV MAVEN_VERSIO=3.9.4
RUN apt-get update && apt-get install -y openjdk-17-jdk \
    && wget "https://downloads.apache.org/maven/maven-3/${MAVEN_VERSIO}/binaries/apache-maven-${MAVEN_VERSIO}-bin.tar.gz"; \
        if [ "$(sha512sum /apache-maven-${MAVEN_VERSIO}-bin.tar.gz )" != "deaa39e16b2cf20f8cd7d232a1306344f04020e1f0fb28d35492606f647a60fe729cc40d3cba33e093a17aed41bd161fe1240556d0f1b80e773abd408686217e  /apache-maven-${MAVEN_VERSIO}-bin.tar.gz" ]; then exit 1; fi; \
        tar xzvf "apache-maven-${MAVEN_VERSIO}-bin.tar.gz"; \
        rm "apache-maven-${MAVEN_VERSIO}-bin.tar.gz";

# Lisää maven bin kansio PATH:iin
ENV PATH="/apache-maven-${MAVEN_VERSIO}/bin/:${PATH}"

# Siivous
RUN apt-get -y --purge autoremove  \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*


#---------------------------------#
#---- Harjadb-imagen rakennus ----#
#---------------------------------#
FROM base

# Yhdistä image harjan repositoryyn
LABEL org.opencontainers.image.source=https://github.com/finnishtransportagency/harja
LABEL org.opencontainers.image.description="PostGIS 3.3.2 extension with PostgreSQL 13.11 bullseye"

# Aseta Harjaan liittyvät default ympäristömuuttujat
ENV HARJA_TIETOKANTA_PORTTI=5432

# Siirretään apuscriptit postgresl käyttäjän kotihakemistoon
WORKDIR /var/lib/postgresql/

ADD docker_aja-migraatiot.sh ./aja-migraatiot.sh
ADD docker_aja-testidata.sh ./aja-testidata.sh

RUN chmod +x aja-migraatiot.sh
RUN chmod +x aja-testidata.sh


## Postgresql konfiguraatio ##

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
    \
    # Configuroi harja-database
    psql -c "CREATE USER harjatest WITH CREATEDB;" -U postgres && \
    psql -c "ALTER USER harjatest WITH SUPERUSER;" -U postgres && \
    psql -c "CREATE USER harja;" -U postgres && \
    psql -c "ALTER USER harja WITH SUPERUSER;" -U postgres && \
    psql -c "CREATE DATABASE harja OWNER harja;" -U postgres && \
    psql -c "CREATE DATABASE temp OWNER harjatest;" -U postgres && \
    psql -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO harjatest;" -U postgres && \
    psql -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO harja;" -U postgres  && \
    \
    # Asenna lisäosat harja-databaseen
    psql -c "CREATE EXTENSION postgis" -U postgres harja && \
    psql -c "CREATE EXTENSION postgis_topology" -U postgres harja && \
    psql -c "CREATE EXTENSION pg_trgm" -U postgres harja && \
    psql -c "CREATE EXTENSION pg_stat_statements" -U postgres harja && \
    \
    # Tulosta PostgreSQL:n ja PostGIS:in versiot
    psql -c "SELECT version()" -U postgres harja && \
    psql -c "SELECT postgis_full_version()" -U postgres harja && \
    \
    pg_ctl stop;

# Käynnistä postgresql ja pidä container käynnissä
# Jos ympäristömuuttujaa HARJA_TIETOKANTA_PORTTI ei ole asetettu, käytä oletusarvoa.
CMD sed -i "s/port = 5432/port = ${HARJA_TIETOKANTA_PORTTI}/g" ./data/postgresql.conf \
    && sed -i 's/#port =/port =/g' ./data/postgresql.conf \
    && pg_ctl start && tail -f /dev/null

