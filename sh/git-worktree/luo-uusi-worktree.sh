#!/usr/bin/env bash

# Huom: Tämä skripti vaatii bashin.
# Jos käyttäjä ajaa sen vahingossa sh:lla, bash-spesifi syntaksi (esim. process substitution) kaatuu epäselvästi.
# Tarkistus on tarkoituksella POSIX-yhteensopiva, jotta se toimii myös /bin/sh:lla.
if [ -z "${BASH_VERSION-}" ]; then
    echo "❌ Tämä skripti vaatii bashin (sitä ei voi ajaa sh:lla)." >&2
    echo "   Käytä joko: bash sh/git-worktree/luo-uusi-worktree.sh <haara-nimi> [portti]" >&2
    echo "   tai:        ./sh/git-worktree/luo-uusi-worktree.sh <haara-nimi> [portti]" >&2
    exit 2
fi

set -euo pipefail

#═══════════════════════════════════════════════════════════════════════════════
# HARJA GIT WORKTREE - LUO UUSI WORKTREE
#═══════════════════════════════════════════════════════════════════════════════
#
# Tämä skripti luo uuden git worktreen Harja-projektille, joka mahdollistaa
# useiden haarojen samanaikaisen kehityksen omissa hakemistoissaan.
#
# KÄYTTÖ:
#   bash sh/git-worktree/luo-uusi-worktree.sh <haara-nimi> [portti]
#   ./sh/git-worktree/luo-uusi-worktree.sh <haara-nimi> [portti]
#
# ESIMERKIT:
#   bash sh/git-worktree/luo-uusi-worktree.sh HAR-1234-uusi-ominaisuus
#   bash sh/git-worktree/luo-uusi-worktree.sh HAR-1234-uusi-ominaisuus 3005
#   ./sh/git-worktree/luo-uusi-worktree.sh HAR-1234-uusi-ominaisuus
#   ./sh/git-worktree/luo-uusi-worktree.sh HAR-1234-uusi-ominaisuus 3005
#
# MITÄ SKRIPTI TEKEE:
#   1. Luo uuden git worktreen annetulle haaralle
#   2. Asentaa npm-riippuvuudet worktreehen
#   3. Tarkistaa migraatioiden erot ja varoittaa tarvittaessa
#   4. Luo käynnistysskriptin (kaynnista-kaikki.sh) worktreehen
#   5. Tarjoaa mahdollisuuden käynnistää worktree heti
#
# WORKTREE SIJAINTI:
#   ../harja-worktree-<haara-nimi>/
#
# HTTP-PORTTI:
#   - Jos määritetty: käyttää annettua porttia
#   - Jos haara tukee worktreeta: etsii vapaan portin 3001-3020
#   - Jos haara ei tue worktreeta: käyttää porttia 3000
#
# FRONTEND REPL -PORTTI (Figwheel):
#   - Jos haara tukee FRONTEND_REPL_PORT-asetusta: etsii vapaan portin 3449-3499
#   - Muuten: käyttää porttia 3449
#
# KÄYNNISTYS:
#   cd ../harja-worktree-<haara-nimi>
#   ./kaynnista-kaikki.sh
#
# POISTO:
#   sh/git-worktree/poista-worktree.sh <haara-nimi>
#
# TIETOKANTA (tärkeä huomio):
#   Tämä skripti varaa worktree:lle oman PostgreSQL-kontin ja oman host-portin.
#   Tämä vähentää migraatio-/data-konflikteja, kun useita worktree:tä ajetaan rinnakkain.
#
#   Tietokanta-portti:
#     - Etsitään vapaa portti rangesta 5433-5499
#
#   Kontin nimi:
#     - Muodostetaan worktree-haaran nimestä (POSTGRESQL_NAME)
#
#═══════════════════════════════════════════════════════════════════════════════

# Värit
VIHREA='\033[0;32m'
SININEN='\033[0;34m'
KELTAINEN='\033[1;33m'
PUNAINEN='\033[0;31m'
EI_VARIA='\033[0m'

# Funktio vapaan portin etsimiseen
etsi_vapaa_portti() {
    local alku_portti=3001
    local loppu_portti=3020
    
    for portti in $(seq $alku_portti $loppu_portti); do
        # Tarkista onko portti vapaana (ei kuuntele mitään)
        if ! lsof -Pi :"$portti" -sTCP:LISTEN -t >/dev/null 2>&1; then
            echo "$portti"
            return 0
        fi
    done
    
    # Jos kaikki portit varattu, palauta virhe
    echo ""
    return 1
}

etsi_vapaa_frontend_repl_portti() {
    local alku_portti=3449
    local loppu_portti=3499

    for portti in $(seq $alku_portti $loppu_portti); do
        if ! lsof -Pi :"$portti" -sTCP:LISTEN -t >/dev/null 2>&1; then
            echo "$portti"
            return 0
        fi
    done

    echo ""
    return 1
}

