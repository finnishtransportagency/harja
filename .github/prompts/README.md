# Harja AI promptit

Tämä hakemisto sisältää promptit joita käytetään ensisijaisesti GitHub Copilotin slash-komennoilla.  
Voit myös copy-pasteta prompteja haluamaasi LLM-työkaluun.  
On suositeltavaa, että luet ```*.prompt.md``` tiedoston sisällön ennen ajamista, jotta ymmärrät rajat, syötteet ja lopputuloksen ja vältät turhat iteraatiot.

## Nopea yhteenveto

| Prompt | Tiedosto | Status | Tarkoitus | Tuotos                                     |
| ------ | -------- | ------ | --------- |--------------------------------------------|
| Project Summary | [`project-summary.prompt.md`](project-summary.prompt.md) | Stable | Projektin yhteenvedon generointi / päivitys | `PROJECT_SUMMARY.md` luodaan/päivittyy     |
| Architectural Help | [`architectural-help.prompt.md`](architectural-help.prompt.md) | Stable | Arkkitehtuurin suunnitelma ennen toteutusta | Toimintasuunnitelma (ei koodia)            |
| Review Code | [`review-code.prompt.md`](review-code.prompt.md) | Stable | Koodimuutosten katselmointi | Kattava katselmointiraportti               |
| DB Migration Helper | [`db-migration-helper.prompt.md`](db-migration-helper.prompt.md) | Experimental | Flyway PostgreSQL/PostGIS migraation suunnittelu | Migraatiosuunnitelma + SQL luonnos         |
| Accessibility Audit | [`accessibility-review.prompt.md`](accessibility-review.prompt.md) | Experimental | WCAG 2.1/2.2 saavutettavuusanalyysi live sivulle | Markdown audit-raportti kuvineen           |
| Browser Tuck Debug | [`browser-debug_tuck-app-state.prompt.md`](browser-debug_tuck-app-state.prompt.md) | Experimental | CLJS Tuck sovellustilan selaindebug | Konsolianalyysi tilasiirtymistä            |

*Huom!*: ```Experimental = kokeiluasteella```, eli ominaisuudet ja käyttötavat voivat vielä muuttua. Kerro ongelmahavainnot tiimille.

## Promptin valinta
```
Tarvitset yleiskuvan projektista? -> Project Summary
Tarvitset arkkitehtuurisen suunnitelmal uudelle ominaisuudelle? -> Architectural Help
Katselmoit juuri tehtyä koodinpätkää (mustattu hiirellä) tai useamman tiedoston muutosta? -> Review Code
Suunnittelet tietokantamigraatiota? -> DB Migration Helper (Experimental)
Diagnosoit CLJS frontin Tuck tilan ongelmia? -> Browser Tuck Debug (Experimental)
Onko ominaisuuden saavutettavuuden vaatimukset otettu huomioon? -> Accessibility Audit (Experimental)
```

## Käyttöohje yleisesti
1. Avaa haluttu prompt slash-komennolla (esim. Copilotissa kirjoita `/project-summary`).
2. **Valitse Mode:** Agent (useimmiten) tai Ask.
3. **Valitse Model:** 
   * GPT-5 (Toimii paremmin jos tehdään suunnitelmaa), tai Gemini Pro (jos saatavilla)
   * Claude Sonnet 4.5 (jos tehdään toteutusta tai katselmointia)
   * Tai, muu käytettävissä oleva malli riippuen tarpeesta.
4. Vastaa promptin kysymyksiin tai anna ne suoraan slash-kommennon kanssa. Älä ohita niitä, niitä tarvitaan parempaan lopputulokseen.
5. Englannin käyttö keskustelun kielenä toimii usein paremmin ja vaatii vähemmän tokeneja, mutta suomikin käy. Lopputulos käännetään kuitenkin aina suomeksi.
6. Experimental promptit: Tulosten validointi ja tulkinta vaativat normaalia enemmän kriittistä silmää.

## Promptien tarkemmat kuvaukset ja esimerkkikäyttö

### 1. Project Summary (`project-summary.prompt.md`)
**Tarkoitus:** Generoida tai päivittää `PROJECT_SUMMARY.md` jotta AI-avustaja ymmärtää projektin rakenteen ja riippuvuudet nopeasti. Parantaa muiden prompttien laatua.  
**Syöte:** Voit pyytää päivittämistä jos uusia kansioita, riippuvuuksia tai teknisiä piirteitä on lisätty.  
**Tuotos:** Päivitetty yhteenveto (arkkitehtuuri, riippuvuudet, workflow, laajennuspisteet, kehityskäytännöt, ym.).

**Esimerkki** (Luo uusi yhteenveto, jos sellaista ei vielä ole):
```text
/project-summary
```

