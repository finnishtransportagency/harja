Tässä ajetaan Harjan front-, back- ja Cypress E2e-testit. Harjan tarvitsemat ympäristömuuttujat on määritetty
[CircleCI:n projektikohtasissa ympäristömuuttujissa](https://circleci.com/docs/2.0/env-vars/#setting-an-environment-variable-in-a-project)

Harjan testit käyttävät tietokantaa, joten jokaisessa jobissa pitää odotella ensin, että Possu on käynnissä ja siihen
on ajettu viimesimmät muutokset. 

Ennen jobin ajamista tarkastetaan, että git branch ei sisällä `&` merkkiä sillä se muuten hajottaisi tapahtumien
lähettelyt.