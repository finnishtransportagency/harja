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
      - api/                (Harja API endpointit ja tukikoodi)
      - integraatiot/       (spesifiset järjestelmäintegraatiot omissa alinamespaceissa)
      - palvelut/           (Harja asiakkaalle palveluija tarjoavat EDN endpointit)
      - main.cljs           (palvelimen aloituspiste)

- (dev-)resources/          (web-puolen resurssit)
  - css/                    (ulkoiset css tiedostot)
  - js/                     (ulkoiset javascript tiedostot)

- migrations/               (tietokantamigraatioiden .sql tiedostot)



      

  