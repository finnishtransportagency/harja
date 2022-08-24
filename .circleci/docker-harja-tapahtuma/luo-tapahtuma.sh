#!/bin/bash
set -euo pipefail

FILE_NAME="$(echo "${QUERY_STRING}" | sed -r 's/tapahtuma=([^&]*)&?.*/\1/')"

ARGS=()
VANHA_IFS=$IFS
IFS="&"
read -r -a ARGS <<< "$(echo "${QUERY_STRING}" | sed -r 's/tapahtuma=[^&]*&?(.*)/\1/')"
IFS=$VANHA_IFS
# Poistetaan dublikaatit
read -r -a ARGS <<< "$(printf "%s " "${ARGS[@]}" | sort -u)"

if [[ ! "${QUERY_STRING}" =~ tapahtuma= ]]
then
  VIRHETEKSTI="Status: 422 Ei löydetty query parametreista arvoa argumentille 'tapahtuma'."
  printf "Content-type: text/html\n\n";
  # shellcheck disable=SC2059
  printf "${VIRHETEKSTI}" "${FILE_NAME}";
  exit 1
fi

if [[ "$FILE_NAME" =~ ^[a-zA-Z0-9_-]+$ ]]
then
  touch -f "/usr/local/apache2/tapahtumat/${FILE_NAME}"
  truncate -s 0 "/usr/local/apache2/tapahtumat/${FILE_NAME}"
  if [[ ${#ARGS[@]} -ne 0 ]]
  then
    for ARG in "${ARGS[@]}"
    do
      if [[ "$ARG" =~ ^[a-zA-Z0-9_-]+=[a-zA-Z0-9_-]+$ ]]
      then
        echo "$ARG" >> "/usr/local/apache2/tapahtumat/${FILE_NAME}"
      else
        rm "/usr/local/apache2/tapahtumat/${FILE_NAME}"
        VIRHETEKSTI="Status: 422 Tapahtuman ( %s ) luonti epäonnistui. \
Tapahtuman argumentit pitää olla muodossa 'NIMI=ARVO', jossa \
'NIMI' sekä 'ARVO' saavat sisältää vain pieniä ja isoja kirjaimia (a-z), sekä \
merkkejä '_' ja '-'"
        printf "Content-type: text/html\n\n";
        # shellcheck disable=SC2059
        printf "${VIRHETEKSTI}" "${FILE_NAME}";
        exit 1
      fi
    done
  fi
  printf "Content-type: text/html\n\n";
  printf "tapahtuma luotu";
else
  VIRHETEKSTI="Status: 422 Tapahtuman ( %s ) luonti epäonnistui. \
Tapahtuma saa sisältää vain pieniä ja isoja kirjaimia (a-z), sekä \
merkkejä '_' ja '-'"
  printf "Content-type: text/html\n\n";
  # shellcheck disable=SC2059
  printf "${VIRHETEKSTI}" "${FILE_NAME}";
fi