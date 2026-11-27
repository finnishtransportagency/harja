---
name: komponenttijarjestelma
description: Harjan komponenttijärjestelmän (harja.ui.perus, komponenttirekisteri, UI-komponenttien tarkastelusivu, Sivusimulaattori ja esimerkkisivut) kehittäminen ja oikea käyttö. Käytä aina kun lisäät tai muutat shared-komponenttia, rekisterimerkintää, esimerkkisivua (.edn), komponentit.less-tiedostoa tai rakennat uutta näkymää shared-komponenteilla. Sisältää välisäännön (gap, ei ulkomarginaaleja), API- ja DOM-sopimuksen, työjärjestyksen ja validoinnin.
---

# Komponenttijärjestelmä

Harjan uusi UI rakennetaan shared-komponenteista (`harja.ui.perus`). Tämä skill kertoo, miten järjestelmää kehitetään ja käytetään. Yksityiskohtainen sopimus on dokumentissa [ui_komponenttien_tarkastelu_rajaukset.md](../../../src/cljs/harja/views/hallinta/tyokalut/ui_komponenttien_tarkastelu_rajaukset.md). Lue se, kun muutat sopimusta, rekisteriä tai simulaattoria.

## Sijainnit

| Osa | Sijainti |
| --- | --- |
| Shared-komponentit | `src/cljc/harja/ui/perus.cljc` |
| Rekisteri (data, eheystarkistus) | `src/cljc/harja/ui/perus/rekisteri.cljc` |
| Simulaattori (EDN-puun validointi) | `src/cljc/harja/ui/perus/simulaattori.cljc` |
| Esimerkkisivut (EDN) | `src/cljc/harja/ui/perus/esimerkkisivut/*.edn` |
| Tarkastelusivu ja esikatselut | `src/cljs/harja/views/hallinta/tyokalut/ui_komponenttien_tarkastelu/*.cljs` |
| Tyylit | `dev-resources/less/komponentit.less` |
| Testit | `test/cljc/harja/ui/perus_test.cljc`, `test/cljc/harja/ui/perus/*_test.cljc`, `test/clj/harja/ui/perus/*_test.clj` |

## Rajaus

- Tuotannossa olevia komponentteja (`src/cljs/harja/ui/`, `src/cljs/harja/views/`) ei muokata, ennen kuin komponenttijärjestelmän ensimmäinen versio on `develop`-haarassa. Uusi järjestelmä rakennetaan ja validoidaan erillään niistä.
- Bootstrap-käyttöpaikkoja ei porteta tämän skillin perusteella. Tunnista Bootstrap-riippuvuus, kirjaa se rekisterin `:bootstrap-tila`-arvoon ja kerro käyttäjälle. Älä korjaa sitä. Porttaus tehdään erillisenä viipaleena `bootstrap-porttaus`-skillillä.
- Älä yhdistä samaan muutokseen komponentin rakentamista, Bootstrap-porttausta, laajaa CSS-modernisointia ja nimeämisremonttia.
- Älä siirrä domain-tilaa, backend-kutsuja tai domain-laskentaa komponenttiin. Näkymä omistaa datan ja päätökset, komponentti omistaa renderöinnin, DOM-rakenteen, saavutettavuuden ja `data-cy`-ankkurit.
- Älä lisää doo- tai PhantomJS-testejä.

## Välisääntö (pakollinen)

Komponentit eivät omista ulkoreunojaan. Vanhempi omistaa lasten välin `gap`-ominaisuudella ja nimetyllä tokenilla (`:pieni`, `:keski`, `:suuri`, LESS: `@ui-vali-*`). Sama periaate on MUI `Stack`, Chakra `VStack` ja Every Layoutin `Stack`.

- Komponentin juurielementti ei aseta `margin-top`-, `margin-bottom`- tai `margin`-arvoa, joka olisi muu kuin `0` tai `auto`. Vaakakeskitys (`margin-inline: auto`) on sallittu.
- Säiliö, joka sijoittaa lapsia, omistaa niiden välin. Myös sisäinen sisältö-wrapper (`.ui-osio-sisalto`, `.ui-kortti-sisalto`) on `display: flex; flex-direction: column` ja sillä on `gap`.
- Jos lapset ovat kiinni toisissaan, korjaa vanhemman gap. Älä lisää lapselle marginaalia, spacer-CSS:ää, `* + *`-valitsinta tai `:margin`-asetusta API:in.
- Sivun ylä- ja alareunan tila on vanhemman paddingia, ei marginaalia.
- Älä kääri valinnaista sisältöä tyhjään wrapperiin. Gap luo välin myös tyhjän lapsen ympärille.
- Legacy-komponentit ja raa'at `<p>`-elementit tuovat omat globaalit marginaalinsa gapin päälle. Älä korjaa niitä tässä vaiheessa. Käytä esikatseluissa ja esimerkeissä `div`-elementtejä tai komponentteja ja kirjaa legacy-marginaali käyttäjälle.
- Valvonta: `lein test :only harja.ui.perus.valit-test`. Uusi säiliökomponentti ei ole valmis ennen kuin sisarten väli on mitattu selaimessa (`getBoundingClientRect`: väli = token).

