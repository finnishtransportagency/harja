#!/usr/bin/env bash
set -euo pipefail

#═══════════════════════════════════════════════════════════════════════════════
# HARJA GIT WORKTREE - POISTA WORKTREE
#═══════════════════════════════════════════════════════════════════════════════
#
# Tämä skripti poistaa olemassa olevan git worktreen turvallisesti.
#
# KÄYTTÖ:
#   sh/git-worktree/poista-worktree.sh <haara-nimi>
#
# ESIMERKKI:
#   sh/git-worktree/poista-worktree.sh HAR-1234-uusi-ominaisuus
#
# MITÄ SKRIPTI TEKEE:
#   1. Tarkistaa että worktree on olemassa
#   2. Kysyy vahvistuksen poistamiselle
#   3. Pysäyttää kaikki worktreen prosessit (backend, lein, Java)
#   4. Poistaa worktree-hakemiston
#   5. Siivoa git worktree -listan
#
# VAROITUS:
#   Kaikki tallentamattomat muutokset worktreessä menetetään!
#
# LISTAA WORKTREE:T:
#   git worktree list
#
# LUO UUSI WORKTREE:
#   sh/git-worktree/luo-uusi-worktree.sh <haara-nimi>
#
# TIETOKANTA (tärkeä huomio):
#   Worktree:t käyttävät oletuksena samaa lokaalista dev-tietokantaa.
#   Jos ajat useaa worktree:tä rinnakkain tai vaihdat branchia jossa on eri migraatiot,
#   saatat aiheuttaa migraatio-/data-konflikteja.
#
#   Jos worktree on luotu `luo-uusi-worktree.sh` skriptillä, worktreellä voi olla oma
#   tietokanta-kontti. Tämä skripti yrittää poistaa sen, jos `.tietokanta.env` löytyy.
#
#═══════════════════════════════════════════════════════════════════════════════

# Värit
VIHREA='\033[0;32m'
SININEN='\033[0;34m'
KELTAINEN='\033[1;33m'
PUNAINEN='\033[0;31m'
EI_VARIA='\033[0m'

usage() {
    echo "Käyttö: $0 <haara-nimi>"
    echo ""
    echo "Argumentit:"
    echo "  haara-nimi    Git-haaran nimi jonka worktreen haluat poistaa"
    echo ""
    echo "Esimerkki:"
    echo "  $0 HAR-1234-uusi-ominaisuus"
    echo ""
    echo "Listaa kaikki worktree:t:"
    echo "  git worktree list"
    exit 1
}

if [ $# -ne 1 ]; then
    usage
fi

HAARAN_NIMI="$1"

validoi_haaran_nimi() {
    local nimi="$1"

    if [ -z "$nimi" ]; then
        echo -e "${PUNAINEN}❌ Haaran nimi puuttuu${EI_VARIA}"
        exit 1
    fi

    if [[ "$nimi" =~ [[:space:]] ]]; then
        echo -e "${PUNAINEN}❌ Haaran nimi ei saa sisältää välilyöntejä: '$nimi'${EI_VARIA}"
        echo -e "${KELTAINEN}   Vinkki: käytä '-' tai '_' välilyöntien sijaan.${EI_VARIA}"
        exit 1
    fi
}

validoi_haaran_nimi "$HAARAN_NIMI"

# Määritä polut - käytä harja_dir.sh apuskriptiä
# shellcheck source=../harja_dir.sh
# shellcheck disable=SC1091
source "$( dirname "${BASH_SOURCE[0]}" )/../harja_dir.sh" || exit

PROJEKTIN_JUURI="$HARJA_DIR"
YLAKANSIO="$(dirname "$PROJEKTIN_JUURI")"

# Sanitoi haaran nimi
TURVALLINEN_HAARAN_NIMI=$(printf '%s' "$HAARAN_NIMI" | sed 's#[^[:alnum:]._-]#-#g')
WORKTREE_KANSIO="${YLAKANSIO}/harja-worktree-${TURVALLINEN_HAARAN_NIMI}"

varmista_turvallinen_polku() {
    local polku="$1"

    if [ -z "$polku" ]; then
        echo -e "${PUNAINEN}❌ Turvavirhe: tyhjä polku${EI_VARIA}"
        exit 1
    fi

    case "$polku" in
        "$YLAKANSIO"|"$YLAKANSIO/"|"/"|".")
            echo -e "${PUNAINEN}❌ Turvavirhe: epäilyttävä worktree-polku: $polku${EI_VARIA}"
            exit 1
            ;;
    esac

    case "$polku" in
        "$YLAKANSIO"/harja-worktree-*)
            ;;
        *)
            echo -e "${PUNAINEN}❌ Turvavirhe: worktree-polku ei näytä oikealta: $polku${EI_VARIA}"
            echo -e "${KELTAINEN}   Odotettu prefix: $YLAKANSIO/harja-worktree-${EI_VARIA}"
            exit 1
            ;;
    esac
}

