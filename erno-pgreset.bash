#!/bin/bash

sudo rm -rf tietokanta.pgowned
cp -a tietokanta tietokanta.pgowned
sudo chown -R postgres:postgres tietokanta.pgowned
(cd tietokanta.pgowned && sudo -u postgres bash -x kanta_template.sh &&
  sudo -u postgres bash -x kanta_uusiksi.sh)

(cd tietokanta.pgowned && sudo -u postgres bash -x testikanta_template.sh &&
  sudo -u postgres bash -x testikanta_uusiksi.sh)
