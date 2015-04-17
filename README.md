# Liikenneviraston Harja järjestelmä #

Projekti on client/server, jossa serveri on Clojure sovellus (http-kit) ja
client on ClojureScript sovellus, joka käyttää Reagentia, LeafletJSää ja Bootstrap CSSää.

Tietokantana PostgreSQL PostGIS laajennoksella. Hostaus Solitan infrassa.

Autentikointiin käytetään KOKAa.

## Hakemistorakenne ##

Alustava 
Harja repon hakemistorakenne:

- README                    (yleinen readme)

- src/                      (kaikki lähdekoodit)
  - cljx/                   (palvelimen ja asiakkaan jaettu koodi)
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
      - api/                (Harja API endpointit ja tukikoodi)
      - palvelut/           (Harja asiakkaalle palveluja tarjoavat EDN endpointit)
      - main.cljs           (palvelimen aloituspiste)

- (dev-)resources/          (web-puolen resurssit)
  - css/                    (ulkoiset css tiedostot)
  - js/                     (ulkoiset javascript tiedostot)


## Integraatiot

Integraatiot MULEeen ja niiden implementaatioprojekti on omanaan.

## Tietokanta

Tietokannan määrittely ja migraatio (SQL tiedostot ja flyway taskit) ovat omassa repossaan: harja-tietokanta 


## Staging tietokannan päivitys, uusi ja hyvä tapa
ssh -L7777:localhost:5432 harja-db1-stg
 * Luo yhteys esim. käyttämäsi IDE:n avulla,
    * tietokanta: harja, username: flyway salasana: kysy tutuilta

## Testipalvelimen tietokannan päivitys, vanha ja huono tapa
 * Avaa VPN putki <br/>
 <code>
    ssh harja-jenkins.solitaservices.fi
    [jarnova@harja-jenkins ~]$ sudo bash <br/>
    [root@harja-jenkins jarnova]# su jenkins <br/>
    bash-4.2$ ssh harja-db1-test <br/>
    Last login: Mon Mar 16 15:23:22 2015 from 172.17.238.100 <br/>
    [jenkins@harja-db1-test ~]$ sudo bash <br/>
    [root@harja-db1-test jenkins]# su postgres <br/>
    bash-4.2$ psql harja <br/>
</code>
 * Tee temput