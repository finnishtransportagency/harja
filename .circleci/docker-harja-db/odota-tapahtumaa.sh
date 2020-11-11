#!/bin/bash
set -euo pipefail

if [[ $# -ne 3 ]]
then
  echo "Väärä määrä argumenttejä."
  echo "Ensimmäisen pitäisi olla TAPAHTUMA"
  echo "Toisen pitäisi olla PALVELIN"
  echo "Kolmannen pitäisi olla PORTTI"
  exit 1
fi

ALKUPERAINEN_TAPAHTUMA="$1"
TAPAHTUMA="$(echo "${ALKUPERAINEN_TAPAHTUMA}" | sed 's/\./_/g')"
PALVELIN="$2"
PORTTI="$3"

while [[ "$(curl -s -o /dev/null -w '%{http_code}' "${PALVELIN}:${PORTTI}/${TAPAHTUMA}" 2>&1)" != '200' ]]
do
   echo "Odotetaan tapahtumaa ${ALKUPERAINEN_TAPAHTUMA}..."
   sleep 1
done