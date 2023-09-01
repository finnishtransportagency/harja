#!/usr/bin/env bash

# ./build_image.sh [true] [auto]

# Exit on any error or an unset variable (use optionally -x to print each command)
set -Eeu

# Buildaa ilman cachea, jotta esim. uusin chrome tulee ladattua joka buildin yhteydessä (default: true)
CLEAN_BUILD=${1:-true}

# Kuinka build progressin lokit printataan (default: auto)
#   Käytetään defaulttina 'plain' lokitusta, jotta nähdään mitä versioita imagen build asentaa paketeista
#   auto = Tiivistetty
#   plain = Vanhanmallinen, tulostaa kaiken (esim. echot)
PROGRESS=${2:-plain}

cmd_opts=()

if [[ "$CLEAN_BUILD" = "true" ]]; then
  cmd_opts+=("--no-cache")
fi

cmd_opts+=("--progress=${PROGRESS}")

echo "Buildataan ja tagataan image..."

# Asennetaan samat versiot cypressin tarvitsemista NPM-paketeista, kuin on käytössä paikallisessa kehityksessäkin
readonly NPM_CYPRESS_VERSION=$(node -pe 'require("../../../package").dependencies.cypress')

if [[ -z "$NPM_CYPRESS_VERSION" ]]; then
    echo "Cypressin NPM-paketin versiota ei löytynyt. Lopetetaan build..."
    exit 1
fi

docker build -t ghcr.io/finnishtransportagency/harja_cypress:latest \
--build-arg="NPM_CYPRESS_VERSION=${NPM_CYPRESS_VERSION}" \
"${cmd_opts[@]}" .

echo "Build valmis."
