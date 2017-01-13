#!/bin/bash

sudo rm -rf tietokanta.pgowned
cp -a tietokanta tietokanta.pgowned
sudo chown -R postgres:postgres tietokanta.pgowned
(cd tietokanta.pgowned && sudo -u postgres bash -x aja_migraatiot.sh)
