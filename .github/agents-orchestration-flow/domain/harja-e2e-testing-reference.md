# Harja E2E Testing Reference

## Tavoite

Tämä dokumentti kokoaa Harjan Cypress- ja E2E-testauksen minimitason domain-ohjeet yhteen paikkaan.

## Milloin Käyttää

Käytä tätä referenssiä, kun tehtävä koskee Harjan E2E- tai Cypress-testausta, UI-polun selainvarmennusta tai selainpohjaisen testiongelman diagnostiikkaa.

Tyypilliset agentit:
- `support-test`
- `review-pre-pr`
- `14-flow-verify`

## Ydinohjeet

### Preconditions

Tarkista ennen ajoa ainakin:
- tietokanta, backend ja frontend ovat käytettävissä
- Cypress on käytettävissä
- oletuskäyttäjäpolkua käytettäessä `HARJA_SALLI_OLETUSKAYTTAJA` on oikein asetettu

Jos auth- tai session-polku ei toimi odotetusti, raportoi eksplisiittisesti johtuiko ongelma ympäristöstä vai varsinaisesta tuotetason viasta.

### Suoritusheuristiikat

Peruslinjaukset:
- suosi `avaaHarjaTimeoutilla()` tai projektin vastaavaa yhteistä avaajaa
- suosi `data-cy`-selektoreita ja vältä hauraita rakennevalitsimia
- käytä `cy.intercept()` + `cy.wait('@alias')`, kun toiminto lataa tai tallentaa dataa
- tee tietokannan siivous vain testin tarvitsemaan rajattuun dataan

Tyypillisiä hyödyllisiä apureita:
- `avaaHarjaTimeoutilla()`
- `cy.valinnatValitse(...)`
- `cy.terminaaliKomento()` silloin kun projekti jo käyttää sitä

### Selainvirheiden Minimitarkistus

Kun verify kohdistuu UI-polkuun, minimitason tarkistus sisältää:
- sivu aukeaa ilman yleistä virhenäkymää
- selainkonsoli ja page errors tarkistetaan ensilatauksen jälkeen
- happy path suoritetaan loppuun asti
- selainkonsoli ja page errors tarkistetaan uudelleen lopussa

Raportoi vähintään:
- löytyikö uncaught page error
- löytyikö selvä punainen console-virhe tai toistuva virhekuvio
- jäikö tarkistus tekemättä, ja miksi

### Epävakauden Diagnostiikka

Jos testi on epävakaa, tarkista ensin:
- puuttuuko odotus loaderin loppumiselle
- käytetäänkö liian haurasta selektoria
- puuttuuko `intercept` ja odotus API-kutsulle
- riippuuko testi aiemmasta testidatasta
- onko timeout liian tiukka kyseiselle näkymälle

Vältä ainakin:
- `.only()` committed koodissa
- kiinteitä `cy.wait(5000)` odotuksia ilman perustetta
- testejä, jotka riippuvat suoritusjärjestyksestä
- liian laajaa tietokantasiivousta

## Ristiviitteet

- Käytä `harja-feature-implementation-reference.md`, kun testi tarvitsee feature-rakenteen tai naming-konvention kontekstia.
- Käytä `harja-validation-review-reference.md`, kun E2E-havainto viittaa backendin validation-, authorization- tai write-path-riskiin.

## Käyttöohje Agentille

- viittaa tähän dokumenttiin, kun tehtävä koskee Harjan E2E- tai Cypress-testausta
- poimi mukaan vain testin tai verify-polun kannalta olennainen muistilista
- pidä agenttiprompti workflow-, diagnoosi- ja raportointikeskeisenä

## Rajaus

Tämä dokumentti ei korvaa feature-rakenteen, validation-reviewn tai yleisen verify-outputin sopimuksia.
