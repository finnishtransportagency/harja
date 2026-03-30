# Harja Validation Review Reference

## Tavoite

Tämä dokumentti kokoaa Harjan backend-validation reviewn minimitason domain-ohjeet yhteen paikkaan.

## Milloin Käyttää

Käytä tätä referenssiä, kun tarkastat write-pathia, authorizationia, SQL-turvallisuutta tai backend-validation puutteita.

Tyypilliset agentit:
- `review-validation`
- `12-flow-review`
- `support-root-cause`
- `review-pre-pr`, kun branchissa on write-path- tai authorization-riski

## Ydinohjeet

### Missä Riskit Yleensä Elävät

Tarkista ensisijaisesti:
- `src/clj/harja/palvelin/integraatiot/api/` ja siihen liittyvät validointi- ja skeematyökalut
- `src/clj/harja/palvelin/palvelut/` write-pathien service entry pointit
- `src/clj/harja/kyselyt/` ja vastaavat `.sql`-tiedostot

Tyypillisiä suojaavia mekanismeja ovat:
- JSON- tai XML-skeemavalidointi
- spec- tai custom-validointi ennen tietokantaoperaatiota
- oikeustarkastukset service entry pointissa
- parameterisoidut JeeSql-kyselyt ja tietokantarajoitteet

### Mitä Tarkistaa Ensin

Tarkista ensimmäisenä:
- puuttuuko input-validointi tai rajatarkistus
- puuttuuko authorization tai käyttäjäkonteksti write-operaatiosta
- rakennetaanko SQL:ää vaarallisesti tai ilman parametrisointia
- puuttuuko business rule -tarkistus kuten tilasiirtymä, aikaväli tai duplikaattien esto
- vuotaako virheenkäsittely sisäisiä poikkeuksia tai puuttuuko lokitus
- tehdäänkö coercionia ilman ennakkovalidointia

### Suosi Ja Varo

Suosi:
- parameterisoituja JeeSql-kyselyitä
- oikeustarkistusta service entry pointissa
- virheiden kerryttämistä yhteen rakenteeseen
- selkeää input-validointia ennen tietokantaoperaatioita

Varo:
- SQL-stringin rakentamista `str`-konkatenaatiolla
- write-operaatioita ilman oikeustarkastusta
- coercionia ilman ennakkotarkastusta
- puuttuvia numero-, päivämäärä- tai olemassaolotarkistuksia

### Priorisointi

Critical:
- endpoint ilman autentikointia tai authorisointia
- SQL injection -riski
- kirjoittava operaatio ilman oikeustarkastusta
- validoinnin puuttuminen suoraan käyttäjän syötteeltä

High:
- puuttuvat nil- tai boundary-tarkastukset
- puuttuva päivämäärä- tai tilasiirtymävalidointi
- duplikaattien eston puute

Medium:
- geneeriset virheilmoitukset
- puuttuva lokitus
- epäyhtenäinen validation pattern

## Ristiviitteet

- Käytä `harja-feature-implementation-reference.md`, kun tarvitset feature-rakenteen tai todennäköisten service-, query- ja testisijaintien kartan.
- Käytä `harja-e2e-testing-reference.md`, kun validointihavainto pitää suhteuttaa UI-polkuun tai toistettavaan E2E-verifiointiin.

## Käyttöohje Agentille

- viittaa tähän dokumenttiin, kun tehtävä koskee Harjan backend validation reviewta
- poimi analyysiin vain olennaiset riskiluokat, tarkistuskohdat ja priorisointi
- pidä agenttiprompti tutkimus- ja raportointikeskeisenä

## Rajaus

Tämä dokumentti ei korvaa feature-rakenteen, E2E-verifioinnin tai yleisen review-prosessin referenceja.
