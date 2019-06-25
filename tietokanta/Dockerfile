FROM ubuntu:xenial

# Asennetaan riippuvuudet
RUN apt-get update
RUN apt-get install -y --no-install-recommends postgresql-9.5 postgresql-9.5-postgis-2.2 postgresql-contrib-9.5 maven

# Konffataan pg kuuntelemaan verkkoa
RUN sed -i "s/#listen_addresses.*/listen_addresses = '*'/g" /etc/postgresql/9.5/main/postgresql.conf
RUN sed -i "s/port = 5433/port = 5432/g" /etc/postgresql/9.5/main/postgresql.conf
RUN echo "local all all trust" > /etc/postgresql/9.5/main/pg_hba.conf
RUN echo "host    all             all             0.0.0.0/0            trust" >> /etc/postgresql/9.5/main/pg_hba.conf

# Konffataan fsync ja full page writes pois
RUN sed -i "s/#fsync.*/fsync = off/g" /etc/postgresql/9.5/main/postgresql.conf
RUN sed -i "s/#full_page_writes.*/full_page_writes = off/g" /etc/postgresql/9.5/main/postgresql.conf
RUN sed -i "s/#shared_preload_libraries.*/shared_preload_libraries = 'pg_stat_statements'/g" /etc/postgresql/9.5/main/postgresql.conf

# Luodaan kanta
RUN service postgresql restart && \
    sleep 5 && \
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
    psql -c "CREATE EXTENSION pg_stat_statements" -U postgres harja

# Ajetaan migraatiot
COPY pom.xml /tmp
COPY src /tmp/src
RUN service postgresql start && cd /tmp && sleep 20 && mvn flyway:migrate && service postgresql stop

# Siivotaan sotkut ja minifioidaan image
RUN apt-get -y autoremove;
RUN rm -R /tmp/src

# Käynnistyskomento
CMD service postgresql start && /bin/bash
