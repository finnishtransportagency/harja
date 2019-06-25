# Väylän Harja-järjestelmä #

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

Asenna tarvittavat kehitystyökalut: vagrant, ansible, virtualbox, Java 8.
Vaihtoehtoisesti voit käyttää dockeria.

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

Harjan pitäisi olla käynnissä ja vastata osoitteesta localhost:8000 tai localhost:3000

### Kehitystyötä helpottavat työkalut

- **unit.sh** ajaa testit ja näyttää tulokset kehittäjäystävällisessä muodossa

### Docker paikallinen kehitysympäristö

Paikallisen kehitysympäristön tarvitsemat palvelut voi käynnistää myös dockerilla.
Tietokanta tarvitaan aina. ActiveMQ ei ole pakollinen, jos ei testaa integraatioita, mutta sovellus
logittaa virheitä jos JMS brokeriin ei saada yhteyttä.

* Tietokanta: ks. tietokanta/devdb_up.sh ja tietokanta/devdb_down.sh
* ActiveMQ: docker run -p 127.0.0.1:61616:61616 -p 127.0.0.1:8161:8161 rmohr/activemq

Kantaimagen päivitys: docker pull solita/harjadb

Voit myös käynnistää Harjan kehityskannan ja ActiveMQ:n ajamalla docker-compose up.

## Dokumentaatio

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

### Namespacet
Jokaisen namespacen alkuun kirjataan seuraavat asiat:
- Olemassa olon syy?
- Listaus minkä domain-käsitteiden kanssa toimitaan tässä nimiavaruudessa.
- Mitkä ovat pääpalvelut, jotka tämä nimiavaruus tarjoaa? Mistä kannattaa lähteä liikenteeseen?
- Toistuvat käsitteet koodin kannalta, tärkeät keywordit.

## Testaus

Harjassa on kolme eritasoista test-suitea: fronttitestit (phantom), palvelutestit (backend) ja
e2e testit (erillisessä projektissa).

### Fronttitestit

Fronttitestit ajetaan komennolla: lein doo phantom test
Laadunseurantatyökalun testit ajetaan komennolla: lein doo phantom laadunseuranta-test

Odotetaan, että kaikilla frontin nimiavaruuksilla on testitiedosto ja vähintään yksi
testi.

Kaikilla yleiskäyttöisillä peruskomponenteilla (esim. grid, lomake ja eri kenttätyypit) tulisi
olla hyvät testit, koska niitä käytetään laajasti koko järjestelmässä.

UI tilan käsittely pitää testata ja tarpeen vaatiessa UI-komponentin renderöinti DOMiin.
Täysiä työnkulkuja, joissa on useita komponentteja, ei tarvitse tässä test suitessa tehdä.

Testaa UI tilassa reaktioiden oikea toiminta ja tuck event käsittely.

### Backend testit

Backend testit testaavat harjan palvelinta ja käytössä on oikea tietokanta.
Kaikille palveluille on syytä tehdä testi, joka testaa vähintään onnistuvan
tapauksen ja mielellään myös virheellisen kutsun.

Pyritään testaamaan kaikki CRUD operaatiot.

Oikeustarkistuksien olemassaolo varmistetaan automaattisesti palvelukutsuissa,
mutta eri käyttäjien pääsy tiettyyn palveluun on syytä testata sen palvelun testeissä.
Testaa palvelukutsuja ainakin kahdella käyttäjällä: sellaisella joka pääsee ja sellaisella
joka ei.

### End-to-end testit

End-to-end testit ajetaan harjan testiympäristöä vasten Seleniumilla. Tämän test suiten
tarkoituksena on tehdä perustason smoke test, joka varmistaa että kaikki osiot latautuvat
oikein eikä harja räsähdä.

Uusille näkymille lisätään testi, jossa näkymään navigoidaan ja tarkistetaan jotain yksinkertaista
sivun rakenteesta.

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

## Tieverkon tuonti kantaan