## Komponentin kehittäminen

Työjärjestys on pakollinen. Älä aloita toteutuksesta.

1. **Tunnista käyttöpaikka.** Mikä näkymä, mikä on datan canonical source, mitkä ovat normaali-, lataus-, tyhjä- ja virhetilat, mitä saavutettavuusvaatimuksia on. Hae käyttöaste (`grep`) ja tarkista rekisteri (`lein komponenttirekisteri :ryhma lomakkeet`). Älä tee komponenttia pelkän abstraktion vuoksi tai ennen kuin toinen todellinen käyttöpaikka osoittaa tarpeen.
2. **Lukitse API ja DOM-sopimus.** Argumentit ja oletukset, callbackit, roolit, fokus, `data-cy`-ankkurit, sallitut poikkeukset. Pidä API pienenä ja säilytä se ennallaan, jos komponentti on jo käytössä. Älä sido käyttöpaikkaa komponentin sisäiseen DOM-rakenteeseen tai CSS-luokkiin.
3. **Lisää rekisterimerkintä** (`rekisteri.cljc`): `:id`, `:ryhma`, `:nimi`, `:kuvaus`, `:funktio`, `:asetukset`, `:slotit`, `:tilat`, `:sallitut-vanhemmat`, `:suositus`, `:korvaa`, `:bootstrap-tila`. Kun layout-komponentti saa uuden tyypillisen lapsen, lisää layout-komponentin id lapsen `:sallitut-vanhemmat`-joukkoon.
4. **Lisää esikatselu tarkastelusivulle** ryhmän tiedostoon: `(register! :id {:esikatselu ... :renderoi ...})`. Jokainen id esiintyy `register!`-kutsussa täsmälleen kerran. Kortin pitää näyttää käyttötarkoitus, public API, normaalitila ja rajatilat, toteutustila sekä juuri- ja lapsiankkurit.
5. **Kirjoita kapea regressiotesti ennen laajaa käyttöä** (`perus_test.cljc`): renderöinti, normaali- ja rajatilat, callbackit, saavutettavuusankkurit, vakaa `data-cy`, virheelliset tokenit (`{:pre ...}` heittää). Todenna, että testi epäonnistuu ilman muutosta.
6. **Toteuta** komponentti ja LESS. Tyylit tulevat Väylä-tokeneista (`colors.less`, `typography.less`, `@ui-vali-*`), rem-yksiköillä.
7. **Validoi** (ks. alla).

### Nimeäminen ja API-sopimus

- Suomenkielinen, viivapohjainen ja litteä nimeäminen (`ui-kortti-otsikko`). Ei BEM-nimeämistä (`__`, `--`).
- Uuden merkinnän (`:suositus :uuteen-ui`) asetuksissa: `:luokka` (ei `:class`), `:disabled` (ei `:disabled?`), `:vaihda-fn` (ei `:valitse-fn`/`:valinta-fn`). `-fn`-päätteisen asetuksen tyyppi on `:funktio` ja `?`-päätteisen `:boolean`.
- Käyttöpaikka antaa `:id`:n. Komponentti ei generoi id:tä renderöinnissä, vaan johtaa siitä `label for`, `<id>-ohje`, `<id>-virhe`, `aria-describedby`, `aria-invalid` ja `aria-required`.
- `:data-cy` on oletuksena `:id`. `:luokka` lisätään juurielementille.
- Valinta välitetään callbackille täsmälleen siinä muodossa kuin vaihtoehto annettiin.
- Tokenit ovat nimettyjä (`:pieni`, `:keski`, `:suuri`). Vapaata CSS:ää tai pikseliarvoja ei välitetä asetuksina.

## Komponenttien käyttö (näkymät ja esimerkkisivut)

