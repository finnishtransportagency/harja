# Harja tietokanta

Uudet migraatiot lisätään kansioon src\main\resources\db\migration

## Tietokantojen salasanat

Kyselepä tiimiläisiltä.

## Tietokantaskriptit ja niiden keskinäiset riippuvuudet

Skriptit rakentavat ja tarvittaessa tuhoavat lokaalin kehitystietokannan.

Kun haluat putsata tietokannan ja alkaa puhtaalta pöydältä, 
aja juuressa tietokanta/devdb_restart.sh tai tietokanta-kansiossa ./devdb_restart.sh

Skriptihierarkia ja -riippuvuudet alla.

devdb_restart.sh
    devdb_down.sh tuhoaa harjadb Docker-kontin ja tietokannan sen mukana
    devdb_up.sh luo harjadb Docker-kontin 
        devdb_testidata.sh ajaa tietokantasisällön tietokantaan, ei toimi omana skriptinään
            testidata.sql
