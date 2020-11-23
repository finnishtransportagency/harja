#!/bin/bash
set -euo pipefail

echo "Tapahtuma"
ALKUPERAINEN_TAPAHTUMA="$1"
TAPAHTUMA="tapahtuma=$(echo "${ALKUPERAINEN_TAPAHTUMA}" | sed 's/\./_/g')"
shift;

if [[ $# -ne 0 ]]
then
  echo "Args"
  ARGS="&$1"
  shift;
else
  ARGS=""
fi

if [[ $# -ne 0 ]]
then
  echo "Palvelin"
  PALVELIN="$1"
  shift;
else
  PALVELIN="localhost"
fi

if [[ $# -ne 0 ]]
then
  echo "Portti"
  PORTTI="$1"
  shift;
else
  PORTTI=80
fi

echo "Luodaan tmp tiedosto"
touch tmp_vastaus_tiedosto__

echo "Tehdään kutsu urliin '${PALVELIN}:${PORTTI}/luo-tapahtuma?${TAPAHTUMA}${ARGS}'"
LUONTI_KOODI="$(curl -s -o tmp_vastaus_tiedosto__ -w '%{http_code}' "${PALVELIN}:${PORTTI}/luo-tapahtuma?${TAPAHTUMA}${ARGS}")"
echo "LUONTI_KOODI: $LUONTI_KOODI"

if [[ 200 -ne "${LUONTI_KOODI}" ]]
then
  echo "Tapahtuman ${ALKUPERAINEN_TAPAHTUMA} luonti epäonnistui."
  if [[ 422 -eq "${LUONTI_KOODI}" ]]
  then
    cat tmp_vastaus_tiedosto__
  fi
  rm tmp_vastaus_tiedosto__
  exit 1
else
  rm tmp_vastaus_tiedosto__
fi