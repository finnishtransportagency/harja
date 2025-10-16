#!/bin/bash
set -euo pipefail

# Värit
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

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

echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Harja Git Worktree luonti${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}Haara:${NC}          $HAARAN_NIMI"
echo -e "${YELLOW}Worktree:${NC}       $WORKTREE_KANSIO"
if [ -n "$HTTP_PORTTI" ]; then
    echo -e "${YELLOW}HTTP-portti:${NC}    $HTTP_PORTTI"
fi
echo ""

# Tarkista onko haara olemassa
if ! git rev-parse --verify "$HAARAN_NIMI" >/dev/null 2>&1; then
    echo -e "${RED}❌ Haaraa '$HAARAN_NIMI' ei löydy!${NC}"
    echo -e "${YELLOW}Haetaan remote-haarat...${NC}"
    git fetch --all
    
    if ! git rev-parse --verify "$HAARAN_NIMI" >/dev/null 2>&1; then
        if git rev-parse --verify "origin/$HAARAN_NIMI" >/dev/null 2>&1; then
            echo -e "${BLUE}Löydettiin remote-haara: origin/$HAARAN_NIMI${NC}"
            HAARAN_NIMI="origin/$HAARAN_NIMI"
        else
            echo -e "${RED}❌ Haaraa ei löydy edes remotesta!${NC}"
            exit 1
        fi
    fi
fi

# Tarkista onko worktree jo olemassa
if [ -d "$WORKTREE_KANSIO" ]; then
    echo -e "${RED}❌ Worktree hakemisto on jo olemassa: $WORKTREE_KANSIO${NC}"
    echo -e "${YELLOW}Aja ensin: sh/git-worktree/poista-worktree.sh $HAARAN_NIMI${NC}"
    exit 1
fi

# Luo worktree
echo -e "${BLUE}📁 Luodaan worktree...${NC}"
git worktree add "$WORKTREE_KANSIO" "$HAARAN_NIMI"

# Jos porttia ei ole vielä määritetty, tarkista tukeeko worktree-branch dynaamisia portteja
if [ -z "$HTTP_PORTTI" ]; then
    if [ -d "$WORKTREE_KANSIO/sh/git-worktree" ]; then
        # Branch tukee worktreeta, etsi vapaa portti
        echo -e "${BLUE}Etsitään vapaata porttia rangesta 3001-3020...${NC}"
        HTTP_PORTTI=$(etsi_vapaa_portti)
        if [ -z "$HTTP_PORTTI" ]; then
            echo -e "${RED}❌ Ei vapaita portteja rangesta 3001-3020!${NC}"
            echo -e "${YELLOW}Sulje joitain worktreeja tai määritä portti manuaalisesti.${NC}"
            cd "$PROJEKTIN_JUURI"
            git worktree remove "$WORKTREE_KANSIO" --force
            exit 1
        fi
        echo -e "${GREEN}✓ Löydettiin vapaa portti: $HTTP_PORTTI${NC}"
    else
        # Branch ei tue worktreeta, käytä porttia 3000
        HTTP_PORTTI=3000
        echo -e "${YELLOW}⚠️  Branch ei tue worktree-toiminnallisuutta${NC}"
        echo -e "${BLUE}   Käytetään porttia: $HTTP_PORTTI${NC}"
    fi
    echo ""
fi

echo -e "${YELLOW}HTTP-portti:${NC}    $HTTP_PORTTI"
echo ""

# Asenna npm-riippuvuudet
echo -e "${BLUE}📦 Asennetaan npm-riippuvuudet (npm ci)...${NC}"
echo -e "${YELLOW}   Tämä voi kestää hetken...${NC}"
cd "$WORKTREE_KANSIO"
if npm ci; then
    echo -e "${GREEN}   ✓ npm-riippuvuudet asennettu${NC}"
