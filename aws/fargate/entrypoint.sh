#!/usr/bin/env bash
set -x

# Ladataan Harjan tarvitsemat staattiset tiedostot ja ylimääräiset kirjastot

## Sonic kirjaston jar-tiedostot
aws s3api get-object --bucket "$HARJA_CONFIG_S3_BUCKET" --key "${HARJA_CONFIG_S3_SONIC_HAKEMISTO}/sonic-client.jar" sonic-client.jar
aws s3api get-object --bucket "$HARJA_CONFIG_S3_BUCKET" --key "${HARJA_CONFIG_S3_SONIC_HAKEMISTO}/sonic-xmessage.jar" sonic-xmessage.jar
aws s3api get-object --bucket "$HARJA_CONFIG_S3_BUCKET" --key "${HARJA_CONFIG_S3_SONIC_HAKEMISTO}/sonic-crypto.jar" sonic-crypto.jar

## Uusin kaista-aineisto (csv)
aws s3api get-object --bucket "$HARJA_CONFIG_S3_BUCKET" --key "$HARJA_CONFIG_S3_KAISTAT_TIEDOSTOPOLKU" uusin_tr_tieto.csv


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
java "${cmd_opts[@]}"
