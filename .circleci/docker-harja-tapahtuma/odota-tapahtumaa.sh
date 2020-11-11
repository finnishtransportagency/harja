#!/bin/bash
set -euo pipefail

if [[ $# -ne 3 ]]
then
  echo "Väärä määrä argumenttejä."
  echo "Saatiin ${#} määrä argumentteja (${*})"
  echo "Ensimmäisen pitäisi olla TAPAHTUMA"
  echo "Toisen pitäisi olla PALVELIN"
  echo "Kolmannen pitäisi olla PORTTI"
  exit 1
fi

ALKUPERAINEN_TAPAHTUMA="$1"
TAPAHTUMA="$(echo "${ALKUPERAINEN_TAPAHTUMA}" | sed 's/\./_/g')"
PALVELIN="$2"
PORTTI="$3"

TIMEOUT=60
ODOTETTU=0

touch tapahtuman_arvot

while [[ "$(curl -s -o tapahtuman_arvot -w '%{http_code}' "${PALVELIN}:${PORTTI}/${TAPAHTUMA}" 2>&1)" != '200' ]]
do
   echo "Odotetaan tapahtumaa ${ALKUPERAINEN_TAPAHTUMA}..."
   sleep 1
   ODOTETTU=$((ODOTETTU+1))
   if [[ $ODOTETTU -eq $TIMEOUT ]]
   then
     echo "Tapahtuma ${ALKUPERAINEN_TAPAHTUMA} ei tapahtunut ${TIMEOUT} sekunnin sisällä. Lopetetaan kuuntelu"
     rm tapahtuman_arvot
     exit 1
   fi
done