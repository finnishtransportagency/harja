# Harja tietokanta

Sisältää Harja-järjestelmän tietokantamääritykset

Uudet migraatiot lisätään kansioon src\main\resources\db\migration

Migraatiot voi ajaan Maven buildin kautta komennolla: mvn clean compile flyway:migrate

Testitietokannan voi päivittää ajamalla komennon: mvn clean compile -Pharjatest flyway:migrate

## Tietokantaan pääsy omalta koneelta

> ssh -L 7777:harja-db1-test:5432 harja-jenkins.solitaservices.fi

sitten psql komento:

> psql -h localhost -p 7777 -U flyway harja

ja flyway salasana migrate123

