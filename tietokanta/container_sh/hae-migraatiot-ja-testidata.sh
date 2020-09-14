#!/bin/bash
set -euo pipefail

if [[ ! $(whoami) = 'postgres' ]]
then
  su postgres
fi

cd ~

echo "Haetaan tietokannan tiedostot"
git clone https://github.com/finnishtransportagency/harja.git;
cd harja
git checkout ${BRANCH:-develop}
if [ "$COMMIT" != "_" ]
then
  git checkout $COMMIT .;
fi;