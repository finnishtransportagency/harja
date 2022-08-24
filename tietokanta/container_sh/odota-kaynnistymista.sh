#!/bin/bash
set -euo pipefail

until pg_ctl status;
do
  echo "Yritetään saada yhteys possuun...";
  sleep 1;
done;

echo "Saatiin possuun yhteys!";