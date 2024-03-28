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
aws s3api get-object --bucket "$HARJA_CONFIG_S3_BUCKET" --key "${HARJA_CONFIG_S3_KAISTAT_TIEDOSTOPOLKU}" uusin_tr_tieto.csv
echo "DONE"

tarkasta_riippuvuus uusin_tr_tieto.csv


## Asetukset.edn templaatti

echo "Ladataan asetukset.edn..."
aws s3api get-object --bucket "$HARJA_CONFIG_S3_BUCKET" --key "${HARJA_CONFIG_S3_ASETUKSET_TIEDOSTOPOLKU}" asetukset.edn
echo "DONE"

tarkasta_riippuvuus asetukset.edn

# Haetaan salaisuudet

## Hae lista salaisuuksista Secrets Managerista ja etsi niistä ne, jotka on tagattu Harja-sovelluksen salaisuuksiksi
SECRET_NAMES=$(aws secretsmanager list-secrets --query "SecretList[?Tags[?Key=='fargate:harja-app']].Name" --output text)

# Käy läpi salaisuuksien nimet loopissa ja hae niiden arvot
for SECRET_NAME in $SECRET_NAMES; do
    SECRET_VALUE=$(aws secretsmanager get-secret-value --secret-id "$SECRET_NAME" --query SecretString --output text)
    # Varmistetaan, että salaisuuden nimessä ei ole ylimääräistä. Esim. jos salaisuuden nimi olisi: moni/mutkaisempi/SALAISUUDEN_NIMI
    #   -> SALAISUUDEN_NIMI
    ENV_VAR_NAME=$(basename "$SECRET_NAME")

    # Aseta salaisuus ympäristömuuttujaksi
    export "$ENV_VAR_NAME"="$SECRET_VALUE"
done


# Valmistellaan java-optiot ja käynnistetään Harja app

## Luodaan java-optioille array, johon otetaan mukaan lisäoptioita mikäli ne on määritelty
cmd_opts=()

if [[ -n "$HARJA_JAVA_AGENT_OPTS" ]]; then
  IFS=' ' read -ra java_agents_arr <<< "$HARJA_JAVA_AGENT_OPTS"
  cmd_opts+=("${java_agents_arr[@]}")
fi

if [[ -n "$HARJA_JVM_OPTS" ]]; then
  # https://docs.aws.amazon.com/AmazonECS/latest/developerguide/taskdef-envfiles.html#taskdef-envfiles-considerations
  # "Spaces or quotation marks are included as part of the values for Amazon ECS files."
  # Poistetaan quotet, eli " tai ' ympäristömuuttujan alusta ja lopusta, mikäli sellaisia löytyy.
  HARJA_JVM_OPTS=$(sed 's/^["'\'']\(.*\)["'\'']$/\1/' <<< "$HARJA_JVM_OPTS")
  IFS=' ' read -ra jvm_opts_arr <<< "$HARJA_JVM_OPTS"
  cmd_opts+=("${jvm_opts_arr[@]}")
fi

cmd_opts+=("-cp" "$HARJA_LIBS":harja.jar harja.palvelin.main)

## Aja java-komento
echo "Käynnistetään java-sovellus..."
echo "Java CMD opts: ${cmd_opts[*]}"

java "${cmd_opts[@]}"
