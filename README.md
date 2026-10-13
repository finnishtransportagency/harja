
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/finnishtransportagency/harja)

# Väylän Harja-järjestelmä #

Projekti on client/server, jossa serveri on Clojure sovellus (http-kit) ja
client on ClojureScript sovellus, joka käyttää Reagentia, OpenLayersiä ja Bootstrap CSSää.

Tietokantana on PostgreSQL PostGIS-laajennoksella. Sovellus ajetaan AWS:n ECS/Fargatessa.
Buildit, testaus ja julkaisu hoidetaan GitHub Actionsilla. Sovelluskontin toteutus on
[`aws/fargate/`](aws/fargate)-hakemistossa ja workflow't [`.github/workflows/`](.github/workflows)-hakemistossa.

Autentikointiin käytetään KOKAa.


## Sisältö
<!-- TOC -->
* [Väylän Harja-järjestelmä](#väylän-harja-järjestelmä-)
  * [Sisältö](#sisältö)
  * [Hakemistorakenne](#hakemistorakenne-)
  * [Kehitysympäristön pystyttäminen](#kehitysympäristön-pystyttäminen)
      * [Paikallisesti](#paikallisesti)
* [Docker](#docker)
  * [GitHub Container Registry](#github-container-registry)
    * [Kehittäjän kirjautuminen Container Registryyn](#kehittäjän-kirjautuminen-container-registryyn)
  * [GitHub Actions & Docker](#github-actions--docker)
  * [Kirjautuminen ja ModHeader](#kirjautuminen-ja-modheader)
  * [Docker - paikallinen kehitysympäristö](#docker---paikallinen-kehitysympäristö)
  * [ActiveMQ Artemis](#activemq-artemis)
  * [Dokumentaatio](#dokumentaatio)
    * [Uuden kehittäjän ohjeet](#uuden-kehittäjän-ohjeet)
    * [Tietokanta](#tietokanta)
      * [Tietokantataulujen nimeäminen](#tietokantataulujen-nimeäminen)
    * [Namespacet](#namespacet)
    * [Tiedostojen nimeäminen](#tiedostojen-nimeäminen)
      * [Miksi?](#miksi)
  * [Testaus](#testaus)
    * [Fronttitestit](#fronttitestit)
    * [Backend-testit](#backend-testit)
    * [End-to-end testit](#end-to-end-testit)
  * [Debug lokituksen näyttäminen](#debug-lokituksen-näyttäminen)
  * [Tietokanta](#tietokanta-1)
  * [Tieverkon tuonti kantaan](#tieverkon-tuonti-kantaan)
  * [Laadunseurannan paikalliskäyttö](#laadunseurannan-paikalliskäyttö)
  * [FIM](#fim)
* [Harvoin tarvittavaa (jos koskaan)](#harvoin-tarvittavaa-jos-koskaan)
  * [Autogeneroi nuolikuvat SVG:nä](#autogeneroi-nuolikuvat-svgnä)
  * [Konvertoi SVG kuvia PNG:ksi](#konvertoi-svg-kuvia-pngksi)
  * [`.harja`-hakemiston ylläpito](#harja-hakemiston-ylläpito)
  * [Lisenssi](#lisenssi)
<!-- TOC -->

## Hakemistorakenne ##

Kehitystyökaluissa olevan [virheen](https://github.com/emezeske/lein-cljsbuild/issues/490) vuoksi työkansio ei saa sijaita sellaisen kansion alla, jonka nimessä on - -merkki. 
Eli nimeä yläkansio, jonne harja ja muut repot kloonataan pelkillä kirjaimilla ja numeroilla ilman erotinmerkkejä. 
Esimerkiksi näin:
```
~/work ❯❯❯ tree -aL 1 harjaroot
harjaroot
├── .harja
├── harja
├── harja-infra
└── harja-testidata
```

Harja repon hakemistorakenne:

- README                    (yleinen readme)

- src/                      (kaikki lähdekoodit)
  - cljc/                   (palvelimen ja asiakkaan jaettu koodi)
  - cljs/                   (asiakaspuolen ClojureScript koodi)
    - harja/asiakas/
      - ui/                 (yleisiä UI komponentteja ja koodia)
      - nakymat/            (UI näkymät)
      - kommunikointi.cljs  (serverikutsujen pääpiste)
      - main.cljs           (aloituspiste, jota sivun latauduttua kutsutaan)
  - cljs-{dev|prod}/        (tuotanto/kehitysbuildeille spesifinen cljs source)
      - harja/asiakas/
        - lokitus.cljs      (lokitus, tuotantoversiossa no-op, kehitysversiossa console.log tms)
  - clj/                    (palvelimen koodi)
    - harja/palvelin/
      - komponentit/        (Yleiset komponentit: tietokanta, todennus, HTTP-palvelin, jne)
      - lokitus/            (Logitukseen liittyvää koodia)
      - integraatiot/       (Integraatioiden toteutukset)
      - api/                (Harja API endpointit ja tukikoodi)
      - palvelut/           (Harja asiakkaalle palveluja tarjoavat EDN endpointit)
      - main.cljs           (palvelimen aloituspiste)

- (dev-)resources/          (web-puolen resurssit)
  - css/                    (ulkoiset css tiedostot)
  - js/                     (ulkoiset javascript tiedostot)

- .github/                  (GitHub Actions ja Docker-työkalut)
  - workflows/              (build-, testaus- ja julkaisu-workflow't)
  - actions/                (workflow'iden käyttämät composite actionit)
  - docker/                 (paikallis- ja CI-käytön [imaget ja ohjeet](.github/docker/README.md))
- aws/fargate/              (AWS:ssä ajettavan sovelluskontin toteutus)

## Kehitysympäristön pystyttäminen

#### Paikallisesti

1. Asenna Leiningen: http://leiningen.org/ tai `brew install leiningen`
2. Asenna Docker ja Docker Compose: https://www.docker.com/
3. Käynnistä Docker ja aja projektin juuresta:
  `(cd tietokanta && ./devdb_up.sh)`
  Skripti alustaa tietokannan migraatioilla ja testidatalla. Tietokannan pitää olla päällä ja alustettu ennen paikallisen buildin tekemistä.

4. Asenna tarvittavat työkalut projektin juuressa: `npm ci`
   * Huom: `.npmrc`-tiedosto sisältää `ignore-scripts=true`, joka estää npm lifecycle -skriptien automaattisen ajon (supply chain -suojaus).
   * `npm ci` asentaa package-lock.json tiedostoon jäädytetyt paketit.
   * Mikäli päivität paketin package.jsoniin, aja ```npm install```, ja committoi päivitetty package-lock.json.
5. Hae `harja-testidata`-repositoriosta ([sisäinen repo *1](#*1)) `.harja`-kansio
   Harja-repositorion rinnalle hakemistorakenteen esimerkin mukaisesti. Katso myös
   [`.harja`-hakemiston ylläpito-ohje](#harja-hakemiston-ylläpito).
6. Siirry harja-projektin juureen. Käännä backend & käynnistä REPL:

    `lein do clean, compile, repl`

7. Käännä frontend ja käynnistä Figwheel:

    `bash kaynnista_harja_front_dev.sh`

    Harjan pitäisi olla käynnissä ja vastata osoitteesta `localhost:3000`
    Jos saat "Ei käyttöoikeutta", tarvitset ModHeader-selainlaajennoksen johon määritellään Harja-käyttäjän roolit. 
    Jos haluat kokeilla ilman Modheaderia tai muuta vastaavaa plugaria, niin voit asettaa env muuttujan `export HARJA_SALLI_OLETUSKAYTTAJA=true`
    ja restartoida backend `lein repl` kommennolla. 

8. Salli paikallinen oletuskäyttäjä Cypress-testejä varten: aseta backendin terminaalissa
   `export HARJA_SALLI_OLETUSKAYTTAJA=true` ja käynnistä REPL uudelleen. Asetus on vain
   paikalliskäyttöön, ei julkaistaviin ympäristöihin.

9. Kun backend ja frontend ovat päällä vaiheiden 6–8 mukaisesti, käynnistä Cypressin
   interaktiivinen e2e-ympäristö projektin juuresta:

    `bash kaynnista_cypress.sh`

    Skripti asentaa tarvittaessa npm-riippuvuudet ja Cypressin binäärin automaattisesti.
    Testit ovat [`cypress/e2e/`](cypress/e2e)-hakemistossa.


# Docker
## GitHub Container Registry

### Kehittäjän kirjautuminen Container Registryyn
Harjassa käytetään Harjan GitHub repositorioon julkaistuja Docker imageita.
Imaget on varastoitu GitHub Packagesiin, johon pääsee käsiksi riittävillä oikeuksilla.

GitHub Packages (eli käytännössä Container Registry) tukee kirjautumista ainoastaan classic personal access tokenilla.  
Lue alla olevat ohjeet tarkoin. Kehittäjä tarvitsee read/write/delete oikeudet Packages scopeen.  
Lue tarkasti varsinkin ohje, jossa neuvotaan pienentämään tokenin access scopea, jotta tokenilla ei ole liian laajat oikeudet.
>Note: By default, when you select the write:packages scope for your personal access token (classic) in the user interface, the repo scope will also be selected.
>The repo scope offers unnecessary and broad access, which we recommend you avoid using for GitHub Actions workflows in particular.
>For more information, see "Security hardening for GitHub Actions."
>As a workaround, you can select just the write:packages scope for your personal access token (classic) in the user interface with this url: https://github.com/settings/tokens/new?scopes=write:packages.

* **Ohjeet tokenin luomiseen ja kirjautumiseen:**  
  * https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry#authenticating-with-a-personal-access-token-classic

* **Tokenin käyttöönotto (tärkeä)**
  * Lopuksi, luotu token täytyy vielä auktorisoida Väyläviraston GitHub-organisaatiolle, jotta sitä voi käyttää.
  * Navigoi: https://github.com/settings/tokens
    * Paina tokenin kohdalla "Configure SSO" ja luvita tokenin käyttö finnistransportagency-organisaatiolle.

## GitHub Actions & Docker
Lue: [.github/docker/README.md](.github/docker/README.md)


## Kirjautuminen ja ModHeader

Harja käyttää Väylän extranetista tulevia headereita kirjautumiseen.
Käytä ModHeader tai vastaavaa asettaaksesi itselle oikeudet paikallisessa ympäristössä, pyydä apua tiimiläiseltä pikaviestimen kautta miten homma tehdään.

Oikeudet on määritelty tiedostossa resources/roolit.xslx: 1. välilehti kertoo oikeudet, 2. välilehti roolit
Harjan harja.domain.oikeudet.makrot luo Excelin pohjalta roolit käytettäväksi koodista.
Käyttäjällä voi olla useita rooleja. Oikeustarkistuksia tehdään sekä frontissa että backissä. Frontissa yleensä
piilotetaan tai disabloidaan kontrollit joihin ei ole oikeutta. Tämän lisäksi backissä vaaditaan
luku- ja/tai kirjoitusoikeus tietyn tiedon käsittelyyn.

Lisätietoa käyttäjärooleista ohjelmistokehittäjän näkökulmasta löytyy Knowledgesta otsikolla Autentikointi ja autorisointi

Seuraavat headerit tuettuna:

* OAM_REMOTE_USER: käyttäjätunnus, esim. LX123123
* OAM_GROUPS: pilkulla erotettu lista ryhmistä (roolit ja niiden linkit). Esim:
    * Järjestelmävastaava: Jarjestelmavastaava
    * ELY urakanvalvoja: <urakan-SAMPO-ID>_ELY_Urakanvalvoja
    * Urakoitisijan laatupäällikkö: <urakoitsijan-ytunnus>_Laatupaallikko
    * Urakan vastuuhenkilö: <urakan-sampoid>_vastuuhenkilo
* OAM_ORGANIZATION: Organisaation nimi, esim. "Väylä" tai "YIT Rakennus Oy"
* OAM_DEPARTMENTNUMBER: Organisaation ELYNUMERO, esim. 12 (POP ELY)
* OAM_USER_FIRST_NAME: Etunimi
* OAM_USER_LAST_NAME: Sukunimi
* OAM_USER_MAIL: Sähköpostiosoite
* OAM_USER_MOBILE: Puhelinnumero

Staging-ympäristössä voidaan lisäksi testata eri rooleja testitunnuksilla,
jotka löytyvät toisesta Excelistä, mitä ei ole Harjan repossa (ei salasanoja repoon).

## Docker - paikallinen kehitysympäristö

Kannan uudelleenluonti (vanha paikallinen kanta poistetaan ja testidata alustetaan uudelleen).
Aja projektin juuresta:

```bash
(cd tietokanta && ./devdb_restart.sh)
```

Kanta alas projektin juuresta (poistaa paikallisen tietokantakontin):

```bash
(cd tietokanta && ./devdb_down.sh)
```

Paikallisen kehitysympäristön tarvitsemat palvelut voi käynnistää Dockerilla.
Tietokanta tarvitaan aina. ActiveMQ ei ole pakollinen, jos ei testaa integraatioita. Jos ActiveMQ
ei ole päällä, sovellus logittaa virheitä jos JMS brokeriin ei saada yhteyttä, mutta se on OK. 

Jos testaat paikallisesti JMS-jonoja ja erityisesti ITMF:ään, katso lisäohjeita tiedostosta test/clj.harja.integraatio

* Tietokanta: ks. `tietokanta/devdb_up.sh` ja `tietokanta/devdb_down.sh`
* ActiveMQ Artemis: `(cd .github/docker && docker compose up -d --wait activemq-artemis-itmf)` projektin juuresta

Kantaimagen päivitys: docker pull ghcr.io/finnishtransportagency/harja_harjadb:latest

## ActiveMQ Artemis

Harja käyttää tieliikenneilmoituksiin ja toimenpidekuittausten välitykseen JMS-jonoja.
Brokerina käytetään ActiveMQ Artemista.  
CI-putken integraatiotesteissä käytetään dockeroitua ActiveMQ Artemis -palvelinta, jonka voi käynnistää myös paikallisesti yksikkötestien ajamista varten.

1. Aja seuraavat komennot Harja-projektin juuressa.
2. Käynnistä broker: `(cd .github/docker && docker compose up -d --wait activemq-artemis-itmf)`
3. ActiveMQ Artemis on nyt käytettävissä osoitteessa ```tcp://localhost:61616```
    * Web-konsoli on osoitteessa ```http://localhost:8161```
    * Käyttäjätunnus: admin ja salasana: admin
4. Ajaaksesi integraatiotestit paikallisesti, anna testejä varten oikeat ympäristömuuttujat:  
   `HARJA_ITMF_BROKER_PORT=61616 HARJA_ITMF_BROKER_AI_PORT=8161 lein test :integraatio`
5. Sammuta ActiveMQ Artemis:  
   `(cd .github/docker && docker compose down activemq-artemis-itmf)`

**Lisätietoja:**
* [.github/docker/activemq-artemis/README.md](.github/docker/activemq-artemis/README.md)
* [.github/docker/README.md](.github/docker/README.md)


## Dokumentaatio

### Uuden kehittäjän ohjeet

 - Uuden harja-tiimiläisen ohjeet löytyy knowledgestä otsikolla "Uudelle tiimiläiselle".
 - Täältä löytyy tarvittavaa perehdytystä domainista, kehityskäytänteistä ja muusta.

### Tietokanta

Tietokantataulut dokumentoimaan antamalla niille kommentti migraatiossa luonnin yhteydessä. Kommenttiin lisätään seuraavat asiat:
- Mikä on taulun konsepti?
- Miten asiaks ymmärtää nämä käsitteet?
- Mikä on taulun olemassaolon syy?
- Viittaukset käsitteellisellä tasolla muihin konsepteihin. Ei siis viitteiden kuvausta, vaan käsitteellisesti miksi viitteet ovat olemassa.
- Mikäli tarpeen voi kuvata, mistä data syntyy.

Tässä dokumentaatiossa käytetään domainkieltä eikä teknistä kuvausta.

Dokumentaatio tauluille lisätään repeatable migraatiossa: R__Dokumentaatio.sql. Tähän migraatioon lisätään kaikki uudet kommentit tauluille.

Dokumentaatio voidaan lisätä kyselyllä: `COMMENT ON TABLE [TAULU] IS E'Rivi 1 \n Rivi 2'`

Dokumentaation saa näkyviin esim. kyselyllä: `SELECT obj_description('public.[TAULU]' :: REGCLASS);`

#### Tietokantataulujen nimeäminen
Sovittiin tiimin kesken, että uusien isompien kokonaisuuksien osalta nimetään osa-alueen taulut yhteisellä etuliitteellä. Esimerkiksi pot2_, pot2_mk_ sekä lupaus_. Näin on helppo löytää kaikki tiettyyn osa-alueeseen liittyvät taulut.

### Namespacet
Jokaisen namespacen alkuun kirjataan seuraavat asiat:
- Olemassa olon syy?
- Listaus minkä domain-käsitteiden kanssa toimitaan tässä nimiavaruudessa.
- Mitkä ovat pääpalvelut, jotka tämä nimiavaruus tarjoaa? Mistä kannattaa lähteä liikenteeseen?
- Toistuvat käsitteet koodin kannalta, tärkeät keywordit.

### Tiedostojen nimeäminen
Tiimin päätös 9/2021:
- Alkuosa: ominaisuuden nimi, mielellään yksikössä
- Loppuosa: kerroksen nimi

esim.

```
lupaus_palvelu.clj
lupaus_kyselyt.clj
lupaus_domain.cljc

lupaus_palvelu_test.clj
lupaus_domain_test.clj

lupaus_kyselyt.sql
lupaus_testidata.sql

lupaus_tiedot.cljs
lupaus_nakyma.cljs
lupaus_tyylit.less

valikatselmus_palvelu.clj
...
```

#### Miksi?

Uniikit nimet helpottavat tiedostojen etsimistä.

Nimeä voi käyttää sellaisenaan require-aliaksena ilman törmäyksiä:
```
(:require
  [harja.tiedot.urakka.lupaus-tiedot :as lupaus-tiedot]
  [harja.domain.lupaus-domain :as lupaus-domain]
  [harja.kyselyt.lupaus-kyselyt :as lupaus-kyselyt]
```
Ehdotan, että uudet ominaisuudet nimetään tällä tavalla.
Halutessaan voi myös nimetä tiedostoja uudelleen, jos koskee johonkin vanhaan toiminnallisuuteen.

## Testaus

Harjassa on kolme eritasoista test-suitea: fronttitestit (PhantomJS/doo),
palvelutestit (backend) ja e2e-testit (Cypress). Cypress-testit ovat tämän repositorion
[`cypress/e2e/`](cypress/e2e)-hakemistossa. PhantomJS/doo on edelleen käytössä fronttitesteissä, mutta se tullaan poistamaan.

### Fronttitestit

Fronttitestit ajetaan komennolla: `lein with-profile +phantomjs doo phantom test once`

Varmista että phantomjs on asennettu, katso https://github.com/bensu/doo ja ohjeet mistä voi saada 
phantomjs komennon. Asenna phantomjs esim komennolla (mac): `brew install phantomjs` 

Laadunseurantatyökalun testit ajetaan komennolla: `lein with-profile +phantomjs doo phantom laadunseuranta-test`

Odotetaan, että kaikilla frontin nimiavaruuksilla on testitiedosto ja vähintään yksi
testi.

Kaikilla yleiskäyttöisillä peruskomponenteilla (esim. grid, lomake ja eri kenttätyypit) tulisi
olla hyvät testit, koska niitä käytetään laajasti koko järjestelmässä.

UI tilan käsittely pitää testata ja tarpeen vaatiessa UI-komponentin renderöinti DOMiin.
Täysiä työnkulkuja, joissa on useita komponentteja, ei tarvitse tässä test suitessa tehdä.

Testaa UI tilassa reaktioiden oikea toiminta ja tuck event käsittely.

### Backend-testit

Backend testit testaavat harjan palvelinta ja käytössä on oikea tietokanta.
Kaikille palveluille on syytä tehdä testi, joka testaa vähintään onnistuvan
tapauksen ja mielellään myös virheellisen kutsun.

Pyritään testaamaan kaikki CRUD operaatiot.

Oikeustarkistuksien olemassaolo varmistetaan palvelukutsuissa, ja puuttuva oikeustarkistus lokitetaan varoitustasolla.
Kehitystiimin linja on, että jokaisen HTTP-palvelun tulee vaatia joko luku- tai kirjoitusoikeus käyttäjältä.

Eri käyttäjien pääsy tiettyyn palveluun on syytä testata myös sen palvelun testeissä.
Testaa palvelukutsuja ainakin kahdella käyttäjällä: sellaisella joka pääsee ja sellaisella
joka ei pääse.

### End-to-end testit

End-to-end-testit tehdään Cypressillä ja ne sijaitsevat [`cypress/e2e/`](cypress/e2e)-hakemistossa.
Käynnistä paikallinen tietokanta, backend ja frontend sekä salli oletuskäyttäjä
[kehitysympäristön ohjeen](#paikallisesti) mukaisesti. Aja sitten projektin juuresta
`bash kaynnista_cypress.sh` ja valitse ajettavat e2e-testit Cypressin käyttöliittymässä.
Paikallinen kohdeosoite on `http://localhost:3000`.

Testien tarkoituksena on varmistaa näkymien latautuminen ja keskeisten työnkulkujen toiminta.

Uusille näkymille lisätään testi, jossa näkymään navigoidaan ja tarkistetaan jotain yksinkertaista
sivun rakenteesta.

## Debug lokituksen näyttäminen

Muokkaa asetukset.edn:aa ja muuta rivillä: 


## Tietokanta

Tietokannan määrittely ja migraatio (SQL tiedostot ja flyway taskit) ovat harja-repositorion kansiossa tietokanta


## Tieverkon tuonti kantaan

Replissä: (harja.palvelin.main/with-db db (harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko/vie-tieverkko-kantaan db "file:/.../harja-testidata/shp/Tieosoiteverkko/PTK_tieosoiteverkko.shp"))

## FIM

Harja käyttää FIM:iä käyttäjätietojen hakemiseen. FIM-komponentista on kaksi eri versiota: FIM ja FIMDEV.
Paikallisessa kehityksessä käytetään FIMDEV:iä: pidä `asetukset.edn`-tiedoston `:fim`-asetuksissa
`:tiedosto "dev-resources/fim.edn"`. FIMDEV palauttaa aina tämän tiedoston testikäyttäjätiedot,
eikä paikalliskäyttö tarvitse SSH-tunnelia.


# Harvoin tarvittavaa (jos koskaan)

## Autogeneroi nuolikuvat SVG:nä
Meillä on nyt Mapen tekemät ikonit myös nuolille, joten tälle ei pitäisi olla tarvetta.
Jos nyt kuitenkin joku käyttää, niin kannattaa myös varmistaa että alla määritellyt värit osuu
puhtaat -namespacessa määriteltyihin.

(def varit {"punainen" "rgb(255,0,0)"
            "oranssi" "rgb(255,128,0)"
            "keltainen" "rgb(255,255,0)"
            "lime" "rgb(128,255,0)"
	    "vihrea" "rgb(0,255,0)"
 	    "turkoosi" "rgb(0,255,128)"
 	    "syaani" "rgb(0,255,255)"
 	    "sininen" "rgb(0,128,255)"
 	    "tummansininen" "rgb(0,0,255)"
 	    "violetti" "rgb(128,0,255)"
 	    "magenta" "rgb(255,0,255)"
 	    "pinkki" "rgb(255,0,128)"})

(for [[vari rgb] varit]
  (spit (str "resources/public/images/nuoli-" vari ".svg")
  	(str "<?xml version=\"1.0\" encoding=\"utf-8\"?>
<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 6 9\" width=\"20px\" height=\"20px\">
   <polygon points=\"5.5,5 0,9 0,7 3,5 0,2 0,0 5.5,5\" style=\"fill:" rgb ";\" />
</svg>")))

## Konvertoi SVG kuvia PNG:ksi

Käytä inkscape sovellusta ilman UI:ta. Muista käyttää täysiä tiedostopolkuja:
> /Applications/Inkscape.app/Contents/Resources/script --without-gui --export-png=/Users/minä/kuva/jossain/image.png /Users/minä/kuva/jossain/image.svg

Fish shellissä koko hakemiston kaikkien kuvien konvertointi:

kun olet hakemistossa, jonka svg kuvat haluat muuntaa:

> for i in *.svg; /Applications/Inkscape.app/Contents/Resources/script --without-gui --export-png=(pwd)/(echo $i | sed 's/\.[^.]*$//').png (pwd)/$i; end

## `.harja`-hakemiston ylläpito

Paikallinen `asetukset.edn` viittaa Harja-repositorion rinnalla olevaan `.harja`-hakemistoon.
Siellä säilytetään esimerkiksi integraatioiden paikallisia asetustiedostoja ja placeholder-salasanoja.

* Hae paikalliskäytön `.harja`-kansio `harja-testidata`-repositoriosta ([sisäinen repo *1](#*1)).
  Kun lisäät uuden tiedoston, päivitä myös sitä käyttävä paikallinen asetus ja sisäisen repon ohje.
* GitHub Actionsin testit luovat erillisen `.harja`-hakemiston testiarvoilla ja tyhjillä tiedostoilla.
  Päivitä tarvittaessa [testi-workflow'n](.github/workflows/reusable_run_app_tests.yml)
  Cypress-vaiheen tiedostojen luonti. Älä käytä oikeita salasanoja näissä testitiedostoissa.
* AWS/Fargatessa asetukset ja kaista-aineisto ladataan S3:sta
  [`entrypoint.sh`](aws/fargate/entrypoint.sh)-skriptillä. Ympäristökohtaiset arvot välitetään
  ympäristömuuttujilla; paikallista `.harja`-hakemistoa ei julkaista sovelluskontin mukana.
  Uuden integraation AWS-asetukset pitää päivittää ympäristön konfiguraatioon erikseen.

Älä lisää salaisuuksia mihinkään repositorioon. Salaisuuksien hallintaan käytetään ainoastaan turvallisia
palveluja ja työkaluja, kuten AWS Secrets manageria.

## Lisenssi
https://github.com/finnishtransportagency/harja/blob/develop/LICENSE.txt
