FROM solita/harjadb:centos-12

ENV POSTGRESQL_PORTTI=5433

RUN yum install -y git

WORKDIR /var/lib/pgsql

USER postgres

CMD sed -i "s/port = 5432/port = ${POSTGRESQL_PORTTI}/g" ~/${POSTGRESQL_VERSION:-12}/data/postgresql.conf; \
    sed -i "s/#port =/port =/g" ~/${POSTGRESQL_VERSION:-12}/data/postgresql.conf; \
    if [[ "${BUILD}" != "DEV" ]]; then bash hae-migraatiot-ja-testidata.sh; fi; \
    sed -i "s/localhost/localhost:${POSTGRESQL_PORTTI}/g" ~/harja/tietokanta/pom.xml; \
    pg_ctl start; \
    bash odota-kaynnistymista.sh; \
    bash aja-migraatiot.sh; \
    bash aja-testidata.sh; \
    echo "Vaihdetaan portti takaisin 5432"; \
    sed -i "s/port = ${POSTGRESQL_PORTTI}/port = 5432/g" ~/${POSTGRESQL_VERSION:-12}/data/postgresql.conf; \
    sed -i "s/localhost:${POSTGRESQL_PORTTI}/localhost/g" ~/harja/tietokanta/pom.xml; \
    pg_ctl restart; \
    /bin/bash;