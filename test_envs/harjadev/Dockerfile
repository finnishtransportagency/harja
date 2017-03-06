FROM ubuntu:xenial

#RUN wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | apt-key add -
#RUN sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt/ precise-pgdg main 9.5" >> /etc/apt/sources.list.d/postgresql.list'

RUN apt-get update
RUN apt-get -y install postgresql-9.5
RUN apt-get -y install postgresql-9.5-postgis-2.2
RUN apt-get -y install openjdk-8-jre
RUN apt-get -y install maven

RUN echo "Konffataan postgres"

# Konffataan pg kuuntelemaan verkkoa
RUN sed -i "s/#listen_addresses.*/listen_addresses = '*'/g" /etc/postgresql/9.5/main/postgresql.conf
RUN sed -i "s/port = 5433/port = 5432/g" /etc/postgresql/9.5/main/postgresql.conf
RUN echo "local all all trust" > /etc/postgresql/9.5/main/pg_hba.conf
RUN echo "host    all             all             0.0.0.0/0            trust" >> /etc/postgresql/9.5/main/pg_hba.conf

RUN service postgresql restart && \
    sleep 5 && \
    psql -c "CREATE USER harjatest WITH CREATEDB;" -U postgres && \
    psql -c "CREATE ROLE harja;" -U postgres && \
    psql -c "ALTER USER harjatest WITH SUPERUSER;" -U postgres && \
    psql -c "CREATE DATABASE temp OWNER harjatest;" -U postgres && \
    psql -c "CREATE DATABASE harjatest_template OWNER harjatest;" -U postgres && \
    psql -c "CREATE EXTENSION postgis" -U postgres harjatest_template && \
    psql -c "CREATE EXTENSION postgis_topology" -U postgres harjatest_template && \
    psql -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO harjatest;" -U postgres

# Kopioidaan harjan sovellus ja konffi
RUN mkdir /opt/harja
COPY target/harja-0.0.1-SNAPSHOT-standalone.jar /opt/harja/harja.jar
COPY asetukset.edn /opt/harja/asetukset.edn

# Luodaan kanta flyway:lla ja ajetaan testidata
COPY tietokanta /opt/tietokanta
RUN service postgresql start && cd /opt/tietokanta && mvn -Ptravis flyway:migrate && service postgresql stop

CMD service postgresql start && /bin/bash