Replissä: (harja.palvelin.main/with-db db (harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko/vie-tieverkko-kantaan db "file:/.../harja-testidata/shp/Tieosoiteverkko/PTK_tieosoiteverkko.shp"))


## Kirjautuminen

Harja käyttää Väylän extranetista tulevia headereita kirjautumiseen.
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
* OAM_ORGANIZATION: Organisaation nimi, esim. "Väylä" tai "YIT Rakennus Oy"
* OAM_DEPARTMENTNUMBER: Organisaation ELYNUMERO, esim. 12 (POP ELY)
* OAM_USER_FIRST_NAME: Etunimi
* OAM_USER_LAST_NAME: Sukunimi
* OAM_USER_MAIL: Sähköpostiosoite
* OAM_USER_MOBILE: Puhelinnumero

Staging-ympäristössä voidaan lisäksi testata eri rooleja testitunnuksilla,
jotka löytyvät toisesta Excelistä, mitä ei ole Harjan repossa (ei salasanoja repoon).

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

Käyttöliittymän kääntäminen ja ajaminen kansiosta /harja:

    lein figwheel laadunseuranta-dev

Avaa selain http://localhost:3000/laadunseuranta/

Palvelin käynnistyy kun Harja käynnistetään.

## Lisenssi
https://github.com/finnishtransportagency/harja/blob/develop/LICENSE.txt

## Sonjan JMS jonojen käyttäminen Hermes JMS:llä
1. Hae Hermes JMS: https://sourceforge.net/projects/hermesjms/
2. Asenna Java SE 6 runtime: https://support.apple.com/kb/DL1572?locale=en_US
3. Kopioi harja-testidata repositoriosta hermes-config.xml kansioon ~/.hermes
4. Kopioi harja-testidata repositoriosta Sonic2013_libs & SonicClientLibs Sonic MQ:n JMS kirjastot haluamaasi kansioon
5. Avaa ~/.hermes/hermes-config.xml tiedosto
6. Muokkaa Sonic JMS ajurien jarrien sijainti kansioihin, johon kopioit ne aiemmin
7. Lisää ssh configiin (~/.ssh/config) seuraavat asetukset:
 Host harja-app1-stg
         ProxyCommand ssh harja-jenkins.solitaservices.fi -W %h:%p

         # Testi-Sonjan JMS jonot (7.6.2)
         LocalForward 2511 testihaproxy.liikennevirasto.fi:2001
         # LocalForward 2511 192.83.32.231:2511

         # Testi-Sonjan (2013) JMS jonot
         LocalForward 2511 testihaproxy.liikennevirasto.fi:2002
         # LocalForward 2512 81.22.173.248:2511

         # Tuotanto-Sonja (7.6.2)
         LocalForward 2001 haproxy.liikennevirasto.fi:2001
         # LocalForward 2001 nginplus.liikennevirasto.fi:2001

         # Tuotanto-Sonja (2013)
         LocalForward 2002 haproxy.liikennevirasto.fi:2002
         # LocalForward 2002 nginplus.liikennevirasto.fi:2002
8. Avaa ssh-yhteys harja-app1-stg palvelimelle: ssh harja-db1-stg
9. Avaa Hermes JMS

## FIM

Harja käyttää FIM:iä käyttäjätietojen hakemiseen. FIM-komponentista on kaksi eri versiota: FIM ja FIMDEV. FIMDEV on käytössä vain lokaalissa kehitysympäristössä ja se palauttaa aina fim.edn-tiedoston sisällön.

Oikean FIM:n testikäyttö:
1. Määrittele asetukset.edn:n FIM:n URL:ksi https://localhost:6666/FIMDEV/SimpleREST4FIM/1/Group.svc/getGroupUsersFromEntitity sekä poista :tiedosto avain.
2. Avaa SSH-yhteys ssh -L6666:testioag.vayla.fi:443 harja-app1-stg

## Active MQ
Käynnistys docker imagesta:
docker run -p 127.0.0.1:61616:61616 -p 127.0.0.1:8161:8161 rmohr/activemq

URL konsoliin:
localhost:8161/admin/queues.jsp (admin/admin)
