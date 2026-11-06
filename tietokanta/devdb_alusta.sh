#!/usr/bin/env bash

# Ajaa migraatiot ja testidatan käynnissä olevaan harjadb-konttiin huoltotilassa.
#
# Huoltotilassa kantaan pääsevät vain kontin sisäiset yhteydet (unix socket ja 127.0.0.1), joita
# aja-migraatiot.sh ja aja-testidata.sh käyttävät. Hostilta Dockerin porttiohjauksen kautta tulevat yhteydet
# (esim. käynnissä oleva Harja REPL) estetään ja olemassa olevat katkaistaan. Huoltotilan päätyttyä
# REPL:n yhteyspooli (c3p0) muodostaa yhteydet automaattisesti uudelleen.
#
# Huom: tapahtumat-taulu tyhjenee, joten REPL:n tarkkailijat palautuvat vasta komennolla
#    Repl: (require 'harja.repl-tyokalut) (harja.repl-tyokalut/dev-restart)

set -euo pipefail

CONTAINER="${POSTGRESQL_NAME:-harjadb}"

aja_kontissa() {
    docker exec --user postgres "$CONTAINER" bash -c "$1"
}

psql_kontissa() {
    docker exec --user postgres "$CONTAINER" psql -d postgres -X -A -t -q -v ON_ERROR_STOP=1 -c "$1"
}

katkaise_ulkoiset_yhteydet() {
    local katkaistut
    katkaistut=$(psql_kontissa "
SELECT count(pg_terminate_backend(pid, 10000))
  FROM pg_stat_activity
 WHERE backend_type = 'client backend'
   AND pid         <> pg_backend_pid()
   AND client_addr <> '127.0.0.1';")
    echo "Katkaistiin ${katkaistut} ulkoista yhteyttä."
}

aloita_huoltotila() {
    echo "Aloitetaan huoltotila: estetään ulkoiset yhteydet kantaan."
    # Jos varmuuskopio on jo olemassa, edellinen ajo on keskeytynyt huoltotilaan. Säilytetään alkuperäinen hba_file.
    aja_kontissa '
    set -euo pipefail
    HBA="$(psql -d postgres -X -A -t -c "SHOW hba_file")"
    if [[ ! -f "${HBA}.ennen-huoltotilaa" ]]; then
      cp "$HBA" "${HBA}.ennen-huoltotilaa"
    fi
    cat > "$HBA" <<EOF
local all all              trust
host  all all 127.0.0.1/32 trust
host  all all 0.0.0.0/0    reject
EOF
    psql -d postgres -X -q -c "SELECT pg_reload_conf();" > /dev/null'

    # pg_reload_conf() on asynkroninen, joten katkaistaan yhteydet uudelleen hetken päästä siltä varalta,
    # että joku ehti yhdistää ennen kuin uudet säännöt tulivat voimaan.
    katkaise_ulkoiset_yhteydet
    sleep 2
    katkaise_ulkoiset_yhteydet
}

# flyway:clean tyhjentää kayttaja_anti_csrf_token-taulun, jolloin selaimen DOM:ssa oleva CSRF ei enää kelpaa
# ja kaikki pyynnöt palauttavat 403. Sessiot tallennetaan käyttäjänimen kanssa, koska kayttaja-id voi muuttua.
CSRF_SESSIOT=""

tallenna_csrf_sessiot() {
    local taulu_olemassa
    taulu_olemassa=$(docker exec --user postgres "$CONTAINER" psql -d harja -X -A -t -q -v ON_ERROR_STOP=1 \
        -c "SELECT to_regclass('public.kayttaja_anti_csrf_token') IS NOT NULL;")
    if [[ "$taulu_olemassa" != "t" ]]; then
        return
    fi
    CSRF_SESSIOT=$(docker exec --user postgres "$CONTAINER" psql -d harja -X -q -v ON_ERROR_STOP=1 -c "
COPY (SELECT k.kayttajanimi, t.anti_csrf_token, t.voimassa, t.luotu
        FROM kayttaja_anti_csrf_token t
             JOIN kayttaja k ON k.id = t.kayttaja_id)
  TO STDOUT WITH (FORMAT csv);")
    echo "Tallennettiin $(printf '%s' "$CSRF_SESSIOT" | grep -c '') CSRF-sessiota."
}

palauta_csrf_sessiot() {
    if [[ -z "$CSRF_SESSIOT" ]]; then
        return
    fi
    printf '%s\n' "$CSRF_SESSIOT" | docker exec -i --user postgres "$CONTAINER" psql -d harja -X -A -t -q -v ON_ERROR_STOP=1 \
        -c "CREATE TEMP TABLE csrf_palautus (kayttajanimi TEXT, anti_csrf_token TEXT, voimassa TIMESTAMP, luotu TIMESTAMP);" \
        -c "\copy csrf_palautus FROM pstdin WITH (FORMAT csv)" \
        -c "
  WITH lisatyt AS (
INSERT INTO kayttaja_anti_csrf_token (kayttaja_id, anti_csrf_token, voimassa, luotu)
SELECT k.id, p.anti_csrf_token, p.voimassa, p.luotu
  FROM csrf_palautus p
       JOIN kayttaja k ON k.kayttajanimi = p.kayttajanimi
       RETURNING 1)
SELECT format('Palautettiin %s CSRF-sessiota.', count(*))
  FROM lisatyt;"
}

lopeta_huoltotila() {
    echo "Lopetetaan huoltotila: sallitaan ulkoiset yhteydet kantaan."
    aja_kontissa '
    set -euo pipefail
    HBA="$(psql -d postgres -X -A -t -c "SHOW hba_file")"
    if [[ -f "${HBA}.ennen-huoltotilaa" ]]; then
      mv "${HBA}.ennen-huoltotilaa" "$HBA"
      psql -d postgres -X -q -c "SELECT pg_reload_conf();" > /dev/null
    fi'
}

# Palautetaan yhteydet myös silloin, jos skripti päättyy virheeseen.
trap lopeta_huoltotila EXIT
# Keskeytyksissä poistutaan signaalikoodeilla; EXIT-käsittelijä palauttaa yhteydet.
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

aloita_huoltotila
tallenna_csrf_sessiot

## Ajetaan migraatiot ja testidata tietokantaan huoltotilassa

# shellcheck disable=SC2088
docker exec --user postgres -e HARJA_TIETOKANTA_HOST=localhost -e HARJA_TIETOKANTA_PORTTI=5432 "$CONTAINER" /bin/bash -c "~/aja-migraatiot.sh"
# shellcheck disable=SC2088
docker exec --user postgres -e HARJA_TIETOKANTA_HOST=localhost -e HARJA_TIETOKANTA_PORTTI=5432 "$CONTAINER" /bin/bash -c "~/aja-testidata.sh"

palauta_csrf_sessiot

echo ""
echo "Migraatiot ja testidata ajettu onnistuneesti."
echo -e "Jos Harja REPL on käynnissä, palauta tarkkailijat toimintaan tarvittaessa komennolla (CLJ-replissä): \n
(require 'harja.repl-tyokalut) (harja.repl-tyokalut/dev-restart)."