etsi_vapaa_tietokanta_portti() {
    local alku_portti=5433
    local loppu_portti=5499

    for portti in $(seq $alku_portti $loppu_portti); do
        if ! lsof -Pi :"$portti" -sTCP:LISTEN -t >/dev/null 2>&1; then
            echo "$portti"
            return 0
        fi
    done

    echo ""
    return 1
}

etsi_vapaa_backend_nrepl_portti() {
    # Oletus nREPL-portti on 4005 (profiles.clj). Worktree-ajossa tarvitaan vapaa portti.
    local alku_portti=4006
    local loppu_portti=4099

    for portti in $(seq $alku_portti $loppu_portti); do
        if ! lsof -Pi :"$portti" -sTCP:LISTEN -t >/dev/null 2>&1; then
            echo "$portti"
            return 0
        fi
    done

    echo ""
    return 1
}

tukeeko_dynaamista_porttia() {
    local worktree_kansio="$1"

    if [ -d "$worktree_kansio/sh/git-worktree" ]; then
        return 0
    fi

    if [ -f "$worktree_kansio/asetukset.edn" ] && grep -q "HARJA_HTTP_PORTTI" "$worktree_kansio/asetukset.edn" 2>/dev/null; then
        return 0
    fi

    return 1
}

tukeeko_dynaamista_frontend_repl_porttia() {
    local worktree_kansio="$1"

    if [ -f "$worktree_kansio/src/clj-dev/harja/tyokalut/figwheel_konffi.clj" ]; then
        return 0
    fi

    return 1
}

portti_varattu() {
    local portti="$1"
    lsof -Pi :"$portti" -sTCP:LISTEN -t >/dev/null 2>&1
}

paivita_worktree_profiles_nrepl_portti() {
    local worktree_kansio="$1"
    local profiles_polku="$worktree_kansio/profiles.clj"

    if [ ! -f "$profiles_polku" ]; then
        return 0
    fi

    # Jos worktree-haarassa on jo tuki env-portille, älä koske.
    if grep -q 'System/getenv "HARJA_NREPL_PORTTI"' "$profiles_polku" 2>/dev/null; then
        return 0
    fi

    # Vain jos löytyy kiinteä 4005-linja (yleinen konfliktin syy, kun päärepo kuuntelee 4005:ssä).
    if grep -qE '^[[:space:]]*:port[[:space:]]+4005[[:space:]]*$' "$profiles_polku" 2>/dev/null; then
        local tmp
        tmp="$(mktemp)"

        sed -E 's|^([[:space:]]*):port[[:space:]]+4005[[:space:]]*$|\1:port #=(eval (let [p (System/getenv "HARJA_NREPL_PORTTI")] (if (and p (re-matches #"[0-9]+" p)) (Integer/parseInt p) 4005)))|' \
            "$profiles_polku" > "$tmp"

        mv "$tmp" "$profiles_polku"
            if grep -q 'System/getenv "HARJA_NREPL_PORTTI"' "$profiles_polku" 2>/dev/null; then
                echo -e "${VIHREA}✓ Päivitettiin profiles.clj: nREPL-portti tukee HARJA_NREPL_PORTTI${EI_VARIA}"
                return 0
            fi
    fi

        echo -e "${PUNAINEN}❌ Worktree-haaran profiles.clj ei tue HARJA_NREPL_PORTTI-asetusta eikä sitä voitu päivittää turvallisesti.${EI_VARIA}"
        echo -e "${KELTAINEN}   Päivitä haaraan profiles.clj-muutos tai muuta nREPL-portin määritys käsin ennen käynnistystä.${EI_VARIA}"
        return 1
}

# Funktio kysymään tietokannan uudelleenkäynnistyksestä
kysy_tietokannan_uudelleenkaynnistys() {
    local worktree_kansio="$1"
    local tietokanta_portti="$2"
    local postgresql_name="$3"

    echo -e "${KELTAINEN}Suositus: Rakenna worktree:n tietokanta uudelleen ennen käynnistystä:${EI_VARIA}"
    echo ""
    echo -e "${SININEN}   docker rm -f \"${postgresql_name}\"${EI_VARIA}"
    echo -e "${SININEN}   HARJA_TIETOKANTA_PORTTI=${tietokanta_portti} POSTGRESQL_NAME=${postgresql_name} ${worktree_kansio}/tietokanta/devdb_up.sh${EI_VARIA}"
    echo ""
    echo -e "${KELTAINEN}Oletko jo ajanut tietokannan uudelleen? [y/N]${EI_VARIA}"
    read -p "" -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${KELTAINEN}💡 Muista ajaa komennot ennen worktreen käynnistystä!${EI_VARIA}"
        echo ""
    else
        echo -e "${VIHREA}✓ Hyvä, jatketaan...${EI_VARIA}"
        echo ""
    fi
}