else
    echo -e "${RED}❌ npm ci epäonnistui!${NC}"
    echo -e "${YELLOW}Yritetään npm install...${NC}"
    if npm install; then
        echo -e "${GREEN}   ✓ npm-riippuvuudet asennettu (npm install)${NC}"
    else
        echo -e "${RED}❌ npm install epäonnistui!${NC}"
        echo -e "${YELLOW}Puhdistetaan worktree...${NC}"
        cd "$PROJEKTIN_JUURI"
        git worktree remove "$WORKTREE_KANSIO" --force
        exit 1
    fi
fi
cd "$PROJEKTIN_JUURI"

# Tarkista onko uusia migraatioita ja tarjoa tietokannan uudelleenkäynnistys
echo -e "${BLUE}🔍 Tarkistetaan migraatiot...${NC}"

# Laske migraatiotiedostot molemmissa paikoissa
WORKTREE_MIGRAATIOT=$(find "$WORKTREE_KANSIO/tietokanta/src" -type f -name "*.sql" 2>/dev/null | wc -l | tr -d ' ')
PAAHARAN_MIGRAATIOT=$(find "$PROJEKTIN_JUURI/tietokanta/src" -type f -name "*.sql" 2>/dev/null | wc -l | tr -d ' ')

if [ "$WORKTREE_MIGRAATIOT" -gt "$PAAHARAN_MIGRAATIOT" ]; then
    echo -e "${YELLOW}⚠️  Huomattu $((WORKTREE_MIGRAATIOT - PAAHARAN_MIGRAATIOT)) uutta migraatiotiedostoa!${NC}"
    echo -e "${YELLOW}Suositus: Aja tietokannan uudelleenkäynnistys ennen käynnistystä${NC}"
    echo ""
    echo -e "${YELLOW}Haluatko uudelleenkäynnistää tietokannan nyt? [y/N]${NC}"
    read -p "" -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${BLUE}🔄 Uudelleenkäynnistetään tietokanta...${NC}"
        if "$PROJEKTIN_JUURI/tietokanta/devdb_restart.sh"; then
            echo -e "${GREEN}✓ Tietokanta uudelleenkäynnistetty onnistuneesti${NC}"
        else
            echo -e "${RED}❌ Tietokannan uudelleenkäynnistys epäonnistui${NC}"
            echo -e "${YELLOW}Voit yrittää myöhemmin: $PROJEKTIN_JUURI/tietokanta/devdb_restart.sh${NC}"
        fi
        echo ""
    else
        echo -e "${YELLOW}💡 Voit ajaa myöhemmin: $PROJEKTIN_JUURI/tietokanta/devdb_restart.sh${NC}"
        echo ""
    fi
elif [ "$WORKTREE_MIGRAATIOT" -eq "$PAAHARAN_MIGRAATIOT" ] && [ "$WORKTREE_MIGRAATIOT" -gt 0 ]; then
    echo -e "${GREEN}✓ Ei uusia migraatioita${NC}"
    echo ""
else
    echo -e "${YELLOW}⚠️  Migraatiotiedostoja ei löytynyt${NC}"
    echo ""
fi

# Luo käynnistysskripti worktreelle
echo -e "${BLUE}⚙️  Luodaan käynnistysskripti...${NC}"

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
echo -e "${GREEN}✓ Käynnistysskripti luotu${NC}"
echo ""
echo -e "${GREEN}✅ Worktree luotu onnistuneesti!${NC}"
echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Käynnistä worktree${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${GREEN}🚀 SUOSITUS (automaattinen):${NC}"
echo -e "   cd $WORKTREE_KANSIO && ./kaynnista-kaikki.sh"
echo ""

# Kysy käyttäjältä haluaako käynnistää heti
echo -e "${YELLOW}Haluatko käynnistää worktreen nyt? [y/N]${NC}"
read -p "" -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    cd "$WORKTREE_KANSIO"
    echo -e "${GREEN}Käynnistetään...${NC}"
    exec ./kaynnista-kaikki.sh
else
    echo -e "${BLUE}Voit käynnistää myöhemmin komennolla:${NC}"
    echo -e "   cd $WORKTREE_KANSIO && ./kaynnista-kaikki.sh"
fi