# Harja tietokanta

Uudet migraatiot lisätään hakemistoon `src/main/resources/db/migration/`.

## Migraatioiden lisääminen

Ennen uuden versioidun migraatiotiedoston lisäämistä tai versionumeron muutosta suorita projektin juurihakemistosta:

```bash
./tietokanta/viimeisin_migraatio.sh
```

Skripti hakee `origin/develop`-haaran tiedot ja näyttää sen suurimman migraationumeron. Nimeä uusi tiedosto muodossa
`V1_<numero>__<kuvaus>.sql` käyttäen seuraavaa numeroa. Uuden migraation version numero ei saa jättää numeroa välistä,
vaan
numeron täytyy olla seuraavaksi suurin numero `origin/develop`-haaran migraatioon verrattuna.

Migraatiot lisätään ja mergetään numerojärjestyksessä. Jos useassa rinnakkaisessa PR:ssä on migraatioita, tarkista aina
PR-branchissä `origin/develop` migraatioiden tilanne scriptin avulla uudelleen ennen mergeä ja muokkaa oman
migraatiotiedostosi numero tarvittaessa oikeanlaiseksi

Jo mergettyjä versioituja `V1_...`-migraatioita ei enää saa muokata eikä poistaa. Käytännössä migraatiota voi kuitenkin
vielä muokata, jos sitä ei ole ehditty vielä deployata johonkin kohdeympäristöön: dev/stg/prod.

`R__...`-migraatiot ovat toistettavia migraatioita, ja niitä voi päivittää jälkikäteen. Flyway havaitsee muutoksen
R-migraatiossa ja ajaa sen uudelleen.

## Tietokantojen salasanat

Kyselepä tiimiläisiltä.

## Paikallinen kehitystietokanta

Suorita komennot projektin juurihakemistosta.

- Käynnistä kanta komennolla `./tietokanta/devdb_up.sh`. Skripti käynnistää `harjadb`-kontin ja ajaa siinä migraatiot
  sekä testidatan.
- Sammuta kanta komennolla `./tietokanta/devdb_down.sh`. Kontin poistaminen poistaa myös paikallisen tietokannan.
- Luo kanta uudelleen tyhjästä komennolla `./tietokanta/devdb_restart.sh`. Skripti poistaa vanhan kontin ja käynnistää
  uuden, minkä jälkeen migraatiot ja testidata alustetaan uudelleen.

