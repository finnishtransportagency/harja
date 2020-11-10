#!/bin/bash
set -euo pipefail

if [[ ! $(whoami) = 'postgres' ]]
then
  cd ~;
else
  # shellcheck disable=SC2016
  POSTGRES_HOME="$(runuser -c 'echo $HOME' -l postgres)";
  cd "$POSTGRES_HOME"
fi

echo "Haetaan tietokannan tiedostot"
git clone https://github.com/finnishtransportagency/harja.git;
cd harja
git checkout "${BRANCH:-develop}"
if [ "$COMMIT" != "_" ]
then
  git checkout "$COMMIT" .;
fi;