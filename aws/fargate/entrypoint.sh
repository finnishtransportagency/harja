#!/usr/bin/env bash
set -x

# Ladataan Harjan tarvitsemat staattiset tiedostot ja ylimääräiset kirjastot

## Sonic kirjaston jar-tiedostot
echo "Ladataan sonic-client.jar..."
aws s3api get-object --bucket "$HARJA_CONFIG_S3_BUCKET" --key "${HARJA_CONFIG_S3_SONIC_HAKEMISTO}/sonic-client.jar" sonic-client.jar
echo "DONE"

echo "Ladataan /sonic-xmessage.jar..."
aws s3api get-object --bucket "$HARJA_CONFIG_S3_BUCKET" --key "${HARJA_CONFIG_S3_SONIC_HAKEMISTO}/sonic-xmessage.jar" sonic-xmessage.jar
echo "DONE"

echo "Ladataan sonic-crypto.jar..."
aws s3api get-object --bucket "$HARJA_CONFIG_S3_BUCKET" --key "${HARJA_CONFIG_S3_SONIC_HAKEMISTO}/sonic-crypto.jar" sonic-crypto.jar
echo "DONE"

## Uusin kaista-aineisto (csv)

echo "Ladataan uusin kaista-aineisto..."
aws s3api get-object --bucket "$HARJA_CONFIG_S3_BUCKET" --key "$HARJA_CONFIG_S3_KAISTAT_TIEDOSTOPOLKU" uusin_tr_tieto.csv
echo "DONE"

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

echo "Käynnistetään harja.jar"
## Aja java-komento
java "${cmd_opts[@]}"
