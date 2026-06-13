#!/bin/bash
#
# Käynnistä Harja frontend-kehitysympäristö (Figwheel + LESS-käännös)
#
# KÄYTTÖ:
#   ./kaynnista_harja_front_dev.sh              # Ilman ympäristöasetuksia (nopea)
#   ./kaynnista_harja_front_dev.sh jotain        # Ympäristöasetuksilla (asetukset.edn)
#
# YMPÄRISTÖMUUTTUJAT:
#   FRONTEND_REPL_PORT  Figwheelin ring-server portti (oletus: 3449)
#                       Hyödyllinen worktree-käytössä porttikollisioiden välttämiseksi
#
set -euo pipefail

# Siivoa taustaprosessit (npm run less) kun skripti lopetetaan
clean() {
  set +e # Jatka vaikka cleanin aikana tulee virheitä (background prosesseja ei ole)
  jobs -p | xargs -r kill # Tapa kaikki prosessit, mitä shell aloitti 
}

# Aja clean, kun tämä prosessi/shell suljetaan 
trap clean EXIT INT TERM

# Asenna depsut, jos ei ole 
lein deps
[ -d node_modules ] || npm ci

echo "Kopioidaan Bootstrap tyylit..."
mkdir -p resources/public/css
# cp node_modules/@tabler/core/dist/css/tabler.min.css resources/public/css/
cp node_modules/@tabler/icons-webfont/dist/tabler-icons.min.css resources/public/css/
cp -r node_modules/@tabler/icons-webfont/dist/fonts resources/public/css/
cp node_modules/@tabler/core/dist/css/tabler-vendors.min.css resources/public/css/

# Nykyaikaisempiin front featureihin 
mkdir -p dev-resources/js/out
cp node_modules/bootstrap/dist/js/bootstrap.bundle.min.js dev-resources/js/out/

# Kopioidaan tässä myös tuotoantobuildille, jos ajetaan uberjarria lokaalisti
mkdir -p resources/public/js/out
cp node_modules/bootstrap/dist/js/bootstrap.bundle.min.js resources/public/js/out/


# Päätä käytetäänkö dev-ympäristöprofiilia (lataa asetukset.edn)
# - Ilman argumenttia: build-dev-no-env (nopea, ei lataa .edn-asetuksia)
# - Argumentilla: +dev-ymparisto profile (lataa asetukset, hitaampi käynnistys)
if [[ $# -eq 0 ]]
then
  ENV_PROFILE=false
else
  ENV_PROFILE=true
fi

# shellcheck source=../harja_dir.sh
source "$( dirname "${BASH_SOURCE[0]}" )/sh/harja_dir.sh" || exit

cd "$HARJA_DIR"

# Figwheel-konffi dynaamisella portilla (worktree-tuki)
# Jos FRONTEND_REPL_PORT asetettu, generoi konffi jossa ring-server-portti yliajettu.
# Välttää 3449-porttikollisiot kun useita Figwheel-instansseja ajetaan rinnakkain.
# Generoitu tiedosto: figwheel_conf/luodut/dev-portti.cljs.edn (ei soti worktreeiden kanssa)
# Esim: FRONTEND_REPL_PORT=3455 bash ./kaynnista_harja_front_dev.sh
FIGWHEEL_BUILD_KONFFI=""
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

  FIGWHEEL_BUILD_KONFFI="figwheel_conf/luodut/dev-portti"
fi

# Käynnistä frontend-kehitysympäristö:
 # npm run less =  aloittaa package.json npm skriptin nimeltä "less", joka on .less tarkkailu
# lein trampoline build-dev  =  aloittaa frontti (.cljs) compilauksen 
# - Figwheel: ClojureScript-kääntäjä + hot-reload dev-server
if [[ "$ENV_PROFILE" = "true" ]]
then
  # +dev-ymparisto profiililla: lataa asetukset.edn (tietokanta-asetukset yms)
  npm run less & 
  if [[ -n "$FIGWHEEL_BUILD_KONFFI" ]]; then
    lein trampoline with-profile +dev-ymparisto with-env-vars run -m figwheel.main -b "$FIGWHEEL_BUILD_KONFFI" -r
  else
    lein trampoline build-dev
  fi
else
  # Ilman profiilia: nopeampi käynnistys, ei lataa asetuksia
  npm run less & 
  if [[ -n "$FIGWHEEL_BUILD_KONFFI" ]]; then
    lein trampoline run -m figwheel.main -b "$FIGWHEEL_BUILD_KONFFI" -r
  else
    lein trampoline build-dev-no-env
  fi
fi
