#!/bin/sh

echo Muunnetaan harja-kanta ilman putsausta...

vagrant ssh -c "cd /harja-tietokanta; sudo -u postgres mvn clean compile flyway:migrate"
