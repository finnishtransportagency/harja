#!/bin/sh

echo Tehdään migrate harja-kannalle ilman kannan putsausta...

vagrant ssh -c "cd /harja/checkout; sudo mvn clean compile flyway:migrate"