varmista_turvallinen_polku "$WORKTREE_KANSIO"

echo -e "${SININEN}═══════════════════════════════════════════════════════════${EI_VARIA}"
echo -e "${SININEN}  Harja Git Worktree poistaminen${EI_VARIA}"
echo -e "${SININEN}═══════════════════════════════════════════════════════════${EI_VARIA}"
echo ""
echo -e "${KELTAINEN}Haara:${EI_VARIA}          $HAARAN_NIMI"
echo -e "${KELTAINEN}Worktree:${EI_VARIA}       $WORKTREE_KANSIO"
echo ""

# Tarkista onko worktree olemassa
if [ ! -d "$WORKTREE_KANSIO" ]; then
    echo -e "${PUNAINEN}❌ Worktree hakemistoa ei löydy: $WORKTREE_KANSIO${EI_VARIA}"
    echo ""
    echo -e "${KELTAINEN}Olemassa olevat worktree:t:${EI_VARIA}"
    git worktree list
    exit 1
fi

# Vahvista poisto
echo -e "${KELTAINEN}⚠️  Haluatko varmasti poistaa worktreen?${EI_VARIA}"
echo -e "${KELTAINEN}   Tämä poistaa hakemiston ja kaikki tallentamattomat muutokset!${EI_VARIA}"
echo ""
read -p "Jatka? [y/N] " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${SININEN}Peruttu.${EI_VARIA}"
    exit 0
fi

echo ""
echo -e "${SININEN}🐘 Poistetaan worktreen tietokanta (jos käytössä)...${EI_VARIA}"

if [ -f "$WORKTREE_KANSIO/.tietokanta.env" ]; then
    # shellcheck disable=SC1091
    source "$WORKTREE_KANSIO/.tietokanta.env"

    if [ -n "${POSTGRESQL_NAME:-}" ]; then
        if command -v docker >/dev/null 2>&1; then
            if docker inspect "$POSTGRESQL_NAME" >/dev/null 2>&1; then
                docker rm -f "$POSTGRESQL_NAME" >/dev/null 2>&1 || true
                echo -e "${VIHREA}✓ Poistettiin tietokanta-kontti: $POSTGRESQL_NAME${EI_VARIA}"
            else
                echo -e "${KELTAINEN}   (Tietokanta-konttia ei löydy: $POSTGRESQL_NAME)${EI_VARIA}"
            fi
        else
            echo -e "${KELTAINEN}⚠️  docker ei ole saatavilla tässä ympäristössä, ohitetaan.${EI_VARIA}"
        fi
    else
        echo -e "${KELTAINEN}   (POSTGRESQL_NAME puuttuu .tietokanta.env -tiedostosta, ohitetaan)${EI_VARIA}"
    fi
else
    echo -e "${KELTAINEN}   (.tietokanta.env puuttuu, ohitetaan)${EI_VARIA}"
fi

echo ""
echo -e "${SININEN}🛑 Pysäytetään worktreen prosessit...${EI_VARIA}"

tapa_prosessit_komentorivista() {
    local komento_regex="$1"
    local nimi="$2"

    local prosessit
    prosessit=$(ps ax -o pid= -o command= | awk -v polku="$WORKTREE_KANSIO" -v re="$komento_regex" 'index($0, polku) && $0 ~ re {print $1}' || true)

    if [ -n "$prosessit" ]; then
        echo -e "${KELTAINEN}   Tapetaan $nimi PID:t: $prosessit${EI_VARIA}"
        echo "$prosessit" | xargs kill -TERM 2>/dev/null || true
        sleep 2

        local elossa=""
        local pid
        for pid in $prosessit; do
            if kill -0 "$pid" 2>/dev/null; then
                elossa="$elossa $pid"
            fi
        done

        if [ -n "$elossa" ]; then
            echo -e "${KELTAINEN}   Pakotetaan lopetus (KILL) PID:t:$elossa${EI_VARIA}"
            # shellcheck disable=SC2086
            kill -KILL $elossa 2>/dev/null || true
        fi
    fi
}

