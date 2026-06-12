#!/bin/bash
set -x

# Asennetaan npm-riippuvuudet, jos node_modules puuttuu
[ -d node_modules ] || npm ci

# Asennetaan Cypressin binääri, jos se puuttuu (.npmrc estää automaattisen postinstall-latauksen)
npx cypress install

npx cypress open
