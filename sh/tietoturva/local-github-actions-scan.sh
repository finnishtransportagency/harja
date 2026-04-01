#!/usr/bin/env bash

# https://docs.zizmor.sh/
# Paikallinen Zizmor-skannaus .github/actions ja .github/workflows -hakemistoille.
# Zizmor on staattinen analyysityökalu GitHub Actions-workflowien skannaamiseksi.
# Se voi löytää ja korjata monia yleisiä tietoturvaongelmia tyypillisissä GH Actions-konfiguraatioissa.
# HUOM: Koska työkalu on staattinen analyysityökalu, se ei pysty ymmärtämään koodin avulla generoituja asioita, eikä
#       sillä ole pääsyä ajonaikaiseen tietoon. Siksi osa ongelmista saattaa jäädä huomaamatta.
#       Se ei myöskään analysoi muita tiedostoja, joihin workflowit tai actionit saattavat viitata, kuten skriptejä tai konfiguraatiotiedostoja.

# Aja --help nähdäksesi käyttöohjeet.

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# Rajoitetaan skannattavat kohteet vain .github-hakemistoon
# Työkalun ei ole tarpeen päästä käsiksi projektin muihin osiin
GITHUB_DIR="$PROJECT_DIR/.github/"
ZIZMOR_IMAGE="ghcr.io/zizmorcore/zizmor:latest"
# .github hakemiston alla olevat kohteet, jotka halutaan skannata.
ZIZMOR_TARGETS=".github/workflows/ .github/actions/ .github/dependabot.yml"

help() {
    echo "Käyttö: $0 [OPTIONS] [-- ZIZMOR_OPTIONS...]"
    echo ""
    echo "Optiot:"
    echo "  --fix=safe          Suorittaa vain turvalliset automaattikorjaukset."
    echo "                      Nämä eivät muuta workflowien toiminnallisuutta."
    echo "  --fix=unsafe-only   Suorittaa VAIN vaaralliset korjaukset, jotka voivat"
    echo "                      rikkoa workfloweja tai actioneja. Tarkista muutokset"
    echo "                      huolellisesti ennen committia!"
    echo "  -h, --help          Näytä tämä ohjeteksti."
    echo ""
    echo "Kaikki '--' jälkeen annetut optiot välitetään suoraan Zizmorille."
    echo "Oletuksena käytetään --persona=pedantic."
    echo ""
    echo "Esimerkkejä:"
    echo "  $0                                Skannaus pedantic-personalla (default)"
    echo "  $0 -- --persona=auditor           Skannaus auditor-personalla"
    echo "  $0 -- --format=json               Skannaus JSON-outputilla"
    exit 0
}

pull_image() {
    docker pull "$ZIZMOR_IMAGE"
}

run_zizmor() {
    # Jos ei olla fix-tilassa, mountataan read-only
    # Tämä lisää tietoturvaa, koska skannauksessa ei tarvita kirjoitusoikeuksia.
    if [[ -z "$FIX_MODE" ]]; then
        mount_opts=",ro"
    fi

    # Disabloidaan SC2086, koska haluamme välittää inputit word-splitattuina
    # Käytetään kuitenkin "--", jotta Zizmor ei tulkitse inputteja optioina.
    # shellcheck disable=SC2086
    # Zizmorille täytyy luoda ".github"-hakemisto workdiriin, koska se olettaa sen olevan olemassa.
    docker run --rm \
        --mount "type=bind,src=$GITHUB_DIR,dst=/workdir/.github${mount_opts}" \
        --workdir "/workdir" \
        "$ZIZMOR_IMAGE" \
        "$@" \
        --collect=workflows,actions,dependabot \
        --config ".github/zizmor.yml" \
        -- \
        $ZIZMOR_TARGETS
}

run_scan() {
    echo -e "🔍 Suoritetaan skannaus...\n"
    run_zizmor "${EXTRA_OPTS[@]}"
}

run_fix() {
    local fix_mode="$1"

    if [[ "$fix_mode" == "unsafe-only" ]]; then
        echo "⚠️  VAROITUS: Suoritetaan vaaralliset korjaukset!"
        echo "   Nämä korjaukset voivat rikkoa workfloweja tai actioneja."
        echo "   Tarkista KAIKKI muutokset huolellisesti ennen committia!"
        echo ""
        read -rp "Haluatko jatkaa? (y/N): " confirm
        if [[ "$confirm" != [yY] ]]; then
            echo "Keskeytetty."
            exit 0
        fi
    else
        echo "🔧 Suoritetaan turvalliset automaattikorjaukset..."
        echo "   Nämä korjaukset eivät muuta workflowien toiminnallisuutta."
    fi

    run_zizmor --fix="$fix_mode" "${EXTRA_OPTS[@]}"
}

# Parsi komentorivioptiot
FIX_MODE=""
EXTRA_OPTS=(--persona=pedantic)

while [[ "$#" -gt 0 ]]; do
    case "$1" in
    --fix=safe)
        FIX_MODE="safe"
        shift
        ;;
    --fix=unsafe-only)
        FIX_MODE="unsafe-only"
        shift
        ;;
    -h | --help)
        help
        ;;
    --)
        shift
        EXTRA_OPTS=("$@")
        break
        ;;
    *)
        echo "Tuntematon optio: $1"
        echo ""
        help
        ;;
    esac
done

pull_image

echo ""
echo -e "Optiot: FIX_MODE='$FIX_MODE', EXTRA_OPTS='${EXTRA_OPTS[*]}'"
echo -e "Skannataan kohteet hakemistossa: $GITHUB_DIR"
echo -e "Skannattavat kohteet: ${ZIZMOR_TARGETS}\n"

if [[ -n "$FIX_MODE" ]]; then
    run_fix "$FIX_MODE"
else
    run_scan
fi
