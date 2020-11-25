#!/bin/bash
set -euo pipefail

if [[ $(whoami) = 'postgres' ]]
then
  cd ~;
else
  # shellcheck disable=SC2016
  POSTGRES_HOME="$(runuser -c 'echo $HOME' -l postgres)";
  cd "$POSTGRES_HOME"
fi

cd harja/tietokanta/
echo "Ajetaan migraatiot"
mvn -Dharja.tietokanta.port="${HARJA_TIETOKANTA_PORTTI:-5432}" -Dharja.tietokanta.host="${HARJA_TIETOKANTA_HOST:-localhost}" flyway:migrate