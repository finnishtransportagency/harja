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

cd "$HARJA_DIR"

FRONTEND_BUILD=""
if [[ -n "${FRONTEND_REPL_PORT:-}" ]]; then
  if [[ ! "${FRONTEND_REPL_PORT}" =~ ^[0-9]+$ ]]; then
    echo "❌ FRONTEND_REPL_PORT ei ole numero: '${FRONTEND_REPL_PORT}'" >&2
    exit 1
  fi

  echo "🧩 FRONTEND_REPL_PORT asetettu: ${FRONTEND_REPL_PORT}"
  echo "   Generoidaan Figwheel-konffi (portti yliajettu): figwheel_conf/luodut/dev-portti.cljs.edn"

  mkdir -p "$HARJA_DIR/figwheel_conf/luodut"
  lein run -m harja.tyokalut.figwheel-konffi \
    "$HARJA_DIR/figwheel_conf/dev.cljs.edn" \
    "$HARJA_DIR/figwheel_conf/luodut/dev-portti.cljs.edn" \
    "$FRONTEND_REPL_PORT"

  FRONTEND_BUILD="figwheel_conf/luodut/dev-portti"
fi

if [[ "$ENV_PROFILE" = "true" ]]
then
  # suorita 2 komentoa :
  # npm run less               =  aloittaa package.json npm skriptin nimeltä "less", joka on .less tarkkailu
  # lein trampoline build-dev  =  aloittaa frontti (.cljs) compilauksen 
  #
  # Aja nämä rinnaikkaisetsti (yhtäaikaa) käyttäen & 
  npm run less & 
  if [[ -n "$FRONTEND_BUILD" ]]; then
    lein trampoline with-profile +dev-ymparisto with-env-vars run -m figwheel.main -b "$FRONTEND_BUILD" -r
  else
    lein trampoline build-dev
  fi
else
  npm run less & 
  if [[ -n "$FRONTEND_BUILD" ]]; then
    lein trampoline run -m figwheel.main -b "$FRONTEND_BUILD" -r
  else
    lein trampoline build-dev-no-env
  fi
fi
