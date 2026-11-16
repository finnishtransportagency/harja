#!/bin/bash
set -euo pipefail

if [[ $(whoami) = 'postgres' ]]
then
  cd ~;
else
  # shellcheck disable=SC2016
  POSTGRES_HOME="$(runuser -c 'echo $HOME' -l postgres)";
  cd "$POSTGRES_HOME"
fi

cd harja/tietokanta/
echo "Ajetaan kaikki migraatiot."
## Vanha tapa ajaa migraatio siten, että jatketaan viimeksi ajetusta migraatiosta.
##mvn -Dharja.tietokanta.port="${HARJA_TIETOKANTA_PORTTI:-5432}" -Dharja.tietokanta.host="${HARJA_TIETOKANTA_HOST:-localhost}" flyway:migrate

## -- Aja kaikki migraatiot uusiksi --

## Lataa flyway-plugin ja sen riippuvuudet ennen tietokannan muuttamista.
## Komentoa retrytetään, mikäli esimerkiksi flyway-pluginin lataus epäonnistuu verkkovirheen vuoksi.
##  -Dflyway.skip=true: Maven resolvoi pluginin, sen riippuvuudet ja projektin riippuvuudet,
##                      mutta Flyway ei suorita 'goalia', eli tässä :info, eikä ota yhteyttä tietokantaan.
##  -U: Ohittaa paikalliseen repoon (.lastUpdated) cachetetut epäonnistuneet lataukset.
##      Ilman tätä retry ei yritä latausta uudelleen, vaan kaatuu heti samaan virheeseen.
odotusajat=(5 15)
for yritys in 1 2 3; do
  echo "Ladataan Flyway-plugin ja sen riippuvuudet (yritys $yritys/3)."
  if mvn -B -U -Dflyway.skip=true flyway:info; then
    break
  else
    virhekoodi=$?
  fi

  if [[ "$yritys" -eq 3 ]]; then
    echo "Flyway-pluginin lataus epäonnistui. Tietokantoja ei muutettu." >&2
    exit "$virhekoodi"
  fi

  odotus="${odotusajat[$((yritys - 1))]}"
  echo "Flyway-pluginin lataus epäonnistui. Yritetään uudelleen $odotus sekunnin kuluttua." >&2
  sleep "$odotus"
done

psql -h localhost -p "${HARJA_TIETOKANTA_PORTTI:-5432}" -U harja harja -c "DROP DATABASE IF EXISTS harjatest_template;"
psql -h localhost -p "${HARJA_TIETOKANTA_PORTTI:-5432}" -U harja harja -c "DROP DATABASE IF EXISTS harjatest;"

## Puhdista kanta
mvn -Dharja.tietokanta.port="${HARJA_TIETOKANTA_PORTTI:-5432}" -Dharja.tietokanta.host="${HARJA_TIETOKANTA_HOST:-localhost}" \
 flyway:clean

## Aja migraatiot alusta
mvn -Dharja.tietokanta.port="${HARJA_TIETOKANTA_PORTTI:-5432}" -Dharja.tietokanta.host="${HARJA_TIETOKANTA_HOST:-localhost}" \
-Dflyway.baselineVersion="0" -Dflyway.baselineOnMigrate="true" -Dflyway.table="schema_version" \
 flyway:migrate

echo "Migraatio ajettu."
