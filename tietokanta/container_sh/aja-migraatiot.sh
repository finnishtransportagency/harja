#!/bin/bash
set -euo pipefail

if [[ ! $(whoami) = 'postgres' ]]
then
  su postgres
fi

cd ~/harja/tietokanta/
echo "Ajetaan migraatiot"
mvn -Dharja.tietokanta.host=${POSTGRESQL_NAME} flyway:migrate