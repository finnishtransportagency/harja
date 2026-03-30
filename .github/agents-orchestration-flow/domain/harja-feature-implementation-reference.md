# Harja Feature Implementation Reference

## Tavoite

Tämä dokumentti kokoaa Harja-featuren toteutuksen peruskartan yhteen paikkaan, jotta agenttien ei tarvitse kantaa samaa rakenne- ja naming-tietoa omissa rungoissaan.

## Milloin Käyttää

Käytä tätä referenssiä, kun tehtävä koskee Harja-featuren toteutusta, isompaa refaktorointia tai todennäköisten muutosalueiden kartoitusta.

Tyypilliset agentit:
- `11-flow-implement`
- `review-pre-pr`

## Ydinohjeet

### Projektikonteksti

Harja on Clojure/ClojureScript-järjestelmä, jossa tyypilliset muutosalueet ovat:
- backend-palvelut ja kyselyt
- ClojureScript-näkymät ja tilanhallinta
- PostgreSQL-migraatiot ja SQL
- backend-yksikkötestit ja Cypress E2E

Pidä erityisesti mielessä:
- urakka- ja hoitovuosikohtaiset säännöt voivat muuttaa käyttäytymistä
- selkeä suomenkielinen domain-sanasto on ensisijainen
- uusia frontend-testipolkuja ohjataan mieluummin tuettuihin malleihin tai E2E:hen kuin vanhoihin doo/phantom-kuvioihin

### Tyypillinen Feature-Kartta

Frontend:
- näkymä `src/cljs/harja/views/urakka/[module]/[feature]_nakyma.cljs`
- tila `src/cljs/harja/tiedot/urakka/[module]/[feature]_tiedot.cljs`
- tarvittaessa app-state-kytkentä `src/cljs/harja/tiedot/urakka/urakka.cljs`
- tarvittaessa tabi tai reititys `src/cljs/harja/views/urakka.cljs`

Backend:
- palvelu `src/clj/harja/palvelin/palvelut/[module]/[feature]_palvelu.clj`
- kyselyt `src/clj/harja/kyselyt/[feature]_kyselyt.clj` ja `.sql`
- oikeudet `src/clj/harja/domain/oikeudet.clj`

Tietokanta ja testit:
- migraatio `tietokanta/src/main/resources/db/migration/V[number]__[feature].sql`
- backend-testi `test/clj/harja/palvelin/palvelut/[module]/[feature]_palvelu_test.clj`
- Cypress E2E `cypress/e2e/[module]/[feature]_test.cy.js`

Jos rollout on asteittainen, tarkista myös feature-flag-polku ja vanhan sekä uuden UI:n rinnakkaiselo.

### Naming Ja Konventiot

Frontend:
- namespace `harja.views.urakka.[module].[feature]-nakyma`
- tiedosto `[feature]_nakyma.cljs`
- funktiot kebab-case
- `data-cy` kebab-case

Backend ja SQL:
- namespace `harja.palvelin.palvelut.[module].[feature]-palvelu`
- tiedosto `[feature]_palvelu.clj`
- endpointit tyyliin `:hae-[feature]` ja `:tallenna-[feature]`
- taulut underscore-muodossa
- migraatiot muodossa `V[number]__[description].sql`
- nimetty kysely esimerkiksi `-- name: hae-data`

### Olemassa Olevat Apualueet

Tarkista ennen uuden utilityn keksimistä ainakin:
- `harja.fmt`
- `harja.pvm`
- `harja.kokoelmat`
- `harja.geo`
- `harja.validointi` tai `harja.ui.validointi`
- `harja.ui.*`
- `harja.testi`

Utilityn sijoitus:
- `src/cljc/harja/` jaetulle puhtaalle logiikalle
- `src/clj/harja/palvelin/tyokalut/` palvelinpuolen apureille
- `src/cljs/harja/ui/` tai `tyokalut/` selain- ja UI-apureille

## Ristiviitteet

- Käytä `harja-unit-testing-reference.md`, kun tehtävä painottuu backend-yksikkötesteihin tai `*_test.clj` muutoksiin.
- Käytä `harja-validation-review-reference.md`, kun write-path, authorization tai validointi on keskiössä.
- Käytä `harja-e2e-testing-reference.md`, kun muutos vaatii UI-polun tai Cypress-kattavuuden arviointia.
- Käytä `harja-utility-namespaces` skillia ennen uuden utilityn keksimistä.

## Käyttöohje Agentille

- viittaa tähän dokumenttiin, kun tehtävä on Harja-feature-toteutus tai iso refaktorointi
- poimi mukaan vain tehtävän kannalta olennainen rakenne- ja naming-konteksti
- pidä agenttiprompti behavior- ja workflow-keskeisenä

## Rajaus

Tämä dokumentti ei korvaa erillisiä testaus-, validation- tai E2E-referenceja.