yrita_fuser_k() {
    local polku="$1"

    if command -v fuser >/dev/null 2>&1; then
        fuser -k "$polku" 2>/dev/null || true
    else
        echo -e "${KELTAINEN}⚠️  fuser ei ole saatavilla tässä ympäristössä, ohitetaan.${EI_VARIA}"
    fi
}

# 1. Tarkista .backend.pid tiedosto
if [ -f "$WORKTREE_KANSIO/.backend.pid" ]; then
    BACKEND_PROSESSI_ID=$(cat "$WORKTREE_KANSIO/.backend.pid")
    echo -e "${KELTAINEN}   Tapetaan backend PID: $BACKEND_PROSESSI_ID${EI_VARIA}"
    kill "$BACKEND_PROSESSI_ID" 2>/dev/null || echo -e "${KELTAINEN}   (Backend ei ollut enää käynnissä)${EI_VARIA}"
    rm "$WORKTREE_KANSIO/.backend.pid"
fi

# 2. Etsi ja tapa lein-prosessit joiden komentorivillä näkyy worktree-polku
echo -e "${KELTAINEN}   Etsitään lein-prosesseja worktreestä...${EI_VARIA}"
tapa_prosessit_komentorivista "[[:space:]/]lein([[:space:]]|$)" "lein"

# 3. Etsi ja tapa Java-prosessit joiden komentorivillä näkyy worktree-polku
echo -e "${KELTAINEN}   Etsitään Java-prosesseja worktreestä...${EI_VARIA}"
tapa_prosessit_komentorivista "[[:space:]/]java([[:space:]]|$)" "Java"

# 4. Odota hetki että prosessit ehtivät sulkeutua
sleep 2

# 5. Vielä yksi tarkistus - onko hakemistoa käyttävää prosessia jäljellä?
JALJELLA_OLEVAT_PROSESSIT=$(lsof +D "$WORKTREE_KANSIO" 2>/dev/null | grep -v "COMMAND" || true)
if [ -n "$JALJELLA_OLEVAT_PROSESSIT" ]; then
    echo -e "${KELTAINEN}⚠️  Varoitus: Jotkin prosessit käyttävät vielä hakemistoa:${EI_VARIA}"
    echo "$JALJELLA_OLEVAT_PROSESSIT"
    echo ""
    echo -e "${KELTAINEN}Yritetään pakottaa poisto...${EI_VARIA}"
fi

# Poista worktree git:stä
echo -e "${SININEN}🗑️  Poistetaan worktree...${EI_VARIA}"
git worktree remove "$WORKTREE_KANSIO" --force 2>/dev/null || {
    echo -e "${KELTAINEN}⚠️  Git worktree remove epäonnistui, poistetaan manuaalisesti...${EI_VARIA}"
    
    # Pakota prosessien lopetus jos vielä jotain jäljellä
    yrita_fuser_k "$WORKTREE_KANSIO"
    sleep 1
    
    # Poista hakemisto
    rm -rf "$WORKTREE_KANSIO"
    
    # Siivoa git worktree -lista
    git worktree prune
}

# Varmista että hakemisto on poistettu
if [ -d "$WORKTREE_KANSIO" ]; then
    echo -e "${PUNAINEN}❌ Hakemisto on vielä olemassa, pakotetaan poisto...${EI_VARIA}"
    
    # Viimeinen yritys - pakota kaikki kiinni
    yrita_fuser_k "$WORKTREE_KANSIO"
    sleep 1
    
    # Poista raa'asti
    rm -rf "$WORKTREE_KANSIO" || {
        echo -e "${PUNAINEN}❌ Hakemiston poisto epäonnistui!${EI_VARIA}"
        echo -e "${KELTAINEN}Kokeile manuaalisesti: sudo rm -rf $WORKTREE_KANSIO${EI_VARIA}"
        exit 1
    }
fi

# Siivoa git worktree -lista
git worktree prune

echo ""
echo -e "${VIHREA}✅ Worktree poistettu onnistuneesti!${EI_VARIA}"
echo ""
echo -e "${KELTAINEN}Olemassa olevat worktree:t:${EI_VARIA}"
git worktree list
echo ""