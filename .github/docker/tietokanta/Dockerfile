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

# https://docs.aws.amazon.com/AmazonRDS/latest/AuroraPostgreSQLReleaseNotes/AuroraPostgreSQL.Extensions.html#AuroraPostgreSQL.Extensions.15
# Muuta materiaalia:
# https://github.com/postgis/docker-postgis
# https://postgis.net/docs/manual-3.3/postgis_installation.html#install_requirements
ARG POSTGIS_VERSION=3.3.3


#-------------------------------#
#----- Rakenna pohja-image -----#
#-------------------------------#

# https://hub.docker.com/_/postgres/tags
FROM postgres:13.13-bullseye as base
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
      clang \
      cmake \
      g++ \
      git \
      llvm-dev \
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
ENV MAVEN_VERSIO=3.9.6
# https://downloads.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz.sha512
ENV MAVEN_SHA512="706f01b20dec0305a822ab614d51f32b07ee11d0218175e55450242e49d2156386483b506b3a4e8a03ac8611bae96395fd5eec15f50d3013d5deed6d1ee18224"

RUN apt-get update && apt-get install -y openjdk-17-jdk \
    && wget "https://downloads.apache.org/maven/maven-3/${MAVEN_VERSIO}/binaries/apache-maven-${MAVEN_VERSIO}-bin.tar.gz" \
    && if [ "$MAVEN_SHA512" != "$(sha512sum apache-maven-${MAVEN_VERSIO}-bin.tar.gz | awk '{print $1}')" ]; then \
               echo "Downloaded Maven SHA512: $(sha512sum apache-maven-${MAVEN_VERSIO}-bin.tar.gz | awk '{print $1}')"; \
               echo "Maven package sha512 values don't match! exiting."; \
               exit 1; \
           fi \
    && tar xzvf "apache-maven-${MAVEN_VERSIO}-bin.tar.gz" \
    && rm "apache-maven-${MAVEN_VERSIO}-bin.tar.gz"

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

