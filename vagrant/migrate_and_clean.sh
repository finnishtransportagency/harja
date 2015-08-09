#!/bin/sh

echo "Muunnetaan ja putsataan sovelluskanta"

vagrant ssh -c "cd /harja-tietokanta; sudo -u postgres sh ./kanta_template.sh"
vagrant ssh -c "cd /harja-tietokanta; sudo -u postgres sh ./kanta_uusiksi.sh"