usage() {
    echo "Käyttö: $0 <haara-nimi> [portti]"
    echo ""
    echo "Argumentit:"
    echo "  haara-nimi    Git-haaran nimi jota haluat tarkastella"
    echo "  portti        Valinnainen HTTP-palvelimen portti (oletus: automaattinen 3001-3020)"
    echo ""
    echo "Esimerkki:"
    echo "  $0 HAR-1234-uusi-ominaisuus"
    echo "  $0 HAR-1234-uusi-ominaisuus 3002"
    exit 1
}

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

varmista_turvallinen_polku() {
    local polku="$1"

    if [ -z "$polku" ]; then
        echo -e "${PUNAINEN}❌ Turvavirhe: tyhjä polku${EI_VARIA}"
        exit 1
    fi

    # Estä vaarallisten polkujen käyttö
    case "$polku" in
        "$YLAKANSIO"|"$YLAKANSIO/"|/|.)
            echo -e "${PUNAINEN}❌ Turvavirhe: epäilyttävä worktree-polku: $polku${EI_VARIA}"
            exit 1
            ;;
    esac

    # Varmista että polku alkaa oikealla tavalla
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

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    usage
fi

# Määritä polut - käytä harja_dir.sh apuskriptiä
# shellcheck source=../harja_dir.sh
# shellcheck disable=SC1091
source "$( dirname "${BASH_SOURCE[0]}" )/../harja_dir.sh" || exit
HAARAN_NIMI="$1"
validoi_haaran_nimi "$HAARAN_NIMI"
PROJEKTIN_JUURI="$HARJA_DIR"
YLAKANSIO="$(dirname "$PROJEKTIN_JUURI")"

