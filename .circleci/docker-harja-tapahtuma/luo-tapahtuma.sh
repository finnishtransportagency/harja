#!/bin/bash
set -euo pipefail

FILE_NAME="$1"

if [[ "$FILE_NAME" =~ ^[a-zA-Z_-]+$ ]]
then
  touch "/usr/local/apache2/tapahtumat/${FILE_NAME}"
  echo "Tapahtui" > "/usr/local/apache2/tapahtumat/${FILE_NAME}"
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