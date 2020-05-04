FROM centos:7

# Asennetaan riippuvuudet
RUN yum install -y https://download.postgresql.org/pub/repos/yum/reporpms/EL-7-x86_64/pgdg-redhat-repo-latest.noarch.rpm; \
    yum install -y postgresql95; \
    yum install -y postgresql95-server; \
    yum install -y https://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm; \
    yum install -y postgis25_95; \
    yum install -y java-11-openjdk sudo wget which;

RUN wget https://downloads.apache.org/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz; \
    if [ "$(sha512sum /apache-maven-3.6.3-bin.tar.gz )" != "c35a1803a6e70a126e80b2b3ae33eed961f83ed74d18fcd16909b2d44d7dada3203f1ffe726c17ef8dcca2dcaa9fca676987befeadc9b9f759967a8cb77181c0  /apache-maven-3.6.3-bin.tar.gz" ]; then exit 1; fi; \
    tar xzvf apache-maven-3.6.3-bin.tar.gz;

ENV PATH="/usr/pgsql-9.5/bin:/apache-maven-3.6.3/bin/:${PATH}"
ENV PGDATA="/var/lib/pgsql/data/9.5"
USER postgres

RUN initdb /var/lib/pgsql/data/9.5/;

# Konffataan pg kuuntelemaan verkkoa
RUN sed -i "s/#listen_addresses.*/listen_addresses = '*'/g" /var/lib/pgsql/data/9.5/postgresql.conf
RUN sed -i "s/port = 5433/port = 5432/g" /var/lib/pgsql/data/9.5/postgresql.conf
RUN echo "local all all trust" > /var/lib/pgsql/data/9.5/pg_hba.conf
RUN echo "host    all             all             0.0.0.0/0            trust" >> /var/lib/pgsql/data/9.5/pg_hba.conf

# Konffataan fsync ja full page writes pois
RUN sed -i "s/#fsync.*/fsync = off/g" /var/lib/pgsql/data/9.5/postgresql.conf
RUN sed -i "s/#full_page_writes.*/full_page_writes = off/g" /var/lib/pgsql/data/9.5/postgresql.conf
RUN sed -i "s/#shared_preload_libraries.*/shared_preload_libraries = 'pg_stat_statements'/g" /var/lib/pgsql/data/9.5/postgresql.conf

## Luodaan kanta
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

# Ajetaan migraatiot
COPY pom.xml /tmp
COPY src /tmp/src
RUN pg_ctl start; \
    until pg_ctl status; \
    do \
      sleep 1; \
    done; \
    cd /tmp; \
    mvn flyway:migrate; \
    pg_ctl stop;

USER root
# Siivotaan
RUN rm -rf /tmp/src

## Käynnistyskomento
CMD sudo -iu postgres /usr/pgsql-9.5/bin/pg_ctl start -D "/var/lib/pgsql/data/9.5"; \
    /bin/bash