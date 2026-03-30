# Domain knowledge

## Tavoite

Tähän hakemistoon kerätään Harja-spesifi uudelleenkäytettävä domain-tieto, jota useat agentit voivat tarvita.

Periaate:
- agentti kuvaa käyttäytymisen, workflow'n ja outputin
- domain-dokumentti kuvaa Harja-spesifin rakenteen, konventiot ja tarkistuslistat

## Kanoninen rakenne

Yksittäisen domain-referenssin tavoiterakenne on:
- `Tavoite`
- `Milloin käyttää`
- `Ydinohjeet`
- `Ristiviitteet`
- `Käyttöohje agentille`
- `Rajaus`

## Dokumentit

| Tiedosto | Käytä kun | Tyypilliset agentit |
|---|---|---|
| `harja-feature-implementation-reference.md` | tarvitset featuren rakenteen, namingin tai todennäköiset muutosalueet | `11-flow-implement`, `review-pre-pr` |
| `harja-unit-testing-reference.md` | kirjoitat, päivität tai arvioit backend-yksikkötestejä | `11-flow-implement`, `review-fix`, `review-pre-pr` |
| `harja-validation-review-reference.md` | tarkastat write-pathia, authorizationia tai validation-riskejä | `review-validation`, `12-flow-review`, `support-root-cause` |
| `harja-e2e-testing-reference.md` | tarkastat UI-polkuja, E2E-ympäristöä tai Cypress-käytäntöjä | `support-test`, `review-pre-pr`, `14-flow-verify` |
| `harja-style-review-reference.md` | arvioit LESS/CSS-alueita, legacy-tyylivelkaa tai stylesheet-priorisointia | `review-style`, `support-ux` |
| `harja-planning-and-spec-reference.md` | kirjoitat `plans/`-rakenteen suunnitelmaa tai spec-dokumenttia | `10-flow-plan` |
| `harja-worktree-reference.md` | käytät Harjan worktree-helpereitä turvallisesti | `support-worktree` |
| `harja-issue-spec-reference.md` | tarkennat ticketin speciksi tai puristat specin Jira-ready issueksi | `support-issue-spec` |
| `harja-delivery-closeout-reference.md` | suljet flown, synkronoit totuuslähteen tai teet closeout-tarkistuksia | `15-flow-closeout` |

## Review ja verify -reititys

Kun tehtävä osuu review- tai verify-haaraan:
- aloita `harja-feature-implementation-reference.md` tiedostosta, jos tarvitset kartan muutosalueeseen
- käytä `harja-unit-testing-reference.md`, kun kysymys on backend-testikatteesta
- käytä `harja-validation-review-reference.md`, kun write-path tai authorization on riski
- käytä `harja-e2e-testing-reference.md`, kun verify nojaa UI-polkuun tai Cypressiin

Jos tehtävä ylittää yhden alueen, valitse yksi ensisijainen referenssi ja käytä muita vain tukena.

## Rajaus

Tähän ei kuulu:
- yleinen agenttirakenne
- orkestrointisäännöt
- file-type-spesifit instructions-säännöt

Ne kuuluvat edelleen agentteihin tai instructions-tiedostoihin.
