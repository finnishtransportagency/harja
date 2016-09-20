# Liikenneviraston Harja järjestelmä #

[![Build Status](https://travis-ci.org/finnishtransportagency/harja.svg?branch=develop)](https://travis-ci.org/finnishtransportagency/harja)

Projekti on client/server, jossa serveri on Clojure sovellus (http-kit) ja
client on ClojureScript sovellus, joka käyttää Reagentia, OpenLayersiä ja Bootstrap CSSää.

Tietokantana PostgreSQL PostGIS laajennoksella. Hostaus Solitan infrassa.

Autentikointiin käytetään KOKAa.

## Hakemistorakenne ##

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

## Kehitysympäristön pystyttäminen

### Kehitystyökalut

Asenna Leiningen:
http://leiningen.org/

Asenna tarvittavat kehitystyökalut: vagrant, ansible, virtualbox, Java 8

### VirtualBox

Käynnistä VirtualBox<br/>
<code>
cd vagrant<br/>
vagrant up
</code>

Jos vagrant up epäonnistuu, aja ensin:<br/>
<code>
vagrant box add geerlingguy/centos7 https://github.com/tommy-muehle/puppet-vagrant-boxes/releases/download/1.1.0/centos-7.0-x86_64.box
</code>

VirtualBoxissa pyörii tietokantapalvelin. Harjan kehitysympäristössä on kaksi eri kantaa:
- **harja** - Varsinaista kehitystyötä varten
- **harjatest** - Testit ajetaan tätä kantaa vasten

Testidata löytyy tiedostosta testidata.sql, joka ajetaan molempiin kantoihin.

### Tunnukset ulkoisiin järjestelmiin
Hae harja-testidata repositoriosta .harja -kansio ja aseta se samaan hakemistoon harjan repositorion kanssa. 

### Kääntäminen

Siirry projektin juureen. Käännä backend & käynnistä REPL:<br/>
<code>
lein do clean, compile, repl
</code>

Käännä frontend ja käynnistä Figwheel:<br/>
<code>
lein figwheel
</code>

Harjan pitäisi olla käynnissä ja vastata osoitteesta localhost:8000

### Kehitystyötä helpottavat työkalut

- **migrate_test.sh** pystyttää testikannan uudelleen
- **migrate_and_clean.sh** pystyttää molemmat tietokannat uudelleen tyhjästä
- **unit.sh** ajaa testit ja näyttää tulokset kehittäjäystävällisessä muodossa
- **deploy2.sh** Deployaa aktiivisen haaran testipalvelimelle testausta varten. Suorittaa testit ennen deployaamista.

## Tietokanta

Tietokannan määrittely ja migraatio (SQL tiedostot ja flyway taskit) ovat harja-repositorion kansiossa tietokanta

Ohjeet kehitysympäristön tietokannan pystytykseen Vagrantilla löytyvät tiedostosta `vagrant/README.md`

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

## Aja cloveragelle testikattavuusraportti
Hae työkalu: https://github.com/jarnovayrynen/cloverage
Työkalun cloverage/cloverage kansiossa aja "lein install"
Harjan juuressa aja "env CLOVERAGE_VERSION=1.0.8-SNAPSHOT lein cloverage"

## Tietokantadumpin ottaminen omalle koneelle

```
cd vagrant
sh fresh_dump.sh
```

## Kirjautuminen

Harja käyttää liikenneviraston extranetista tulevia headereita kirjautumiseen.
Käytä ModHeader tai vastaavaa asettaaksesi itselle oikeudet paikallisessa ympäristössä.

Oikeudet on määritelty tiedostossa resources/roolit.xslx: 1. välilehti kertoo oikeudet, 2. välilehti roolit
Harjan harja.domain.oikeudet.makrot luo Excelin pohjalta roolit käytettäväksi koodista.
Käyttäjällä voi olla useita rooleja. Oikeustarkistuksia tehdään sekä frontissa että backissä. Frontissa yleensä
piilotetaan tai disabloidaan kontrollit joihin ei ole oikeutta. Tämän lisäksi backissä vaaditaan
luku- ja/tai kirjoitusoikeus tietyn tiedon käsittelyyn.

Seuraavat headerit tuettuna:

* OAM_REMOTE_USER: käyttäjätunnus, esim. LX123123
* OAM_GROUPS: pilkulla erotettu lista ryhmistä (roolit ja niiden linkit). Esim:
    * Järjestelmävastaava: Jarjestelmavastaava
    * ELY urakanvalvoja: <urakan-SAMPO-ID>_ELY_Urakanvalvoja
    * Urakoitisijan laatupäällikkö: <urakoitsijan-ytunnus>_Laatupaallikko
    * Urakan vastuuhenkilö: <urakan-sampoid>_vastuuhenkilo
* OAM_ORGANIZATION: Organisaation nimi, esim. "Liikennevirasto" tai "YIT Rakennus Oy"
* OAM_DEPARTMENTNUMBER: Organisaation ELYNUMERO, esim. 12 (POP ELY)
* OAM_USER_FIRST_NAME: Etunimi
* OAM_USER_LAST_NAME: Sukunimi
* OAM_USER_MAIL: Sähköpostiosoite
* OAM_USER_MOBILE: Puhelinnumero

Staging-ympäristössä voidaan lisäksi testata eri rooleja testitunnuksilla,
jotka löytyvät toisesta Excelistä, mitä ei ole Harjan repossa (ei salasanoja repoon).

# Fronttitestit

Fronttitestit pyörivät figwheelin kautta.
Ne voi ajaa myös komentorivillä komennolla "lein doo phantom test"

# Labyrintin SMS gatewayn testaus kehitysmpäristössä
Labyrintin SMS viestien vastaanottoa voi testata tekemällä reverse SSH-tunneli 
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
