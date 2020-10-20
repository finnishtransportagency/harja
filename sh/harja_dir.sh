#!/bin/bash
set -euo pipefail

HARJA_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"

cd "$HARJA_DIR" || exit

if [[ -z "$(grep "defproject harja" project.clj)" ]]; then exit; fi;