#!/bin/bash
set -euo pipefail

# Värit
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Funktio vapaan portin etsimiseen
find_free_port() {
    local start_port=3001
    local end_port=3020
    
    for port in $(seq $start_port $end_port); do
        # Tarkista onko portti vapaana (ei kuuntele mitään)
        if ! lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
            echo $port
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

BRANCH_NAME="$1"

# Jos porttia ei annettu, etsi vapaa portti
if [ $# -eq 2 ]; then
    HTTP_PORT="$2"
else
    echo -e "${BLUE}Etsitään vapaata porttia rangesta 3001-3020...${NC}"
    HTTP_PORT=$(find_free_port)
    if [ -z "$HTTP_PORT" ]; then
        echo -e "${RED}❌ Ei vapaita portteja rangesta 3001-3020!${NC}"
        echo -e "${YELLOW}Sulje joitain worktreeja tai määritä portti manuaalisesti.${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ Löydettiin vapaa portti: $HTTP_PORT${NC}"
    echo ""
fi

# Määritä polut - käytä harja_dir.sh apuskriptiä
# shellcheck source=../harja_dir.sh
source "$( dirname "${BASH_SOURCE[0]}" )/../harja_dir.sh" || exit

PROJECT_ROOT="$HARJA_DIR"
PARENT_DIR="$(dirname "$PROJECT_ROOT")"

# Sanitoi haaran nimi
SAFE_BRANCH_NAME=$(echo "$BRANCH_NAME" | sed 's/[\/:]/-/g')
WORKTREE_DIR="${PARENT_DIR}/harja-worktree-${SAFE_BRANCH_NAME}"

echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Harja Git Worktree luonti${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}Haara:${NC}          $BRANCH_NAME"
echo -e "${YELLOW}Worktree:${NC}       $WORKTREE_DIR"
echo -e "${YELLOW}HTTP-portti:${NC}    $HTTP_PORT"
echo ""

# Tarkista onko haara olemassa
if ! git rev-parse --verify "$BRANCH_NAME" >/dev/null 2>&1; then
    echo -e "${RED}❌ Haaraa '$BRANCH_NAME' ei löydy!${NC}"
    echo -e "${YELLOW}Haetaan remote-haarat...${NC}"
    git fetch --all
    
    if ! git rev-parse --verify "$BRANCH_NAME" >/dev/null 2>&1; then
        if git rev-parse --verify "origin/$BRANCH_NAME" >/dev/null 2>&1; then
            echo -e "${BLUE}Löydettiin remote-haara: origin/$BRANCH_NAME${NC}"
            BRANCH_NAME="origin/$BRANCH_NAME"
        else
            echo -e "${RED}❌ Haaraa ei löydy edes remotesta!${NC}"
            exit 1
        fi
    fi
fi

# Tarkista onko worktree jo olemassa
if [ -d "$WORKTREE_DIR" ]; then
    echo -e "${RED}❌ Worktree hakemisto on jo olemassa: $WORKTREE_DIR${NC}"
    echo -e "${YELLOW}Aja ensin: sh/git-worktree/poista-worktree.sh $BRANCH_NAME${NC}"
    exit 1
fi

# Luo worktree
echo -e "${BLUE}📁 Luodaan worktree...${NC}"
git worktree add "$WORKTREE_DIR" "$BRANCH_NAME"

# Asenna npm-riippuvuudet
echo -e "${BLUE}📦 Asennetaan npm-riippuvuudet (npm ci)...${NC}"
echo -e "${YELLOW}   Tämä voi kestää hetken...${NC}"
cd "$WORKTREE_DIR"
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
        cd "$PROJECT_ROOT"
        git worktree remove "$WORKTREE_DIR" --force
        exit 1
    fi
fi
cd "$PROJECT_ROOT"

# Patchaa asetukset.edn tukemaan worktree-porttia
echo -e "${BLUE}⚙️  Varmistetaan että asetukset.edn tukee worktree-porttia...${NC}"

ASETUKSET_FILE="$WORKTREE_DIR/asetukset.edn"

if [ -f "$ASETUKSET_FILE" ]; then
    # Tarkista onko HARJA_HTTP_PORTTI jo tuettuna
    if ! grep -q "HARJA_HTTP_PORTTI" "$ASETUKSET_FILE"; then
        echo -e "${YELLOW}   Lisätään HARJA_HTTP_PORTTI tuki asetukset.edn:ään${NC}"
        
        # Tarkista onko :portti rivi olemassa :http-palvelin lohkossa
        if grep -A 10 ":http-palvelin" "$ASETUKSET_FILE" | grep -q ":portti"; then
            # Korvaa olemassaoleva :portti rivi
            sed -i.bak 's/:portti [0-9]*/:portti #=(eval (harja.tyokalut.env\/env "HARJA_HTTP_PORTTI" 3000))/' "$ASETUKSET_FILE"
            rm "$ASETUKSET_FILE.bak"
            echo -e "${GREEN}   ✓ HARJA_HTTP_PORTTI tuki lisätty (korvattu)${NC}"
        elif grep -q ":http-palvelin" "$ASETUKSET_FILE"; then
            # Lisää :portti rivi :http-palvelin lohkon alkuun
            sed -i.bak '/:http-palvelin/a\
                 :portti #=(eval (harja.tyokalut.env\/env "HARJA_HTTP_PORTTI" 3000))' "$ASETUKSET_FILE"
            rm "$ASETUKSET_FILE.bak"
            echo -e "${GREEN}   ✓ HARJA_HTTP_PORTTI tuki lisätty (uusi rivi)${NC}"
        else
            echo -e "${YELLOW}   ⚠️  :http-palvelin ei löytynyt, ohitetaan${NC}"
        fi
    else
        echo -e "${GREEN}   ✓ HARJA_HTTP_PORTTI jo tuettuna${NC}"
    fi
    
    # Tarkista onko HARJA_ENV_HARJA_URL jo tuettuna
    if ! grep -q "HARJA_ENV_HARJA_URL" "$ASETUKSET_FILE"; then
        echo -e "${YELLOW}   Lisätään HARJA_ENV_HARJA_URL tuki asetukset.edn:ään${NC}"
        
        # Tarkista onko :harja-url rivi olemassa
        if grep -q ":harja-url" "$ASETUKSET_FILE"; then
            # Korvaa olemassaoleva :harja-url rivi
            sed -i.bak 's/:harja-url "[^"]*"/:harja-url #=(eval (harja.tyokalut.env\/env "HARJA_ENV_HARJA_URL" "localhost:3000"))/' "$ASETUKSET_FILE"
            rm "$ASETUKSET_FILE.bak"
            echo -e "${GREEN}   ✓ HARJA_ENV_HARJA_URL tuki lisätty (korvattu)${NC}"
        else
            # Lisää :harja-url rivi :http-palvelin lohkon jälkeen
            sed -i.bak '/:http-palvelin/,/^[[:space:]]*}/a\
 :harja-url #=(eval (harja.tyokalut.env\/env "HARJA_ENV_HARJA_URL" "localhost:3000"))' "$ASETUKSET_FILE"
            rm "$ASETUKSET_FILE.bak"
            echo -e "${GREEN}   ✓ HARJA_ENV_HARJA_URL tuki lisätty (uusi rivi)${NC}"
        fi
    else
        echo -e "${GREEN}   ✓ HARJA_ENV_HARJA_URL jo tuettuna${NC}"
    fi
else
    echo -e "${RED}❌ asetukset.edn ei löydy: $ASETUKSET_FILE${NC}"
fi

echo ""

# Luo käynnistysskripti worktreelle
echo -e "${BLUE}⚙️  Luodaan käynnistysskripti...${NC}"

cat > "$WORKTREE_DIR/kaynnista-kaikki.sh" << EOF
#!/bin/bash
set -euo pipefail

WORKTREE_DIR="\$( cd "\$( dirname "\${BASH_SOURCE[0]}" )" && pwd )"

# Aseta worktree-spesifiset ympäristömuuttujat
export HARJA_HTTP_PORTTI=$HTTP_PORT
export HARJA_ENV_HARJA_URL="localhost:$HTTP_PORT"

echo "🚀 Käynnistetään Harja worktree..."
echo "   Backend käynnistetään taustaprosessina"
echo "   Frontend käynnistyy tässä terminaalissa"
echo "   Portti: \$HARJA_HTTP_PORTTI"
echo "   URL: http://localhost:\$HARJA_HTTP_PORTTI"
echo ""

# Käynnistä backend taustalle
echo "🔧 Käynnistetään backend taustalle..."
lein do clean, compile, repl :headless :host 0.0.0.0 > "\$WORKTREE_DIR/backend.log" 2>&1 &
BACKEND_PID=\$!
echo "\$BACKEND_PID" > "\$WORKTREE_DIR/.backend.pid"
echo "   Backend PID: \$BACKEND_PID"
echo "   Backend loki: \$WORKTREE_DIR/backend.log"

# Odota että backend käynnistyy (tarkista REPL)
echo "⏳ Odotetaan backendin käynnistymistä..."
timeout=180
elapsed=0
while [ \$elapsed -lt \$timeout ]; do
    if grep -q "nREPL server started" "\$WORKTREE_DIR/backend.log" 2>/dev/null; then
        NREPL_PORT=\$(grep "nREPL server started" "\$WORKTREE_DIR/backend.log" | grep -oE 'port [0-9]+' | grep -oE '[0-9]+')
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
    echo "   Tarkista loki: tail -f \$WORKTREE_DIR/backend.log"
    exit 1
fi

echo ""
echo "🎨 Käynnistetään frontend..."
echo "   (Backend pyörii taustalla)"
echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║  ⚠️  TÄRKEÄÄ: Avaa selain osoitteeseen:                 ║"
echo "║      http://localhost:\$HARJA_HTTP_PORTTI                ║"
echo "║                                                          ║"
echo "║  (EI localhost:3000 tai localhost:3449!)                ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# Käynnistä frontend interaktiivisesti
bash ./kaynnista_harja_front_dev.sh

# Kun frontend lopetetaan, tapa myös backend
if [ -f "\$WORKTREE_DIR/.backend.pid" ]; then
    BACKEND_PID=\$(cat "\$WORKTREE_DIR/.backend.pid")
    echo ""
    echo "🛑 Pysäytetään backend (PID: \$BACKEND_PID)..."
    kill \$BACKEND_PID 2>/dev/null || true
    rm "\$WORKTREE_DIR/.backend.pid"
fi
EOF

chmod +x "$WORKTREE_DIR/kaynnista-kaikki.sh"
echo -e "${GREEN}✓ Käynnistysskripti luotu${NC}"
echo ""
echo -e "${GREEN}✅ Worktree luotu onnistuneesti!${NC}"
echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Käynnistä worktree${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${GREEN}🚀 SUOSITUS (automaattinen):${NC}"
echo -e "   cd $WORKTREE_DIR && ./kaynnista-kaikki.sh"
echo ""

# Kysy käyttäjältä haluaako käynnistää heti
echo -e "${YELLOW}Haluatko käynnistää worktreen nyt? [y/N]${NC}"
read -p "" -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    cd "$WORKTREE_DIR"
    echo -e "${GREEN}Käynnistetään...${NC}"
    exec ./kaynnista-kaikki.sh
else
    echo -e "${BLUE}Voit käynnistää myöhemmin komennolla:${NC}"
    echo -e "   cd $WORKTREE_DIR && ./kaynnista-kaikki.sh"
fi