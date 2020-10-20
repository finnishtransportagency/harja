#!/bin/bash
set -euo pipefail

if [[ -z "$USER" ]]
then
  KAYTTAJA="$(whoami)"
else
  KAYTTAJA="$USER"
fi

# shellcheck source=../harja_dir.sh
source "$( dirname "${BASH_SOURCE[0]}" )/../harja_dir.sh" || exit

## Tehdään tässä vielä varmistus, että ollaan jossain päin kotikansiota
# shellcheck disable=SC2116
echo "${HARJA_DIR}" | grep "$(echo ~)" || {
  echo "Ei ole oikeanlainen HARJA_DIR: ${HARJA_DIR}";
  exit;
   }
echo "Muutetaan oikeudet kansiossa ${HARJA_DIR}"

sudo find "${HARJA_DIR}" -user root -exec chown "$KAYTTAJA":"$KAYTTAJA" {} \; ;