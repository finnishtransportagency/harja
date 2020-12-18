FROM solita/harjadb:postgis-3.1

RUN yum install -y git

WORKDIR /var/lib/pgsql

USER postgres

CMD ALKUPERAINEN_HARJA_TIETOKANTA_PORTTI="${HARJA_TIETOKANTA_PORTTI:-5432}"; \
    export HARJA_TIETOKANTA_PORTTI=5321; \
    sed -i "s/port = 5432/port = ${HARJA_TIETOKANTA_PORTTI}/g" ~/${POSTGRESQL_VERSION:-12}/data/postgresql.conf; \
    sed -i "s/#port =/port =/g" ~/${POSTGRESQL_VERSION:-12}/data/postgresql.conf; \
    if [[ "${POSTGRESQL_LOG_ALL}" = "true" ]]; then echo "log_statement = all" >>  ~/${POSTGRESQL_VERSION:-12}/data/postgresql.conf; fi; \
    if [[ "${BUILD}" != "DEV" ]]; then bash hae-migraatiot-ja-testidata.sh; fi; \
    pg_ctl start; \
    bash odota-kaynnistymista.sh; \
    bash aja-migraatiot.sh; \
    bash aja-testidata.sh; \
    echo "Vaihdetaan portti takaisin alkuperäiseen"; \
    sed -i "s/port = ${HARJA_TIETOKANTA_PORTTI}/port = ${ALKUPERAINEN_HARJA_TIETOKANTA_PORTTI}/g" ~/${POSTGRESQL_VERSION:-12}/data/postgresql.conf; \
    pg_ctl restart; \
    /bin/bash;
