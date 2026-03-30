# Harja Unit Testing Reference

## Tavoite

Pakattu muistilista Harjan backend-yksikkotesteihin ja kapeisiin palvelutason testeihin.
Tavoite on suojata liiketoimintasaannot backend-testilla ennen E2E-varmistusta.

## Milloin Käyttää

- kirjoitat tai päivität `*_test.clj` tiedostoa
- muutat palvelu-, kysely- tai domainlogiikkaa
- korjaat bugia, jonka regressiosuoja kuuluu backendiin
- arvioit puuttuuko branchilta olennainen backend-testi

## Ydinohjeet

### Periaatteet

- suosi backend-testiä kun sääntö elää palvelu-, data- tai validointilogiikassa
- suosi E2E:tä kun riski on käyttöpolussa tai UI-integraatiossa
- pidä molemmat kun backend-sääntö muuttuu ja UI-polku on liiketoiminnallisesti keskeinen
- kirjoita failaava testi ensin kun käyttäytyminen muuttuu
- aja ensin kapein relevantti testi, sitten lähin järkevä kokonaisuus
- testaa yksi sääntö tai tapaus per `deftest`
- nimeä testi niin, että suojattu sääntö selviää ilman toteutuskoodin lukemista

### Testidata Ja Rakenne

- suosi olemassa olevia fixtureja ja testiapureita ad hoc -setupin sijaan
- jos lähialue käyttää transaction-fixturea, oleta automaattinen rollback eikä manuaalista cleanupia
- käytä database helpereita tarkoituksen mukaan: `q-map` kyselyyn mappeina, `q` raakariveinä, `i` insertin id:hen, `u` update/delete vaikutusmäärään
- jos sama SQL-setup tai datanluonti toistuu, nosta se nimettyyn utility-funktioon testin alussa tai yhteiseen testiapuriin
- rakenna vain testin tarvitsema minimi data
- tarkista paluuarvo, tallennettu tila, validointivirhe tai oikeuseston lopputulos
- vältä toteutusdetaljien ylimääräistä lukitsemista assertioneissa

### Testattavuus Ennen Redefiä

- vältä `with-redefs` tai muuta redef-painotteista testausta oletusratkaisuna
- mieluummin refaktoroi koodi helpommin testattavaksi kuin mockaa laajasti sisäisiä riippuvuuksia
- jos redef on pakollinen, pidä se paikallisena, minimaalisena ja perusteltuna

### Vältä Ainakin

- monta irrallista sääntöä samassa testissä
- raskasta tai epäselvää testidataa
- toisteista raakaa SQL-setupia, jos utility-funktio tekisi testistä luettavamman
- uuden custom testiapurin luomista ilman todellista uudelleenkäyttöä
- selaintason ongelman pakottamista backend-testiin
- bugikorjausta ilman regressiotestiä, kun sellainen on realistinen

## Ristiviitteet

- käytä `harja-feature-implementation-reference.md`, kun tarvitset feature-rakenteen ja namingin kontekstia
- käytä `harja-validation-review-reference.md`, kun authorization tai validointi on keskiössä
- käytä `harja-e2e-testing-reference.md`, kun backend-testin rinnalle tarvitaan selaintason varmistus

## Käyttöohje Agentille

- viittaa tähän dokumenttiin, kun tehtävä painottuu backend-yksikkötestin kirjoittamiseen, päivitykseen tai arviointiin
- poimi vain testin kannalta olennainen muistilista omaan analyysiin tai toteutukseen
- pidä varsinainen agenttiprompti workflow- ja käyttäytymiskeskeisenä

## Rajaus

Tämä dokumentti ei korvaa E2E-, feature-rakenne- tai validation-referenceja.