**Esimerkki** (Päivitä olemassaoleva yhteenveto):
```text
/project-summary
Päivitä projektin yhteenveto. Uudet asiat: lisätty ActiveMQ Artemis, uusi foobar_ domain ja uusi harja-rooli.
```

### 2. Architectural Help (`architectural-help.prompt.md`)
**Tarkoitus:** Suunnitelma ennen toteutusta. Suunnittelee arkkitehtuurikerrokset, riippuvuudet, tietovirrat ym. Ei koodia.  
**Syöte**: Kuvaus ominaisuudesta + tekniset vaatimukset (integraatiot, suorituskyky, tietomallit, käyttöoikeudet).  
**Tuotos**: Suunnitelma (kerrokset, tietovirrat, mahdolliset tekstikaaviot, vaiheistus).

**Esimerkki:**
```text
/architectural-help
```

### 3. Review Code (`review-code.prompt.md`)
**Tarkoitus:** Katselmoi rajatun koodimuutoksen: laatu, toiminnallisuus, suorituskyky, tietoturva, testit, code smell.  
**Syöte:** Hiirellä valittu koodi ja/tai tiedostot kontekstina (ja tarkenna vaikka miksi muutos tehtiin, riskit, suorituskykyhuolet).  
**Tuotos:** Raportti jaottelulla: Code Quality, Functionality, Performance, Security, Code smell, Tests.

**Esimerkki:**
```text
/review-code
Haluamiasi tarkennuksia
```

## Experimental promptit
Seuraavat promptit ovat kokeellisia. Käytä ja anna palautetta.

### Accessibility Audit (`accessibility-review.prompt.md`)
**Tarkoitus:** Analysoi web sivun WCAG 2.1/2.2 ongelmat käyttäen Chrome Devtools / Playwright työkaluja.  
**Syöte:** Sivun URL + mahdolliset tarkennukset (komponentit, tiedostot).  
**Tuotos:** Priorisoitu audit-raportti (issue-taulukko, vaikutus, korjaus, viitteet, mahdolliset screenshotit).

**Esimerkki:**
```text
/accessibility-review
URL: https://localhost:3000/foo/bar. YOU MUST focus on the main dashboard component and its contrasts.
```

### Browser Tuck Debug (`browser-debug_tuck-app-state.prompt.md`)
TODO: Promptin vaativat riippuvuudet eivät vielä repossa.

**Tarkoitus:** Selvittää CLJS Tuck sovelluksen tila selaimessa ennalta määrätyillä debug-funktioilla.  
**Syöte:** URL + kuvaus ongelmasta (event ei päivity, tila ei muutu).  
**Tuotos:** Konsolista kerätty tilasiirtymien analyysi, mahdolliset puuttuvat *Onnistui eventit.

**Esimerkki:**
```text
/browser-debug-tuck-app-state
URL: https://localhost:3000/laadunseuranta. Ongelma: tallennusevent pysähtyy ennen vahvistusta.
```

### DB Migration Helper (`db-migration-helper.prompt.md`)
**Tarkoitus:** Suunnitella Flyway migraatio (taulu/sarake/indeksi/data fix) lukitusriskit minimoiden.  
**Syöte:** Muutoksen tyyppi, tauluprefiksi, datamäärä, FK-suhteet.  
**Tuotos:** Migraatiosuunnitelma + luonnos SQL:stä, indeksit, verifiointi, riskit.

**Esimerkki:**
```text
/db-migration-helper
Tyyppi: Uusi taulu. Prefiksi: lupaus_. Määrä: 5M riviä. FK: kayttaja, urakka.
```

## Yleisiä vinkkejä
- Tarkista onko Project Summary ajan tasalla tai onko sitä ylipäänsä luotu.
- Käytä konkreetisia asioiden nimiä (taulu, namespace, tiedosto), anna tiedostoja konteksteiksi / valitse hiirellä haluamasi koodinpätkä, jotta konteksti tarkentuu.
- Isot suunnittelua vaativat työtehtävät: Luo suunnitelma ennen koodausta (Architectural Help / Migration Helper).
- Käytä Harjan omaa domain-kieltä, mitä koodissakin käytetään tärkeimmistä termeistä.
- Experimental promptit: Lopputulokset voivat olla odottamattomia, tai niistä ei ole hyötyä. Arvioi kriittisesti.

### Päivitys ja ylläpito
1. Lisää uusi prompti luomalla `nimi.prompt.md`.
2. Pyritään käyttämään samankaltaista rakennetta kaikissa prompteissa. 
   * Metadata YAML alkuun
   * ROLE/OBJECTIVE/SUCCESS CRITERIA.
3. Päivitä tämä README:
   * Lisää rivi taulukkoon
   * Lisää kuvaus + esimerkki oikeaan osioon (Stable vs Experimental).


May the odds be ever in your favor. Pidä funktiot puhtaina, ja promptit hallinnassa.
