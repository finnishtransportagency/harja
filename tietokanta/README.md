# Harja tietokanta

Sisältää Harja-järjestelmän tietokantamääritykset

Uudet migraatiot lisätään kansioon src\main\resources\db\migration

Migraatiot voi ajaan Maven buildin kautta komennolla: mvn clean compile flyway:migrate

Testitietokannan voi päivittää ajamalla komennon: mvn clean compile -Pharjatest flyway:migrate

## Tietokantaan pääsy omalta koneelta

> ssh -L 7777:harja-db1-test:5432 harja-jenkins.solitaservices.fi

sitten psql komento:

> psql -h localhost -p 7777 -U flyway harja


## Tietokannan lokitus paikallisessa Docker-kontissa. Tällä sisään kantakoneelle:
> docker exec -it harjadb /bin/bash
### Aseta haluamasi lokitustaso, ohjeet: https://www.postgresql.org/docs/9.5/static/runtime-config-logging.html
> nano /etc/postgresql/9.5/main/postgresql.conf
### Jos nano tai muu haluamasi tekstinkäsittelyohjelma puuttui
> apt-get update
> apt-get install nano
### log-tason muutoksen voimaannuttamiseksi, restart possu
> service postgresql restart
### tarkkaile lokin häntää
> tail -f /var/log/postgresql/postgresql-9.5-main.log

Jos osaat ja ehdit, voit tehdä yo. tehtäviin skriptejä esim Sedin avulla, esim. log-localdb.sh