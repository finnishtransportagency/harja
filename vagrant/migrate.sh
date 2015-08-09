#!/bin/sh

echo Muunnetaan sovelluskanta ilman putsausta...

# vagrant ssh -c "cd /harja-tietokanta; sudo -u postgres mvn clean compile -Pharja_template flyway:migrate"
vagrant ssh -c "cd /harja-tietokanta; sudo -u postgres mvn clean compile flyway:migrate"