- Uuteen näkymäkoodiin ei lisätä suoraa Bootstrap-markupia: `.row`, `.col-*`, `.form-group`, `.panel`, `.dropdown`, `.dropdown-menu`. Ei myöskään näkymäkohtaista CSS:ää komponentin sisäisen rakenteen hallintaan eikä marginaaleja lasten välin tekemiseen.
- Jos sopivaa komponenttia ei ole, tee ensin pieni shared-komponentti ja sen tarkastelukortti. Uuden komponentin sisäinen toteutus saa käyttää vanhaa toteutusta väliaikaisesti, jos API ja DOM-sopimus säilyvät.
- Sivurakenne: `sailio` (leveys ja sisäreuna) → `osio` (otsikollinen alue, `:vali`) → `kortti`, `pino`, `vaaka`, `ruudukko`. `kortti` ei aseta ulkomarginaalia, joten väli tulee ympäröivältä `osio`lta tai `pino`lta.
- Uusi esimerkkisivu lisätään EDN-tiedostona (`esimerkkisivut/<snake_case>.edn`): `{:id ... :nimi ... :kuvaus ... :puu [...]}`. Ei funktioita, symboleja eikä hiccupia. Käytä vain komponentteja, joilla on simulaattorin `:renderoi`. Lähtökohdaksi sopii lähin olemassa oleva esimerkki, ja sen `:data-cy`-ankkurien malli säilytetään.
- Esimerkit ladataan käännösaikaisella makrolla. EDN-muutos ei laukaise Figwheel-reloadia: kosketa `esimerkkisivut.cljc`-tiedostoa tai käännä uudelleen.

## Validointi

Aja pienin kattava joukko:

```
lein komponenttirekisteri :id <id>        # rekisterin sisältö
lein test :only harja.ui.perus.valit-test
lein test harja.ui.perus-test harja.ui.perus.rekisteri-test harja.ui.perus.simulaattori-test harja.ui.perus.esimerkkisivut-test harja.ui.perus.bootstrap-luokat-test
npm run build-css                          # kun LESS muuttui
lein compile-dev                           # kun CLJS muuttui
```

Rakennetestit kattavat vain cljc-puolen hiccupin. CLJS-esikatselut ja CSS-käyttäytyminen validoidaan selaimessa Playwrightilla (`playwright-cli`).

### Selainvalidointi

- Dev-sovellus: `http://localhost:3000/`. Todennus tulee `OAM_*`-headereista, joten aseta ne `page.context().setExtraHTTPHeaders({...})`-kutsulla ennen navigointia (esim. `OAM_REMOTE_USER: jvh`, `OAM_GROUPS: Jarjestelmavastaava`, `OAM_ORGANIZATION: Liikennevirasto` sekä nimi-, sähköposti- ja puhelinheaderit). Pelkkä sivun avaus näyttää "Todennusvirhe".
- Tarkastelusivu: Hallinta → Työkalut-välilehti → "UI-komponenttien tarkastelu". Suora hash-osoite ei toimi, vaan klikkaa välilehdet (`.tabs-taso1`, `.tabs-taso2`). Sivun sisällä "Sivusimulaattori" ja esimerkkivalitsin (`data-cy=ui-simulaattori-esimerkki`) renderöivät EDN-esimerkit.
- CSS tarjoillaan `http://localhost:3001/css/application.css`-osoitteesta. `npm run build-css` päivittää sen, mutta selain voi käyttää välimuistia. Avaa selainsessio uudelleen, jos tyylit eivät päivity.
- Mittaa väli luvuilla, älä silmällä: `getBoundingClientRect().top - edellinen.bottom` pitää olla tokenin arvo (`:pieni` 8 px, `:keski` 16 px, `:suuri` 24 px).
- Tarkista normaali-, tyhjä-, lataus-, virhe- ja kapea tila tarpeen mukaan sekä pitkät tekstit.
- Siivoa väliaikaiset tiedostot ja palvelimet lopuksi.

## Valmis kun

- API ja DOM-ankkurit on dokumentoitu rekisterissä ja tarkastelusivulla sekä testattu
- komponentti löytyy tarkastelusivulta ja `:bootstrap-tila` on rekisterissä ajan tasalla
- välisääntö täyttyy ja `valit-test` menee läpi
- domain-tila pysyy näkymän vastuulla
- olemassa olevia Bootstrap-käyttöpaikkoja ei ole muutettu
- selainpolku toimii normaalitilassa ja olennaisessa rajatilassa

## Lopputiivistys

Kerro lopuksi: toteutuiko komponentti vai jäikö se kesken, mitä jäi tekemättä, seuraavat 1–3 askelta ja suositeltu seuraava komponentti tai UI-alue.
