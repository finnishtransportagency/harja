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

## Migraatioiden automaattinen tarkistus

PR-testeissä ajetaan check `Validoi Flyway-migraatiot`, joka vertaa PR:n migraatiomuutoksia base-branchiin
(`.github/scripts/tarkista-migraatiot.js`, Node.js ilman riippuvuuksia).  
Jos migraatioihin ei ole muutoksia, tarkistus menee suoraan läpi. 

**Tarkistus estää:**
- virheelliset tiedostonimet. Sallitut muodot: `V1_<numero>__[kuvaus].sql` (numero on pakollinen, ei etunollia; kuvaus
  vapaaehtoinen, merkit `A-Z a-z 0-9 _ -`), `R__<kuvaus>.sql` ja `afterMigrate.sql`
- versionumeron, joka on jo käytössä (myös eri kuvauksella)
- numeroinnin, joka ei jatka base-branchin suurinta numeroa aukottomasti
- base-branchissa jo olevan `V1_...`-migraation muokkauksen, poiston tai uudelleennimeämisen

Jos olemassa olevaa `V1_...`-migraatiota on tarkoituksella muutettava (esim. sitä ei ole vielä deployattu), lisää PR:ään
label `salli-v1-migraation-muutos` ja pushaa sen jälkeen uusi commit. Label luetaan PR-eventistä, joten pelkkä
uudelleenajo ei huomaa sitä.

Tarkistuksen voi ajaa paikallisesti ennen PR:n avaamista (vain commitoidut muutokset):

```bash
git fetch origin develop
node .github/scripts/tarkista-migraatiot.js origin/develop
```

Skriptin omat testit: `node --test .github/scripts/tarkista-migraatiot.test.js`.

Tarkistus ei estä kahden rinnakkaisen PR:n kilpailutilannetta. 
Se ajetaan PR:n ja sen hetkisen base-branchin merge-commitille, joten develop-haaran muututtua PR on päivitettävä, 
jotta tarkistus ajetaan uudelleen.

## Tietokantojen salasanat

Kyselepä tiimiläisiltä.

## Paikallinen kehitystietokanta

Suorita komennot projektin juurihakemistosta.

- Käynnistä kanta komennolla `./tietokanta/devdb_up.sh`. Skripti käynnistää `harjadb`-kontin ja ajaa siinä migraatiot
  sekä testidatan.
- Sammuta kanta komennolla `./tietokanta/devdb_down.sh`. Kontin poistaminen poistaa myös paikallisen tietokannan.
- Luo kanta uudelleen tyhjästä komennolla `./tietokanta/devdb_restart.sh`. Skripti poistaa vanhan kontin ja käynnistää
  uuden, minkä jälkeen migraatiot ja testidata alustetaan uudelleen.

