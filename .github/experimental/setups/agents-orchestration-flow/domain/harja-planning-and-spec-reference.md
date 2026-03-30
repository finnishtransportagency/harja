# Harja planning and spec reference

## Tavoite

Tämä dokumentti kokoaa yhteiset workspace-konventiot suunnitelma- ja spesifikaatiodokumenteille, jotta `10-flow-plan` ei kanna samoja rakenne- ja sijaintisääntöjä omassa rungossaan.

## Milloin käyttää

Käytä tätä referenssiä, kun agentin tai käyttäjän tehtävään kuuluu:
- toteutussuunnitelman kirjoittaminen
- feature-spesifikaation laatiminen tai päivittäminen
- `plans/`-hakemiston nimeämis- ja sijoittelukonventioiden noudattaminen

Tyypilliset agentit:
- `10-flow-plan`

## Ydinohjeet

### Tallennussijainti

Oletussijainti on `plans/`.

Suosi aihekohtaista hakemistoa muodossa:
- `plans/<topic-slug>/plan.md`
- `plans/<topic-slug>/spec.md`
- `plans/<topic-slug>/tasks/<task-slug>.md`

Jos saman aiheen kansio on jo olemassa, käytä sitä ensisijaisena tallennuspaikkana.
Jos aihehakemistoa ei ole vielä olemassa, johda `topic-slug` ensisijaisesti nykyisen feature branchin nimestä.
Jos feature branchia ei ole, branch on `develop` tai muu geneerinen haara, tai branchin nimi ei selvästi vastaa tehtävää, kysy käyttäjältä slug ennen tallennusta.

Normalisoi branch-pohjainen `topic-slug` näin:
- muunna pieniksi kirjaimiksi
- korvaa kauttaviivat ja alaviivat väliviivoilla
- tiivistä peräkkäiset väliviivat yhdeksi
- poista alusta ticket-prefiksi muodossa `<kirjaimet>-<numerot>-`, jos sen jälkeen jää kuvaava nimi

Esimerkki:
- branch `HARJA-1971-valimaisia-varusteita-puuttuu` -> `valimaisia-varusteita-puuttuu`

Valitse tiedostonimi tehtävän laajuuden mukaan:
- yksi kokonainen aihe tai laaja muutos: `plan.md` ja tarvittaessa `spec.md`
- isomman kokonaisuuden aliosa: `tasks/<task-slug>.md`

Siirtymävaiheessa olemassa olevia legacy-dokumentteja saa päivittää paikoillaan:
- `.prd/...`
- `specs/...`

Niitä ei tarvitse siirtää automaattisesti uuden muutoksen yhteydessä.

### Toteutussuunnitelman minimirakenne

Kun kirjoitat suunnitelmadokumentin, sisällytä vähintään:
- `Overview`
- `Requirements`
- `Implementation Steps`
- `Testing`

Tavoite:
- kuvata mitä tehdään
- rajata mitä vaaditaan valmiiksi
- esittää toteutus konkreettisina askelina
- nimetä verifiointi ennen toteutusta

### Spesifikaation minimirakenne

Kun kirjoitat tai päivität feature-specia, sisällytä vähintään:
- status, luonti- ja päivitystiedot
- vision tai tavoitteen kuvaus
- user stories
- acceptance criteria
- technical approach
- boundaries: always, ask first, never
- edge cases ja riskit
- testing strategy
- implementation tasks
- change log silloin kun spec elää

### Tehtävien koko

Kun spec pilkotaan toteutustasolle, suosi tehtäviä jotka ovat:
- selkeästi rajattuja
- mahdollista toteuttaa ja varmistaa yhdessä pienessä kierroksessa
- noin 1-2 tunnin kokoisia aina kun se on realistista

## Ristiviitteet

Kun dokumentti koskee Harja-featurea, yhdistä suunnitelma tai spec tarvittaessa näihin referensseihin:
- `harja-feature-implementation-reference.md` rakenteeseen, scaffoldingiin ja naming-konventioihin
- `harja-validation-review-reference.md` write-path-, validation- ja authorization-riskeihin
- `harja-e2e-testing-reference.md` verifiointi- ja E2E-kattavuuteen
- `harja-style-review-reference.md` stylesheet- tai design-system -migreeraation havaintoihin

## Käyttöohje agentille

Agentin ei tarvitse kopioida tämän dokumentin rakennelistaa omaan runkoonsa.

Agentin tulisi sen sijaan:
- viitata tähän dokumenttiin, kun kirjoitettava dokumentti kuuluu `plans/`-konventioon
- käyttää nykyistä feature branchia oletuslähteenä `topic-slugille`
- pysähtyä kysymään, jos branch-pohjainen slug ei ole turvallinen tai uskottava
- poimia vastaukseensa vain tehtävän kannalta olennainen osa rakenteesta
- pitää oma promptinsa behavior- ja workflow-keskeisenä
- kattaa tarvittaessa sekä toteutussuunnitelman että spec-tyylisen suunnitteludokumentin ohjaus saman `10-flow-plan`-vastuun alla

## Rajaus

Tämä dokumentti ei korvaa issue-specin terminologiatyötä tai agenttien output-sopimuksia.
