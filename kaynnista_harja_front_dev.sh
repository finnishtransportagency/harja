#!/bin/bash
set -euo pipefail

clean() {
  set +e # Jatka vaikka cleanin aikana tulee virheitä (background prosesseja ei ole)
  jobs -p | xargs -r kill # Tapa kaikki prosessit, mitä shell aloitti 
}

# Aja clean, kun tämä prosessi/shell suljetaan 
trap clean EXIT INT TERM

# Asenna depsut, jos ei ole 
lein deps
[ -d node_modules ] || npm ci

if [[ $# -eq 0 ]]
then
  ENV_PROFILE=false
else
  ENV_PROFILE=true
fi

# shellcheck source=../harja_dir.sh
source "$( dirname "${BASH_SOURCE[0]}" )/sh/harja_dir.sh" || exit

if [[ "$ENV_PROFILE" = "true" ]]
then
  # suorita 2 komentoa :
  # npm run less               =  aloittaa package.json npm skriptin nimeltä "less", joka on .less tarkkailu
  # lein trampoline build-dev  =  aloittaa frontti (.cljs) compilauksen 
  #
  # Aja nämä rinnaikkaisetsti (yhtäaikaa) käyttäen & 
  npm run less & 
  lein trampoline build-dev
else
  npm run less & 
  lein trampoline build-dev-no-env
fi
