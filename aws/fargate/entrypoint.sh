#!/usr/bin/env bash

# set -x

function tarkasta_riippuvuus {
    if [[ ! -f "$1" ]] ; then
        echo "Tiedostoa $1 ei löytynyt. Lopetetaan käynnistys..."
        exit 1
    fi
}

# Ladataan Harjan tarvitsemat staattiset tiedostot ja ylimääräiset kirjastot

## Sonic kirjaston jar-tiedostot
echo "Ladataan sonic-client.jar..."
aws s3api get-object --bucket "$HARJA_CONFIG_S3_BUCKET" --key "${HARJA_CONFIG_S3_SONIC_HAKEMISTO}/sonic-client.jar" sonic-client.jar
echo "DONE"

tarkasta_riippuvuus sonic-client.jar

echo "Ladataan sonic-xmessage.jar..."
aws s3api get-object --bucket "$HARJA_CONFIG_S3_BUCKET" --key "${HARJA_CONFIG_S3_SONIC_HAKEMISTO}/sonic-xmessage.jar" sonic-xmessage.jar
echo "DONE"

tarkasta_riippuvuus sonic-xmessage.jar

echo "Ladataan sonic-crypto.jar..."
aws s3api get-object --bucket "$HARJA_CONFIG_S3_BUCKET" --key "${HARJA_CONFIG_S3_SONIC_HAKEMISTO}/sonic-crypto.jar" sonic-crypto.jar
echo "DONE"

tarkasta_riippuvuus sonic-crypto.jar

## Uusin kaista-aineisto (csv)

echo "Ladataan uusin kaista-aineisto..."
aws s3api get-object --bucket "$HARJA_CONFIG_S3_BUCKET" --key "$HARJA_CONFIG_S3_KAISTAT_TIEDOSTOPOLKU" uusin_tr_tieto.csv
echo "DONE"

tarkasta_riippuvuus uusin_tr_tieto.csv

# Valmistellaan java-optiot ja käynnistetään Harja app

## Luodaan java-optioille array, johon otetaan mukaan lisäoptioita mikäli ne on määritelty
cmd_opts=()

if [[ -n "$HARJA_JAVA_AGENT" ]]; then
  cmd_opts+=("$HARJA_JAVA_AGENT")
fi

if [[ -n "$HARJA_JVM_OPTS" ]]; then
  cmd_opts+=("$HARJA_JVM_OPTS")
fi

cmd_opts+=("-cp" "$HARJA_LIBS":harja.jar harja.palvelin.main)

## Aja java-komento
echo "Käynnistetään java-sovellus..."

java "${cmd_opts[@]}"