# Jos portti on annettu, käytä sitä. Muuten päätetään myöhemmin worktreen luonnin jälkeen
if [ $# -eq 2 ]; then
    HTTP_PORTTI="$2"
else
    HTTP_PORTTI=""  # Päätetään myöhemmin
fi



# Sanitoi haaran nimi worktree-kansion nimeksi (ei välilyöntejä tai erikoismerkkejä)
TURVALLINEN_HAARAN_NIMI=$(printf '%s' "$HAARAN_NIMI" | sed 's#[^[:alnum:]._-]#-#g')
WORKTREE_KANSIO="${YLAKANSIO}/harja-worktree-${TURVALLINEN_HAARAN_NIMI}"

# Validoi polku ennen jatkamista
varmista_turvallinen_polku "$WORKTREE_KANSIO"

echo -e "${SININEN}═══════════════════════════════════════════════════════════${EI_VARIA}"
echo -e "${SININEN}  Harja Git Worktree luonti${EI_VARIA}"
echo -e "${SININEN}═══════════════════════════════════════════════════════════${EI_VARIA}"
echo ""
echo -e "${KELTAINEN}Haara:${EI_VARIA}          $HAARAN_NIMI"
echo -e "${KELTAINEN}Worktree:${EI_VARIA}       $WORKTREE_KANSIO"
if [ -n "$HTTP_PORTTI" ]; then
    echo -e "${KELTAINEN}HTTP-portti:${EI_VARIA}    $HTTP_PORTTI"
fi
echo ""

# Tarkista onko haara olemassa
if ! git rev-parse --verify "${HAARAN_NIMI}" >/dev/null 2>&1; then
    echo -e "${PUNAINEN}❌ Haaraa '${HAARAN_NIMI}' ei löydy!${EI_VARIA}"
    echo -e "${KELTAINEN}Haetaan remote-haarat...${EI_VARIA}"
    git fetch --all
    
    if ! git rev-parse --verify "${HAARAN_NIMI}" >/dev/null 2>&1; then
        if git rev-parse --verify "origin/${HAARAN_NIMI}" >/dev/null 2>&1; then
            echo -e "${SININEN}Löydettiin remote-haara: origin/${HAARAN_NIMI}${EI_VARIA}"
            HAARAN_NIMI="origin/${HAARAN_NIMI}"
        else
            echo -e "${PUNAINEN}❌ Haaraa ei löydy edes remotesta!${EI_VARIA}"
            exit 1
        fi
    fi
fi

# Tarkista onko worktree jo olemassa
if [ -d "$WORKTREE_KANSIO" ]; then
    echo -e "${PUNAINEN}❌ Worktree hakemisto on jo olemassa: $WORKTREE_KANSIO${EI_VARIA}"
    echo -e "${KELTAINEN}Aja ensin: sh/git-worktree/poista-worktree.sh $HAARAN_NIMI${EI_VARIA}"
    exit 1
fi

# Luo worktree
echo -e "${SININEN}📁 Luodaan worktree...${EI_VARIA}"
git worktree add "${WORKTREE_KANSIO}" "${HAARAN_NIMI}"

# Varmista että worktree tukee nREPL-portin yliajoa (muuten se yrittää usein porttia 4005 ja törmää pääinstanssiin).
if ! paivita_worktree_profiles_nrepl_portti "$WORKTREE_KANSIO"; then
    echo ""
    echo -e "${KELTAINEN}Worktree jätetään paikoilleen käsin korjattavaksi:${EI_VARIA}"
    echo -e "${KELTAINEN}   $WORKTREE_KANSIO/profiles.clj${EI_VARIA}"
    echo -e "${KELTAINEN}Lisää tiedostoon HARJA_NREPL_PORTTI-tuki tai muuta :port-määritys käsin ennen käynnistystä.${EI_VARIA}"
    exit 1
fi

# Jos porttia ei ole vielä määritetty, tarkista tukeeko worktree-branch dynaamisia portteja
if [ -z "$HTTP_PORTTI" ]; then
    if tukeeko_dynaamista_porttia "$WORKTREE_KANSIO"; then
        # Branch tukee worktreeta, etsi vapaa portti
        echo -e "${SININEN}Etsitään vapaata porttia rangesta 3001-3020...${EI_VARIA}"
        HTTP_PORTTI=$(etsi_vapaa_portti)
        if [ -z "$HTTP_PORTTI" ]; then
            echo -e "${PUNAINEN}❌ Ei vapaita portteja rangesta 3001-3020!${EI_VARIA}"
            echo -e "${KELTAINEN}Sulje joitain worktreeja tai määritä portti manuaalisesti.${EI_VARIA}"
            cd "$PROJEKTIN_JUURI"
            git worktree remove "$WORKTREE_KANSIO" --force
            exit 1
        fi
        echo -e "${VIHREA}✓ Löydettiin vapaa portti: $HTTP_PORTTI${EI_VARIA}"
    else
        # Branch ei tue worktreeta, käytä porttia 3000
        HTTP_PORTTI=3000
        echo -e "${KELTAINEN}⚠️  Branch ei tue worktree-toiminnallisuutta${EI_VARIA}"
        echo -e "${SININEN}   Käytetään porttia: $HTTP_PORTTI${EI_VARIA}"

        if portti_varattu "$HTTP_PORTTI"; then
            echo ""
            echo -e "${PUNAINEN}❌ Portti $HTTP_PORTTI on jo käytössä.${EI_VARIA}"
            echo -e "${KELTAINEN}   Tämä branch ei tue HARJA_HTTP_PORTTI-asetusta, joten Harja yrittää käynnistyä porttiin 3000.${EI_VARIA}"
            echo -e "${KELTAINEN}   Vapauta portti 3000 tai käytä branchia, jossa worktree-tuki/portin valinta on mukana.${EI_VARIA}"
            echo ""
            echo -e "${KELTAINEN}Siivotaan luotu worktree pois.${EI_VARIA}"
            cd "$PROJEKTIN_JUURI"
            git worktree remove "$WORKTREE_KANSIO" --force || true
            exit 1
        fi
    fi
    echo ""
fi

# Päätä Figwheelin frontend REPL -portti
if tukeeko_dynaamista_frontend_repl_porttia "$WORKTREE_KANSIO"; then
    echo -e "${SININEN}Etsitään vapaata frontend REPL -porttia rangesta 3449-3499...${EI_VARIA}"
    FRONTEND_REPL_PORTTI=$(etsi_vapaa_frontend_repl_portti)
    if [ -z "$FRONTEND_REPL_PORTTI" ]; then
        echo -e "${PUNAINEN}❌ Ei vapaita portteja rangesta 3449-3499!${EI_VARIA}"
        echo -e "${KELTAINEN}Sulje joitain Figwheel-instansseja tai aseta FRONTEND_REPL_PORT käsin.${EI_VARIA}"
        cd "$PROJEKTIN_JUURI"
        git worktree remove "$WORKTREE_KANSIO" --force
        exit 1
    fi
    echo -e "${VIHREA}✓ Löydettiin vapaa frontend REPL -portti: $FRONTEND_REPL_PORTTI${EI_VARIA}"
else
    FRONTEND_REPL_PORTTI=3449
    echo -e "${KELTAINEN}⚠️  Branch ei tue FRONTEND_REPL_PORT-asetusta${EI_VARIA}"
    echo -e "${SININEN}   Käytetään frontend REPL -porttia: $FRONTEND_REPL_PORTTI${EI_VARIA}"

    if portti_varattu "$FRONTEND_REPL_PORTTI"; then
        echo ""
        echo -e "${PUNAINEN}❌ Portti $FRONTEND_REPL_PORTTI on jo käytössä.${EI_VARIA}"
        echo -e "${KELTAINEN}   Tämä branch ei tue FRONTEND_REPL_PORT-asetusta, joten Figwheel yrittää käynnistyä porttiin 3449.${EI_VARIA}"
        echo -e "${KELTAINEN}   Vapauta portti 3449 tai käytä branchia, jossa FRONTEND_REPL_PORT-tuki on mukana.${EI_VARIA}"
        echo ""
        echo -e "${KELTAINEN}Siivotaan luotu worktree pois.${EI_VARIA}"
        cd "$PROJEKTIN_JUURI"
        git worktree remove "$WORKTREE_KANSIO" --force || true
        exit 1
    fi
fi
echo ""

echo -e "${SININEN}Etsitään vapaata tietokantaporttia rangesta 5433-5499...${EI_VARIA}"
TIETOKANTA_PORTTI=$(etsi_vapaa_tietokanta_portti)
if [ -z "$TIETOKANTA_PORTTI" ]; then
    echo -e "${PUNAINEN}❌ Ei vapaita tietokantaportteja rangesta 5433-5499!${EI_VARIA}"
    echo -e "${KELTAINEN}Sulje joitain paikallisia PostgreSQL-instansseja tai vapauta portteja.${EI_VARIA}"
    cd "$PROJEKTIN_JUURI"
    git worktree remove "$WORKTREE_KANSIO" --force
    exit 1
fi

POSTGRESQL_NAME="harjadb-${TURVALLINEN_HAARAN_NIMI}"
POSTGRESQL_NAME="${POSTGRESQL_NAME:0:60}"

echo -e "${VIHREA}✓ Löydettiin vapaa tietokantaportti: $TIETOKANTA_PORTTI${EI_VARIA}"
echo -e "${VIHREA}✓ Tietokanta-kontin nimi: $POSTGRESQL_NAME${EI_VARIA}"
echo ""

echo -e "${SININEN}Etsitään vapaata backend nREPL -porttia rangesta 4006-4099...${EI_VARIA}"
BACKEND_NREPL_PORTTI=$(etsi_vapaa_backend_nrepl_portti)
if [ -z "$BACKEND_NREPL_PORTTI" ]; then
    echo -e "${PUNAINEN}❌ Ei vapaita backend nREPL -portteja rangesta 4006-4099!${EI_VARIA}"
    echo -e "${KELTAINEN}Sulje joitain lein repl -instansseja tai aseta HARJA_NREPL_PORTTI käsin.${EI_VARIA}"
    cd "$PROJEKTIN_JUURI"
    git worktree remove "$WORKTREE_KANSIO" --force
    exit 1
fi
echo -e "${VIHREA}✓ Löydettiin vapaa backend nREPL -portti: $BACKEND_NREPL_PORTTI${EI_VARIA}"
echo ""

echo -e "${KELTAINEN}HTTP-portti:${EI_VARIA}    $HTTP_PORTTI"
echo -e "${KELTAINEN}Frontend REPL-portti:${EI_VARIA} $FRONTEND_REPL_PORTTI"
echo -e "${KELTAINEN}Tietokanta-portti:${EI_VARIA} $TIETOKANTA_PORTTI"
echo -e "${KELTAINEN}Tietokanta-kontti:${EI_VARIA} $POSTGRESQL_NAME"
echo -e "${KELTAINEN}Backend nREPL-portti:${EI_VARIA} $BACKEND_NREPL_PORTTI"
echo ""

# Asenna npm-riippuvuudet
echo -e "${SININEN}📦 Asennetaan npm-riippuvuudet (npm ci)...${EI_VARIA}"
echo -e "${KELTAINEN}   Tämä voi kestää hetken...${EI_VARIA}"
cd "$WORKTREE_KANSIO"
if npm ci; then
    echo -e "${VIHREA}   ✓ npm-riippuvuudet asennettu${EI_VARIA}"
else
    echo -e "${PUNAINEN}❌ npm ci epäonnistui!${EI_VARIA}"
    echo -e "${PUNAINEN}⚠️  KRIITTINEN: npm ci on pakollinen riippuvuuksien asentamiseen${EI_VARIA}"
    echo -e "${KELTAINEN}   Syitä epäonnistumiseen:${EI_VARIA}"
    echo -e "${KELTAINEN}   - package-lock.json puuttuu tai on korruptoitunut${EI_VARIA}"
    echo -e "${KELTAINEN}   - Node.js-versio ei ole yhteensopiva${EI_VARIA}"
    echo -e "${KELTAINEN}   - npm cache on korruptoitunut (kokeile: npm cache clean --force)${EI_VARIA}"
    echo ""
    echo -e "${KELTAINEN}Puhdistetaan luotu worktree...${EI_VARIA}"
    cd "$PROJEKTIN_JUURI"
    git worktree remove "$WORKTREE_KANSIO" --force
    exit 1
fi
cd "$PROJEKTIN_JUURI"

# Tarkista onko uusia migraatioita ja tarjoa tietokannan uudelleenkäynnistys
echo -e "${SININEN}🔍 Tarkistetaan migraatiot...${EI_VARIA}"

# Laske migraatiotiedostot molemmissa paikoissa
WORKTREE_MIGRAATIOT=$(find "$WORKTREE_KANSIO/tietokanta/src/main/resources/db/migration" -type f -name "*.sql" 2>/dev/null | wc -l | tr -d ' ')
PAAHARAN_MIGRAATIOT=$(find "$PROJEKTIN_JUURI/tietokanta/src/main/resources/db/migration" -type f -name "*.sql" 2>/dev/null | wc -l | tr -d ' ')

# Hae viimeiset migraatiotiedostot (lajittele numeerisesti version mukaan)
hae_viimeisin_migraatio() {
    local polku="$1"
    local regex="V1_([0-9]+)__.*"
    local suurin_versio=0
    local viimeisin_tiedosto=""

    local tiedosto
    while IFS= read -r -d '' tiedosto; do
        local nimi
        nimi=$(basename -- "$tiedosto")
        if [[ $nimi =~ $regex ]]; then
            local versio
            versio="${BASH_REMATCH[1]}"
            if [ "$versio" -gt "$suurin_versio" ]; then
                suurin_versio=$versio
                viimeisin_tiedosto=$nimi
            fi
        fi
    done < <(find "$polku" -type f -name "*.sql" -print0 2>/dev/null)
    
    echo "$viimeisin_tiedosto"
}

WORKTREE_VIIMEINEN=$(hae_viimeisin_migraatio "$WORKTREE_KANSIO/tietokanta/src/main/resources/db/migration")
PAAHARAN_VIIMEINEN=$(hae_viimeisin_migraatio "$PROJEKTIN_JUURI/tietokanta/src/main/resources/db/migration")

if [ "$WORKTREE_MIGRAATIOT" -gt "$PAAHARAN_MIGRAATIOT" ]; then
    echo -e "${KELTAINEN}⚠️  Huomattu $((WORKTREE_MIGRAATIOT - PAAHARAN_MIGRAATIOT)) uutta migraatiotiedostoa!${EI_VARIA}"
    kysy_tietokannan_uudelleenkaynnistys "$WORKTREE_KANSIO" "$TIETOKANTA_PORTTI" "$POSTGRESQL_NAME"
elif [ "$WORKTREE_MIGRAATIOT" -lt "$PAAHARAN_MIGRAATIOT" ]; then
    echo -e "${KELTAINEN}⚠️  Worktreessä on vähemmän migraatioita kuin päähaarassa!${EI_VARIA}"
    echo -e "${KELTAINEN}   Worktree: $WORKTREE_MIGRAATIOT, Päähaara: $PAAHARAN_MIGRAATIOT${EI_VARIA}"
    echo -e "${KELTAINEN}   Viimeinen worktreessä: $WORKTREE_VIIMEINEN${EI_VARIA}"
    echo -e "${KELTAINEN}   Viimeinen päähaarassa: $PAAHARAN_VIIMEINEN${EI_VARIA}"
    echo ""
    kysy_tietokannan_uudelleenkaynnistys "$WORKTREE_KANSIO" "$TIETOKANTA_PORTTI" "$POSTGRESQL_NAME"
elif [ "$WORKTREE_MIGRAATIOT" -eq "$PAAHARAN_MIGRAATIOT" ] && [ "$WORKTREE_MIGRAATIOT" -gt 0 ]; then
    # Tarkista vielä että viimeiset migraatiot täsmäävät
    if [ "$WORKTREE_VIIMEINEN" != "$PAAHARAN_VIIMEINEN" ]; then
        echo -e "${KELTAINEN}⚠️  Migraatioiden määrä sama, mutta viimeiset tiedostot eroavat!${EI_VARIA}"
        echo -e "${KELTAINEN}   Viimeinen worktreessä: $WORKTREE_VIIMEINEN${EI_VARIA}"
        echo -e "${KELTAINEN}   Viimeinen päähaarassa: $PAAHARAN_VIIMEINEN${EI_VARIA}"
        echo ""
        kysy_tietokannan_uudelleenkaynnistys "$WORKTREE_KANSIO" "$TIETOKANTA_PORTTI" "$POSTGRESQL_NAME"
    else
        echo -e "${VIHREA}✓ Migraatiot täsmäävät${EI_VARIA}"
        echo ""
    fi
else
    echo -e "${KELTAINEN}⚠️  Migraatiotiedostoja ei löytynyt${EI_VARIA}"
    echo -e "${KELTAINEN}   Worktree: $WORKTREE_MIGRAATIOT, Päähaara: $PAAHARAN_MIGRAATIOT${EI_VARIA}"
    echo ""
fi

# Luo käynnistysskripti worktreelle
echo -e "${SININEN}⚙️  Luodaan käynnistysskripti...${EI_VARIA}"

cat > "$WORKTREE_KANSIO/.tietokanta.env" << EOF
export HARJA_TIETOKANTA_HOST="127.0.0.1"
export HARJA_TIETOKANTA_PORTTI="$TIETOKANTA_PORTTI"
export POSTGRESQL_NAME="$POSTGRESQL_NAME"
EOF

cat > "$WORKTREE_KANSIO/kaynnista-kaikki.sh" << EOF
#!/usr/bin/env bash
set -euo pipefail

WORKTREE_KANSIO="\$( cd "\$( dirname "\${BASH_SOURCE[0]}" )" && pwd )"

# Aseta worktree-spesifiset ympäristömuuttujat
export HARJA_HTTP_PORTTI=$HTTP_PORTTI
export HARJA_ENV_HARJA_URL="localhost:$HTTP_PORTTI"
export FRONTEND_REPL_PORT=$FRONTEND_REPL_PORTTI
export HARJA_NREPL_PORTTI=$BACKEND_NREPL_PORTTI

clean() {
    set +e
    if [ -f "\$WORKTREE_KANSIO/.backend.pid" ]; then
        BACKEND_PID=\$(cat "\$WORKTREE_KANSIO/.backend.pid" 2>/dev/null || true)
        if [ -n "\${BACKEND_PID:-}" ]; then
            echo ""
            echo "🛑 Pysäytetään backend (PID: \$BACKEND_PID)..."
            if kill -0 "\$BACKEND_PID" 2>/dev/null; then
                cmd=\$(ps -p "\$BACKEND_PID" -o command= 2>/dev/null || true)
                if [[ "\$cmd" == *lein* || "\$cmd" == *java* ]]; then
                    kill "\$BACKEND_PID" 2>/dev/null || true
                    sleep 1
                    if kill -0 "\$BACKEND_PID" 2>/dev/null; then
                        kill -KILL "\$BACKEND_PID" 2>/dev/null || true
                    fi
                else
                    echo "   (PID ei näytä backend-prosessilta, ohitetaan tappo: \$cmd)"
                fi
            fi
        fi
        rm -f "\$WORKTREE_KANSIO/.backend.pid"
    fi
}

trap clean EXIT INT TERM HUP

# Lue worktree-kohtaiset tietokanta-asetukset
if [ -f "\$WORKTREE_KANSIO/.tietokanta.env" ]; then
    # shellcheck disable=SC1091
    source "\$WORKTREE_KANSIO/.tietokanta.env"
else
    echo "❌ Tietokanta-asetustiedosto puuttuu: \$WORKTREE_KANSIO/.tietokanta.env"
    exit 1
fi

echo "🚀 Käynnistetään Harja worktree..."
echo "   Backend käynnistetään taustaprosessina"
echo "   Frontend käynnistyy tässä terminaalissa"
echo "   Backend-portti: \$HARJA_HTTP_PORTTI"
echo "   Backend URL: http://localhost:\$HARJA_HTTP_PORTTI"
echo "   Frontend (Figwheel) portti: \$FRONTEND_REPL_PORT"
echo "   Frontend (Figwheel) URL: http://localhost:\$FRONTEND_REPL_PORT"
echo "   Backend nREPL portti: \$HARJA_NREPL_PORTTI"
echo "   Tietokanta portti: \$HARJA_TIETOKANTA_PORTTI"
echo "   Tietokanta kontti: \$POSTGRESQL_NAME"
echo ""

# Käynnistä tietokanta (tai varmista että se on käynnissä)
if ! command -v docker >/dev/null 2>&1; then
    echo "❌ docker ei löydy PATH:sta. Asenna Docker Desktop tai varmista että docker on käytettävissä."
    exit 1
fi

echo "🐘 Varmistetaan tietokanta..."
if docker inspect "\$POSTGRESQL_NAME" >/dev/null 2>&1; then
    if docker ps --format '{{.Names}}' | grep -Fx "\$POSTGRESQL_NAME" >/dev/null 2>&1; then
        echo "   ✓ Tietokanta-kontti on jo käynnissä: \$POSTGRESQL_NAME"
    else
        echo "   🔁 Käynnistetään olemassa oleva tietokanta-kontti: \$POSTGRESQL_NAME"
        docker start "\$POSTGRESQL_NAME" >/dev/null
        until docker exec "\$POSTGRESQL_NAME" pg_isready >/dev/null 2>&1; do
            sleep 0.5
        done
        echo "   ✓ Tietokanta käynnissä"
    fi
else
    echo "   🆕 Luodaan ja alustetaan tietokanta (tämä voi kestää hetken)..."
    DEVDB_UP="\$WORKTREE_KANSIO/tietokanta/devdb_up.sh"
    if [ ! -f "\$DEVDB_UP" ]; then
        echo "❌ Tietokannan käynnistysskripti puuttuu: \$DEVDB_UP"
        exit 1
    fi

    if grep -q '\${HARJA_TIETOKANTA_HOST:-harjadb}' "\$DEVDB_UP" \
        || grep -q '\${HARJA_TIETOKANTA_PORTTI:-5432}:\${HARJA_TIETOKANTA_PORTTI:-5432}' "\$DEVDB_UP"; then
        echo "❌ Tämä haara sisältää vanhan version skriptistä tietokanta/devdb_up.sh, joka ei tue worktree-tietokantaa oikein."
        echo "   Oire: Docker yrittää käyttää konttinimenä hostia (esim. 127.0.0.1) ja antaa virheen: No such container: 127.0.0.1"
        echo ""
        echo "   Korjaa päivittämällä haara sisältämään devdb_up.sh fixi (PR #4130 / commit 593702bd27)"
        echo "   tai aja migraatiot käsin oikeaan konttiin:"
        echo "     docker exec --user postgres -e HARJA_TIETOKANTA_HOST=localhost -e HARJA_TIETOKANTA_PORTTI=5432 \"\$POSTGRESQL_NAME\" /bin/bash -c \"~/aja-migraatiot.sh\""
        echo "     docker exec --user postgres -e HARJA_TIETOKANTA_HOST=localhost -e HARJA_TIETOKANTA_PORTTI=5432 \"\$POSTGRESQL_NAME\" /bin/bash -c \"~/aja-testidata.sh\""
        exit 1
    fi

    HARJA_TIETOKANTA_PORTTI="\$HARJA_TIETOKANTA_PORTTI" POSTGRESQL_NAME="\$POSTGRESQL_NAME" "\$DEVDB_UP"
fi

# Käynnistä backend taustalle
echo "🔧 Käynnistetään backend taustalle..."
lein do clean, compile, repl :headless :host 127.0.0.1 > "\$WORKTREE_KANSIO/backend.log" 2>&1 &
BACKEND_PID=\$!
echo "\$BACKEND_PID" > "\$WORKTREE_KANSIO/.backend.pid"
echo "   Backend PID: \$BACKEND_PID"
echo "   Backend loki: \$WORKTREE_KANSIO/backend.log"

# Odota että backend käynnistyy (tarkista REPL)
echo "⏳ Odotetaan backendin käynnistymistä..."
timeout=180
elapsed=0
while [ \$elapsed -lt \$timeout ]; do
    if grep -q "nREPL server started" "\$WORKTREE_KANSIO/backend.log" 2>/dev/null; then
        NREPL_PORT=\$(grep "nREPL server started" "\$WORKTREE_KANSIO/backend.log" | grep -oE 'port [0-9]+' | grep -oE '[0-9]+' || true)
        if [ -z "\${NREPL_PORT:-}" ]; then
            NREPL_PORT="\$HARJA_NREPL_PORTTI"
        fi
        echo "✅ Backend käynnistyi!"
        echo "   nREPL portti: \$NREPL_PORT"
        echo "   Yhdistä editorilla porttiin: \$NREPL_PORT"
        break
    fi
    sleep 2
    elapsed=\$((elapsed + 2))
done

if [ \$elapsed -ge \$timeout ]; then
    echo "❌ Backend ei käynnistynyt ajallaan!"
    echo "   Tarkista loki: tail -f \$WORKTREE_KANSIO/backend.log"
    exit 1
fi

echo ""
echo "🎨 Käynnistetään frontend..."
echo "   (Backend pyörii taustalla)"
echo "Frontend (Figwheel): http://localhost:\$FRONTEND_REPL_PORT"
echo "Backend: http://localhost:\$HARJA_HTTP_PORTTI"


# Käynnistä frontend interaktiivisesti
bash ./kaynnista_harja_front_dev.sh

clean
EOF

chmod +x "$WORKTREE_KANSIO/kaynnista-kaikki.sh"
echo -e "${VIHREA}✓ Käynnistysskripti luotu${EI_VARIA}"
echo ""
echo -e "${VIHREA}✅ Worktree luotu onnistuneesti!${EI_VARIA}"
echo ""
echo -e "${SININEN}═══════════════════════════════════════════════════════════${EI_VARIA}"
echo -e "${SININEN}  Käynnistä worktree${EI_VARIA}"
echo -e "${SININEN}═══════════════════════════════════════════════════════════${EI_VARIA}"
echo ""
echo -e "${VIHREA}🚀 SUOSITUS (automaattinen):${EI_VARIA}"
echo -e "   cd $WORKTREE_KANSIO && ./kaynnista-kaikki.sh"
echo ""

# Kysy käyttäjältä haluaako käynnistää heti
echo -e "${KELTAINEN}Haluatko käynnistää worktreen nyt? [y/N]${EI_VARIA}"
read -p "" -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    cd "$WORKTREE_KANSIO"
    echo -e "${VIHREA}Käynnistetään...${EI_VARIA}"
    exec ./kaynnista-kaikki.sh
else
    echo -e "${SININEN}Voit käynnistää myöhemmin komennolla:${EI_VARIA}"
    echo -e "   cd $WORKTREE_KANSIO && ./kaynnista-kaikki.sh"
fi
