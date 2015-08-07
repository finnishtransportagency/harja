#!/bin/sh

echo "Putsataan ja muunnetaan testikanta..."

vagrant ssh -c "cd /harja-tietokanta; sudo -u postgres sh testikanta_template.sh"
vagrant ssh -c "cd /harja-tietokanta; sudo -u postgres sh testikanta_uusiksi.sh"
