

# Väylän Harja-järjestelmä #

Projekti on client/server, jossa serveri on Clojure sovellus (http-kit) ja
client on ClojureScript sovellus, joka käyttää Reagentia, OpenLayersiä ja Bootstrap CSSää.

Tietokantana PostgreSQL PostGIS laajennoksella. Hostaus Solitan infrassa.

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
* [Docker compose](#docker-compose)
  * [Kirjautuminen ja ModHeader](#kirjautuminen-ja-modheader)
  * [Docker - paikallinen kehitysympäristö](#docker---paikallinen-kehitysympäristö)
  * [Docker compose - paikallinen kehitysympäristö](#docker-compose---paikallinen-kehitysympäristö)
      * [Ongelmia](#ongelmia)
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
  * [Staging tietokannan sisällön muokkaus](#staging-tietokannan-sisällön-muokkaus)
  * [Tieverkon tuonti kantaan](#tieverkon-tuonti-kantaan)
  * [Labyrintin SMS-gateway](#labyrintin-sms-gateway)
  * [Väylän Harja-järjestelmän laadunseurantatyökalu](#väylän-harja-järjestelmän-laadunseurantatyökalu-)
  * [FIM](#fim)
  * [ActiveMQ Artemis](#activemq-artemis)
* [Harvoin tarvittavaa (jos koskaan)](#harvoin-tarvittavaa-jos-koskaan)
  * [Autogeneroi nuolikuvat SVG:nä](#autogeneroi-nuolikuvat-svgnä)
  * [Konvertoi SVG kuvia PNG:ksi](#konvertoi-svg-kuvia-pngksi)
  * [Lisääminen `.harja` hakemistoon](#lisääminen-harja-hakemistoon-)
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
├── harja-jenkins
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

- .circleci/                (CircleCi konfiguraatio, mm. docker [imaget](.circleci/README.md) käytössä CircleCi ymperistössä)

## Kehitysympäristön pystyttäminen

#### Paikallisesti

1. Asenna Leiningen: http://leiningen.org/ tai `brew install leiningen`
2. Asenna Docker: www.docker.com/
3. Laita docker käyntiin sanomalla /harja/tietokanta kansiossa

    `sh devdb_up.sh`

4. Asenna tarvittavat työkalut `npm ci` 
   * Huom: ```npm ci``` -komento asentaa package-lock.json tiedostoon jäädytetyt paketit.
   * Mikäli päivität paketin package.jsoniin, aja ```npm install```, ja committoi päivitetty package-lock.json.
5. Hae harja-testidata repositoriosta (Internal [*1](#*1)) .harja -kansio ja aseta se samaan hakemistoon harjan repositorion kanssa.
6. Siirry harja-projektin juureen. Käännä backend & käynnistä REPL:

    `lein do clean, compile, repl`

7. Käännä frontend ja käynnistä Figwheel:

    `sh kaynnista_harja_front_dev.sh`

Harjan pitäisi olla käynnissä ja vastata osoitteesta `localhost:3000`
Jos saat "Ei käyttöoikeutta", tarvitset ModHeader-selainlaajennoksen johon määritellään Harja-käyttäjän roolit. 
Jos haluat kokeilla ilman Modheaderia tai muuta vastaavaa plugaria, niin voit asettaa env muuttujan `export HARJA_SALLI_OLETUSKAYTTAJA=true`
ja restartoida backend `lein repl` kommennolla. 

8. Paikallisesti kun ajat Cypress testejä, on syytä asettaa asetukset.edn:ssä HARJA_SALLI_OLETUSKAYTTAJA" true
Muista myös käynnistää REPL uudestaan, kun muutat asetukset.edn tiedostoa

9. Kaynnista Cypress e2e testi ympäristö. Kun backend ja frontend ovat päällä, 6., 7. ja 8.
   askeleiden mukaan, voit käynnistää cypress e2e interaktiivisen ympäristön:

   `sh kaynnista_cypress.sh`




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

**Ohjeet:**  
https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry#authenticating-with-a-personal-access-token-classic

## GitHub Actions & Docker
Lue: [.github/docker/README.md](.github/docker/README.md)




# Docker compose

1. Asenna docker ja docker compose
2. Jos olet linuxilla, niin lisää itsesi `docker` ryhmään, jos näin ei jo ole. Lisää tietoja
   [täältä](https://docs.docker.com/engine/install/linux-postinstall/#manage-docker-as-a-non-root-user)
3. Aja `bash sh/dc/aja-harja-dokkerissa.sh`

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

Kannan restart 

```bash
sh devdb_restart.sh
```

Kanta alas 

```bash
sh devdb_down.sh
```

Paikallisen kehitysympäristön tarvitsemat palvelut voi käynnistää Dockerilla.
Tietokanta tarvitaan aina. ActiveMQ ei ole pakollinen, jos ei testaa integraatioita. Jos ActiveMQ
ei ole päällä, sovellus logittaa virheitä jos JMS brokeriin ei saada yhteyttä, mutta se on OK. 

Jos testaat paikallisesti JMS-jonoja ja erityisesti ITMF:ään, katso lisäohjeita tiedostosta test/clj.harja.integraatio

* Tietokanta: ks. `tietokanta/devdb_up.sh` ja `tietokanta/devdb_down.sh`
* ActiveMQ: `docker run -p 127.0.0.1:61616:61616 -p 127.0.0.1:8161:8161 --name harja_activemq -dit solita/harja-activemq:5.15.9`

Kantaimagen päivitys: docker pull solita/harjadb

Voit myös käynnistää Harjan kehityskannan ja ActiveMQ:n ajamalla docker-compose up. (käytä mieluummin ym. sh devdb_* skriptejä.)

## Docker compose - paikallinen kehitysympäristö

Docker compose:n konfiguroimisessa on käytetty versiota kaksi kolmosen sijasta, koska kakkonen sopii paikalliseen devaukseen
paremmin. 

- https://github.com/docker/compose/issues/4513
- https://goldmann.pl/blog/2014/09/11/resource-management-in-docker/#_cpu

MAC käyttäjillä saattaa olla hieman hankaluuksia Docker composen hitauden kanssa, koska datan siirtäminen hostin (Macin) ja
konttien välillä on melkoisen hidasta, mutta tämä ongelma saatetaan korjata joskus
 
- https://www.amazee.io/blog/post/docker-on-mac-performance-docker-machine-vs-docker-for-mac
- https://docs.docker.com/docker-for-mac/osxfs-caching/


Docker composea ja leiningenin perffiä on yritetty parantaa joiltain osin MAC käyttäjille tämän hitauden takia.

- Käytetään `delegated` voluumia docker composessa
- Kakutetaan leiningenin trampoliinit käyttämällä `LEIN_FAST_TRAMPOLINE` env muuttujaa
  - https://github.com/technomancy/leiningen/wiki/Faster

#### Ongelmia

- Uudet konfiguraatiot ei heijastu konteissa.
  - Kokeile poistaa trampoliini cachet. Ovat kansiossa `target/trampolines`

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

Harjassa on kolme eritasoista test-suitea: fronttitestit (phantom), palvelutestit (backend) ja
e2e testit (erillisessä projektissa). Lisäksi nykyään on myös Cypress:illä tehtyjä e2e-testejä, joita on hyvä suosia. Uusia Selenium-testejä ei kannata enää kirjoitella.

### Fronttitestit

Fronttitestit ajetaan komennolla: `lein doo phantom test`
Taikka näin:  `lein with-profile +test doo phantom test once`

Varmista että phantomjs on asennettu, katso https://github.com/bensu/doo ja ohjeet mistä voi saada 
phantomjs komennon. Asenna phantomjs esim komennolla (mac): `brew install phantomjs` 

Laadunseurantatyökalun testit ajetaan komennolla: `lein doo phantom laadunseuranta-test`
Tai eri profiililla: `lein with-profile +test doo phantom laadunseuranta-test`

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

End-to-end testit ajetaan harjan testiympäristöä vasten Seleniumilla. Tämän test suiten
tarkoituksena on tehdä perustason smoke test, joka varmistaa että kaikki osiot latautuvat
oikein eikä harja räsähdä.

Uusille näkymille lisätään testi, jossa näkymään navigoidaan ja tarkistetaan jotain yksinkertaista
sivun rakenteesta.

## Debug lokituksen näyttäminen

Muokkaa asetukset.edn:aa ja muuta rivillä: 


## Tietokanta

Tietokannan määrittely ja migraatio (SQL tiedostot ja flyway taskit) ovat harja-repositorion kansiossa tietokanta


## Staging tietokannan sisällön muokkaus

* Lisää itsellesi tiedosto ~/.ssh/config johon sisällöksi:
Host harja-*-test
  ProxyCommand ssh harja-jenkins.solitaservices.fi -W %h:%p

Host harja-*-stg
  ProxyCommand ssh harja-jenkins.solitaservices.fi -W %h:%p

* Sourceta uusi config tai avaa uusi terminaali-ikkuna.

* Avaa VPN putki.

* Luo itsellesi SSH-avainpari ja pyydä tuttuja laittamaan julkinen avain palvelimelle.

ssh -L7777:localhost:5432 harja-db1-stg
 * Luo yhteys esim. käyttämäsi IDE:n avulla,
    * tietokanta: harja, username: flyway salasana: kysy tutuilta


## Tieverkon tuonti kantaan

Replissä: (harja.palvelin.main/with-db db (harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko/vie-tieverkko-kantaan db "file:/.../harja-testidata/shp/Tieosoiteverkko/PTK_tieosoiteverkko.shp"))

## Labyrintin SMS-gateway

Harja käyttää Labyrintin SMS-gatewaytä SMS-viestien lähettämiseen. Labyrintin komponentista on olemassa kaksi eri versiota: Labyrintti ja FeikkiLabyrintti. FeikkiLabyrintti on käytössä vain lokaalissa kehitysympäristössä ja se ainoastaan logittaa viestit REPLiin.

Jos haluat kehityskäytössä testata SMS-gatewayta, vaihda main.clj tiedostosta oikea Labyrinttikomponentti käyttöön feikin sijasta.

SMS:n lähetys kehitystympäristöstä vaatii SSH-putken, jonka voi avata seuraavalla komennolla: ssh -L 28080:gw.labyrintti.com:28080 harja-app1-stg

Huom! Tätä ei saa enää käyttää kuin hätätilanteessa. Labyrintin liikenne ohjautuu tällöin devausympäristöön
tuotannon sijasta. Oikeiden Labyrintin SMS viestien vastaanottoa voi testata tekemällä reverse SSH-tunneli
harja-front1-stg palvelimelle sekä muuttamalla NginX:n reititys osoittamaan
harja-app1-stg palvelimen sijasta localhostin SSH tunnelin porttiin.

1. Avaa reverse SSH-tunneli molemmille tuotannon fronttipalvelimille:
harja-front1 palvelimelle: ssh -R 6666:localhost:8000 harja-front1
harja-front2 palvelimelle: ssh -R 6666:localhost:8000 harja-front2
2. Avaa NginX:n konfiguraatio: sudo vi /etc/nginx/conf.d/site.conf
3. Vaihda SMS-käsittelijä upstreamiin localhost:6666                                                           
upstream sms-kasittelija {
   server localhost:6666;
}
4. Käynnistä nginx uudestan: sudo service nginx restart
5. Luo SSH-tunneli: ssh -L 28080:gw.labyrintti.com:28080 harja-app1-stg
6. Lähetä tekstiviesti numeroon +358 50 9023530
-> Viesti pitäisi välittyä REPL:n

## Väylän Harja-järjestelmän laadunseurantatyökalu #

Toisessa serverissä pyörii Harjan laadunseurantatyökalu, jonka avulla tieverkon kunnossapitoa voidaan valvoa ja raportoida tiestön kuntoon liittyviä havaintoja ja mittauksia.

Käyttöliittymän kääntäminen ja ajaminen kansiosta /harja: ks. sh kaynnista_harja_front_dev.sh

Avaa selain http://localhost:3000/laadunseuranta/

Palvelin käynnistyy kun Harja käynnistetään.

## FIM

Harja käyttää FIM:iä käyttäjätietojen hakemiseen. FIM-komponentista on kaksi eri versiota: FIM ja FIMDEV. FIMDEV on käytössä vain lokaalissa kehitysympäristössä ja se palauttaa aina fim.edn-tiedoston sisällön.

Oikean FIM:n testikäyttö:
1. Määrittele asetukset.edn:n FIM:n URL:ksi https://localhost:6666/FIMDEV/SimpleREST4FIM/1/Group.svc/getGroupUsersFromEntitity sekä poista :tiedosto avain.
2. Avaa SSH-yhteys ssh -L6666:testioag.vayla.fi:443 harja-app1-stg

## ActiveMQ Artemis

Harja käyttää tieliikenneilmoituksiin ja toimenpidekuittausten välitykseen JMS-jonoja.
Brokerina käytetään ActiveMQ Artemista.  
CI-putken integraatiotesteissä käytetään dockeroitua ActiveMQ Artemis -palvelinta, jonka voi käynnistää myös paikallisesti yksikkötestien ajamista varten.

1. Navigoi ```.github/docker/``` kansioon
2. Aja ```$ docker compose up --wait activemq-artemis-itmf```
3. ActiveMQ Artemis on nyt käytettävissä osoitteessa ```tcp://localhost:61616```
   * Web-konsoli on osoitteessa ```http://localhost:8161```
   * Käyttäjätunnus: admin ja salasana: admin
4. Ajaaksesi integraatiotestit paikallisesti, anna testejä varten oikeat ympäristömuuttujat:  
   ```$ HARJA_ITMF_BROKER_PORT=61616 HARJA_ITMF_BROKER_AI_PORT=8161 lein test :integraatio```
5. Sammuta ActiveMQ Artemis:  
   ```$ cd .github/docker/ && docker compose down activemq-artemis-itmf```

**Lisätietoja:** 
* [.github/docker/activemq-artemis/README.md](.github/docker/activemq-artemis/README.md)
* [.github/docker/README.md](.github/docker/README.md)

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

## Lisääminen `.harja` hakemistoon 

Mahdollinen use case on ulkoisen palvelun lisääminen. Hetkellä `.harja` hakemistossa pidetään esim. palveluiden 
salasanat. Jos tarvitse lisätä palvelua, ehkä täytyy lisätä myös salasana tiedosto `.harja` hakemistoon. Askeleet: 
* Tee muutoksia `.harja` hakemistoon, `harja-testidata` Internal [*1](#*1) repossa
* Päivitä `harja-app` hakemisto, `harja-docker` OnPrem [*1](#*1) repossa. Sieltä löytyvät ohjeet. Muista buildata
  "harja-app" docker imagen ja pushata `hub.docker.com`:iin.
* Katso `.circleci` hakemisto `harja` repossa, ja siellä [config.yml](.circleci/config.yml) tiedosto jossa lukee 
  mitä buildi ja testaukset tekevät. Löydä paikat missä tehdään "dummy" `.harja` hakemisto ja sen sisältö. Sinne 
  ehkä tarvitse lisätä ne tiedostot mitä haluamme. 

<a name="*1">*1</a> Solita-intenal/OnPrem repo - etsi projektin sisäisestä [wikistä](https://knowledge.solita.fi/pages/viewpage.action?pageId=136218429)

## Lisenssi
https://github.com/finnishtransportagency/harja/blob/develop/LICENSE.txt
