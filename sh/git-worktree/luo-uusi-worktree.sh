#!/bin/bash
set -euo pipefail

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
        if ! lsof -Pi :$portti -sTCP:LISTEN -t >/dev/null 2>&1; then
            echo $portti
            return 0
        fi
    done
    
    # Jos kaikki portit varattu, palauta virhe
    echo ""
    return 1
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

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    usage
fi

# Määritä polut - käytä harja_dir.sh apuskriptiä
# shellcheck source=../harja_dir.sh
source "$( dirname "${BASH_SOURCE[0]}" )/../harja_dir.sh" || exit
HAARAN_NIMI="$1"
PROJEKTIN_JUURI="$HARJA_DIR"
YLAKANSIO="$(dirname "$PROJEKTIN_JUURI")"

# Jos portti on annettu, käytä sitä. Muuten päätetään myöhemmin worktreen luonnin jälkeen
if [ $# -eq 2 ]; then
    HTTP_PORTTI="$2"
else
    HTTP_PORTTI=""  # Päätetään myöhemmin
fi



# Sanitoi haaran nimi
TURVALLINEN_HAARAN_NIMI=$(echo "$HAARAN_NIMI" | sed 's/[\/:]/-/g')
WORKTREE_KANSIO="${YLAKANSIO}/harja-worktree-${TURVALLINEN_HAARAN_NIMI}"

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
if ! git rev-parse --verify "$HAARAN_NIMI" >/dev/null 2>&1; then
    echo -e "${PUNAINEN}❌ Haaraa '$HAARAN_NIMI' ei löydy!${EI_VARIA}"
    echo -e "${KELTAINEN}Haetaan remote-haarat...${EI_VARIA}"
    git fetch --all
    
    if ! git rev-parse --verify "$HAARAN_NIMI" >/dev/null 2>&1; then
        if git rev-parse --verify "origin/$HAARAN_NIMI" >/dev/null 2>&1; then
            echo -e "${SININEN}Löydettiin remote-haara: origin/$HAARAN_NIMI${EI_VARIA}"
            HAARAN_NIMI="origin/$HAARAN_NIMI"
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
git worktree add "$WORKTREE_KANSIO" "$HAARAN_NIMI"

# Jos porttia ei ole vielä määritetty, tarkista tukeeko worktree-branch dynaamisia portteja
if [ -z "$HTTP_PORTTI" ]; then
    if [ -d "$WORKTREE_KANSIO/sh/git-worktree" ]; then
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
    fi
    echo ""
fi

echo -e "${KELTAINEN}HTTP-portti:${EI_VARIA}    $HTTP_PORTTI"
echo ""

# Asenna npm-riippuvuudet
echo -e "${SININEN}📦 Asennetaan npm-riippuvuudet (npm ci)...${EI_VARIA}"
echo -e "${KELTAINEN}   Tämä voi kestää hetken...${EI_VARIA}"
cd "$WORKTREE_KANSIO"
if npm ci; then
    echo -e "${VIHREA}   ✓ npm-riippuvuudet asennettu${EI_VARIA}"
else
    echo -e "${PUNAINEN}❌ npm ci epäonnistui!${EI_VARIA}"
    echo -e "${KELTAINEN}Yritetään npm install...${EI_VARIA}"
    if npm install; then
        echo -e "${VIHREA}   ✓ npm-riippuvuudet asennettu (npm install)${EI_VARIA}"
    else
        echo -e "${PUNAINEN}❌ npm install epäonnistui!${EI_VARIA}"
        echo -e "${KELTAINEN}Puhdistetaan worktree...${EI_VARIA}"
        cd "$PROJEKTIN_JUURI"
        git worktree remove "$WORKTREE_KANSIO" --force
        exit 1
    fi
fi
cd "$PROJEKTIN_JUURI"

# Tarkista onko uusia migraatioita ja tarjoa tietokannan uudelleenkäynnistys
echo -e "${SININEN}🔍 Tarkistetaan migraatiot...${EI_VARIA}"

# Laske migraatiotiedostot molemmissa paikoissa
WORKTREE_MIGRAATIOT=$(find "$WORKTREE_KANSIO/tietokanta/src" -type f -name "*.sql" 2>/dev/null | wc -l | tr -d ' ')
PAAHARAN_MIGRAATIOT=$(find "$PROJEKTIN_JUURI/tietokanta/src" -type f -name "*.sql" 2>/dev/null | wc -l | tr -d ' ')

if [ "$WORKTREE_MIGRAATIOT" -gt "$PAAHARAN_MIGRAATIOT" ]; then
    echo -e "${KELTAINEN}⚠️  Huomattu $((WORKTREE_MIGRAATIOT - PAAHARAN_MIGRAATIOT)) uutta migraatiotiedostoa!${EI_VARIA}"
    echo -e "${KELTAINEN}Suositus: Aja tietokannan uudelleenkäynnistys ennen käynnistystä${EI_VARIA}"
    echo ""
    echo -e "${KELTAINEN}Haluatko uudelleenkäynnistää tietokannan nyt? [y/N]${EI_VARIA}"
    read -p "" -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${SININEN}🔄 Uudelleenkäynnistetään tietokanta...${EI_VARIA}"
        if "$PROJEKTIN_JUURI/tietokanta/devdb_restart.sh"; then
            echo -e "${VIHREA}✓ Tietokanta uudelleenkäynnistetty onnistuneesti${EI_VARIA}"
        else
            echo -e "${PUNAINEN}❌ Tietokannan uudelleenkäynnistys epäonnistui${EI_VARIA}"
            echo -e "${KELTAINEN}Voit yrittää myöhemmin: $PROJEKTIN_JUURI/tietokanta/devdb_restart.sh${EI_VARIA}"
        fi
        echo ""
    else
        echo -e "${KELTAINEN}💡 Voit ajaa myöhemmin: $PROJEKTIN_JUURI/tietokanta/devdb_restart.sh${EI_VARIA}"
        echo ""
    fi
elif [ "$WORKTREE_MIGRAATIOT" -eq "$PAAHARAN_MIGRAATIOT" ] && [ "$WORKTREE_MIGRAATIOT" -gt 0 ]; then
    echo -e "${VIHREA}✓ Ei uusia migraatioita${EI_VARIA}"
    echo ""
else
    echo -e "${KELTAINEN}⚠️  Migraatiotiedostoja ei löytynyt${EI_VARIA}"
    echo ""
fi

# Luo käynnistysskripti worktreelle
echo -e "${SININEN}⚙️  Luodaan käynnistysskripti...${EI_VARIA}"

cat > "$WORKTREE_KANSIO/kaynnista-kaikki.sh" << EOF
#!/bin/bash
set -euo pipefail

WORKTREE_KANSIO="\$( cd "\$( dirname "\${BASH_SOURCE[0]}" )" && pwd )"

# Aseta worktree-spesifiset ympäristömuuttujat
export HARJA_HTTP_PORTTI=$HTTP_PORTTI
export HARJA_ENV_HARJA_URL="localhost:$HTTP_PORTTI"

echo "🚀 Käynnistetään Harja worktree..."
echo "   Backend käynnistetään taustaprosessina"
echo "   Frontend käynnistyy tässä terminaalissa"
echo "   Portti: \$HARJA_HTTP_PORTTI"
echo "   URL: http://localhost:\$HARJA_HTTP_PORTTI"
echo ""

# Käynnistä backend taustalle
echo "🔧 Käynnistetään backend taustalle..."
lein do clean, compile, repl :headless :host 0.0.0.0 > "\$WORKTREE_KANSIO/backend.log" 2>&1 &
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
        NREPL_PORT=\$(grep "nREPL server started" "\$WORKTREE_KANSIO/backend.log" | grep -oE 'port [0-9]+' | grep -oE '[0-9]+')
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
echo "http://localhost:\$HARJA_HTTP_PORTTI "


# Käynnistä frontend interaktiivisesti
bash ./kaynnista_harja_front_dev.sh

# Kun frontend lopetetaan, tapa myös backend
if [ -f "\$WORKTREE_KANSIO/.backend.pid" ]; then
    BACKEND_PID=\$(cat "\$WORKTREE_KANSIO/.backend.pid")
    echo ""
    echo "🛑 Pysäytetään backend (PID: \$BACKEND_PID)..."
    kill \$BACKEND_PID 2>/dev/null || true
    rm "\$WORKTREE_KANSIO/.backend.pid"
fi
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