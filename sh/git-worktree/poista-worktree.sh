#!/bin/bash
set -euo pipefail

# Värit
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

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

BRANCH_NAME="$1"

# Määritä polut - käytä harja_dir.sh apuskriptiä
# shellcheck source=../harja_dir.sh
source "$( dirname "${BASH_SOURCE[0]}" )/../harja_dir.sh" || exit

PROJECT_ROOT="$HARJA_DIR"
PARENT_DIR="$(dirname "$PROJECT_ROOT")"

# Sanitoi haaran nimi
SAFE_BRANCH_NAME=$(echo "$BRANCH_NAME" | sed 's/[\/:]/-/g')
WORKTREE_DIR="${PARENT_DIR}/harja-worktree-${SAFE_BRANCH_NAME}"

echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Harja Git Worktree poistaminen${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}Haara:${NC}          $BRANCH_NAME"
echo -e "${YELLOW}Worktree:${NC}       $WORKTREE_DIR"
echo ""

# Tarkista onko worktree olemassa
if [ ! -d "$WORKTREE_DIR" ]; then
    echo -e "${RED}❌ Worktree hakemistoa ei löydy: $WORKTREE_DIR${NC}"
    echo ""
    echo -e "${YELLOW}Olemassa olevat worktree:t:${NC}"
    git worktree list
    exit 1
fi

# Vahvista poisto
echo -e "${YELLOW}⚠️  Haluatko varmasti poistaa worktreen?${NC}"
echo -e "${YELLOW}   Tämä poistaa hakemiston ja kaikki tallentamattomat muutokset!${NC}"
echo ""
read -p "Jatka? [y/N] " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${BLUE}Peruttu.${NC}"
    exit 0
fi

echo ""
echo -e "${BLUE}🛑 Pysäytetään worktreen prosessit...${NC}"

# 1. Tarkista .backend.pid tiedosto
if [ -f "$WORKTREE_DIR/.backend.pid" ]; then
    BACKEND_PID=$(cat "$WORKTREE_DIR/.backend.pid")
    echo -e "${YELLOW}   Tapetaan backend PID: $BACKEND_PID${NC}"
    kill "$BACKEND_PID" 2>/dev/null || echo -e "${YELLOW}   (Backend ei ollut enää käynnissä)${NC}"
    rm "$WORKTREE_DIR/.backend.pid"
fi

# 2. Etsi ja tapa kaikki lein-prosessit jotka liittyvät tähän worktreehen
echo -e "${YELLOW}   Etsitään lein-prosesseja worktree-hakemistossa...${NC}"
LEIN_PIDS=$(lsof -t +D "$WORKTREE_DIR" 2>/dev/null || true)
if [ -n "$LEIN_PIDS" ]; then
    echo -e "${YELLOW}   Tapetaan prosessit: $LEIN_PIDS${NC}"
    echo "$LEIN_PIDS" | xargs kill -9 2>/dev/null || true
fi

# 3. Etsi Java-prosessit jotka viittaavat worktree-hakemistoon
echo -e "${YELLOW}   Etsitään Java-prosesseja...${NC}"
JAVA_PIDS=$(ps aux | grep "java.*$WORKTREE_DIR" | grep -v grep | awk '{print $2}' || true)
if [ -n "$JAVA_PIDS" ]; then
    echo -e "${YELLOW}   Tapetaan Java-prosessit: $JAVA_PIDS${NC}"
    echo "$JAVA_PIDS" | xargs kill -9 2>/dev/null || true
fi

# 4. Odota hetki että prosessit ehtivät sulkeutua
sleep 2

# 5. Vielä yksi tarkistus - onko jotain jäljellä?
REMAINING_PROCS=$(lsof +D "$WORKTREE_DIR" 2>/dev/null | grep -v "COMMAND" || true)
if [ -n "$REMAINING_PROCS" ]; then
    echo -e "${YELLOW}⚠️  Varoitus: Jotkin prosessit käyttävät vielä hakemistoa:${NC}"
    echo "$REMAINING_PROCS"
    echo ""
    echo -e "${YELLOW}Yritetään pakottaa poisto...${NC}"
fi

# Poista worktree git:stä
echo -e "${BLUE}🗑️  Poistetaan worktree...${NC}"
git worktree remove "$WORKTREE_DIR" --force 2>/dev/null || {
    echo -e "${YELLOW}⚠️  Git worktree remove epäonnistui, poistetaan manuaalisesti...${NC}"
    
    # Pakota prosessien lopetus jos vielä jotain jäljellä
    fuser -k "$WORKTREE_DIR" 2>/dev/null || true
    sleep 1
    
    # Poista hakemisto
    rm -rf "$WORKTREE_DIR"
    
    # Siivoa git worktree -lista
    git worktree prune
}

# Varmista että hakemisto on poistettu
if [ -d "$WORKTREE_DIR" ]; then
    echo -e "${RED}❌ Hakemisto on vielä olemassa, pakotetaan poisto...${NC}"
    
    # Viimeinen yritys - pakota kaikki kiinni
    fuser -k "$WORKTREE_DIR" 2>/dev/null || true
    sleep 1
    
    # Poista raa'asti
    rm -rf "$WORKTREE_DIR" || {
        echo -e "${RED}❌ Hakemiston poisto epäonnistui!${NC}"
        echo -e "${YELLOW}Kokeile manuaalisesti: sudo rm -rf $WORKTREE_DIR${NC}"
        exit 1
    }
fi

# Siivoa git worktree -lista
git worktree prune

echo ""
echo -e "${GREEN}✅ Worktree poistettu onnistuneesti!${NC}"
echo ""
echo -e "${YELLOW}Olemassa olevat worktree:t:${NC}"
git worktree list
echo ""