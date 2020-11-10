#!/bin/bash
set -euo pipefail

ALKUPERAINEN_TAPAHTUMA="$1"
TAPAHTUMA="$(echo "${ALKUPERAINEN_TAPAHTUMA}" | sed 's/\./_/g')"
shift;

if [[ $# -ne 0 ]]
then
  PALVELIN="$1"
else
  PALVELIN="localhost"
fi

if [[ $# -ne 0 ]]
then
  PORTTI="$1"
else
  PORTTI=80
fi

touch tmp_vastaus_tiedosto__

LUONTI_KOODI="$(curl -s -o tmp_vastaus_tiedosto__ -w '%{http_code}' "${PALVELIN}:${PORTTI}/luo-tapahtuma?${TAPAHTUMA}")"

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