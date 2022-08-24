Tässä ajetaan Harjan front-, back- ja Cypress E2e-testit. Harjan tarvitsemat ympäristömuuttujat on määritetty
[CircleCI:n projektikohtasissa ympäristömuuttujissa](https://circleci.com/docs/2.0/env-vars/#setting-an-environment-variable-in-a-project)

Harjan testit käyttävät tietokantaa, joten jokaisessa jobissa pitää odotella ensin, että Possu on käynnissä ja siihen
on ajettu viimesimmät muutokset. 

Jos tarvitse lisätä jotain `.harja` hakemistoon, että on saatavana testi ympäristössä, katso `docker-harja-testit`
hakemisto ja sen [Dockerfile](docker-harja-testit/Dockerfile) ja paikka missä luodaan `.harja` hakemiston. Lisäksi, 
kannattaa etsiä kaikki paikat missä `touch`:lla lisätän "dummy" sisältöö `.harja` hakemistoon, eri skripteissa. 

Jos tarvitse tehdä muutoksia Docker imageihiin (`harja-db`, `harja-tapahtuma`, `harja-testit`, `harja-cypress`), 
muista buildata ja pushata "latest" image `hub.docker.com` :iin. 

Katso CircleCi:n [config.yml](config.yml) että näet mitä eri CircleCi vaihteissa tehdään. 

Ennen jobin ajamista tarkastetaan, että git branch ei sisällä `&` merkkiä sillä se muuten hajottaisi tapahtumien
lähettelyt.