# Harja Planning And Spec Reference

## Tavoite

Tämä dokumentti kokoaa yhteiset workspace-konventiot suunnitelma- ja spesifikaatiodokumenteille, jotta `10-flow-plan` ei kanna samoja rakenne- ja sijaintisääntöjä omassa rungossaan.

## Milloin Käyttää

Käytä tätä referenssiä, kun agentin tai käyttäjän tehtävään kuuluu:
- toteutussuunnitelman kirjoittaminen
- feature-spesifikaation laatiminen tai päivittäminen
- `.prd`-hakemiston nimeämis- ja sijoittelukonventioiden noudattaminen

Tyypilliset agentit:
- `10-flow-plan`

## Ydinohjeet

### Tallennussijainti

Oletussijainti on `.prd/`.

Tyypilliset muodot:
- `.prd/[feature-name]-prd.md`
- `.prd/[feature]/[component]-spec.md`

Valitse tiedostonimi tehtävän laajuuden mukaan:
- yksi kokonainen feature tai laaja muutos: feature-niminen PRD
- isomman featurekokonaisuuden aliosa: component- tai osa-aluekohtainen spec

### Toteutussuunnitelman Minimirakenne

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

### Spesifikaation Minimirakenne

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

### Tehtävien Koko

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

## Käyttöohje Agentille

Agentin ei tarvitse kopioida tämän dokumentin rakennelistaa omaan runkoonsa.

Agentin tulisi sen sijaan:
- viitata tähän dokumenttiin, kun kirjoitettava dokumentti kuuluu `.prd`-konventioon
- poimia vastaukseensa vain tehtävän kannalta olennainen osa rakenteesta
- pitää oma promptinsa behavior- ja workflow-keskeisenä
- kattaa tarvittaessa sekä toteutussuunnitelman että spec-tyylisen suunnitteludokumentin ohjaus saman `10-flow-plan`-vastuun alla

## Rajaus

Tämä dokumentti ei korvaa issue-specin terminologiatyötä tai agenttien output-sopimuksia.
