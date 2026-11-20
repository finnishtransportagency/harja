# UI-komponenttien tarkastelun rajaus

Tämä dokumentti on tarkastelusivun review-pinta. Arvioi muutoksia ensin tämän rajauksen kautta.

## Tavoite

Rakentaa Harjan uutta UI:ta shared-komponenttien avulla niin, että:

- näkymä omistaa domain-datan, tilan, haut, oikeudet ja callbackien päätökset
- shared-komponentti omistaa DOM-rakenteen, saavutettavuuden, vuorovaikutuksen ja vakaat `data-cy`-ankkurit
- uusi UI ei lisää suoraa Bootstrap-markupia eikä näkymäkohtaista yleis-CSS:ää
- komponentin public API näkyy tarkastelusivulla ennen tuotantokäyttöpaikkaa

## Rakenne

| Osa | Sijainti |
| --- | --- |
| Shared-komponentit (`pino`, `suodatinrivi`, `suodatin`, `kenttaryhma`, `valintaryhma`, `toimintoryhma`, `tilaviesti`) | `src/cljc/harja/ui/komponentit.cljc` |
| Komponenttirekisteri (data) ja eheystarkistus | `src/cljc/harja/ui/komponentit/rekisteri.cljc` |
| Rekisterin EDN-tulostus komentoriviltä | `src/clj/harja/tyokalut/komponenttirekisteri.clj` (`lein komponenttirekisteri`) |
| Tarkastelusivun entry | `tyokalut/ui_komponenttien_tarkastelu_nakyma.cljs` |
| Tarkastelurunko (navigaatio, kortti, sopimus, `ankkuri`) | `tyokalut/ui_komponenttien_tarkastelu/kehys.cljs` |
| Esikatselut ryhmittäin | `tyokalut/ui_komponenttien_tarkastelu/{alasvedot,toiminnot,lomakkeet,gridit}.cljs` |
| Sopimus- ja rekisteritestit (`lein test`) | `test/cljc/harja/ui/komponentit_test.cljc`, `test/cljc/harja/ui/komponentit/rekisteri_test.cljc` |
| Tyylit | `dev-resources/less/komponentit.less`, `dev-resources/less/pages/ui_komponenttien_tarkastelu.less` |

Uuden komponentin lisääminen:

1. Lisää rekisterimerkintä `rekisteri.cljc`:ään (`:id`, `:ryhma`, `:nimi`, `:kuvaus`, `:funktio`, `:asetukset`, `:bootstrap-tila`).
2. Lisää esikatselu ryhmän tiedostoon `register!`-kutsulla (`rekisterointi.cljs`) rekisterin `:id`:llä. Kaksi rekisteröintiä samalla id:llä on virhe.
3. Lisää sopimustesti, jos komponentti on shared-komponentti.
4. `rekisteri/virheet`-testi epäonnistuu, jos rekisterin tiedot ovat puutteelliset.

## Rekisterin tulostus

`lein komponenttirekisteri` tulostaa rekisterin EDN:nä stdoutiin ilman appia, tietokantaa tai selainta. Suodattimet annetaan avain-arvo-pareina: `lein komponenttirekisteri :id nappi` tai `lein komponenttirekisteri :ryhma lomakkeet`. Tuloksen avaimet: `:id`, `:ryhma`, `:rooli`, `:suositus`, `:korvaa`, `:slotit`, `:tilat`, `:sallitut-vanhemmat`, `:asetukset`, `:bootstrap-tila`, `:ankkuri`. Funktioarvot jätetään pois. Tuntematon `:id` tulostaa virheen stderriin ja päättyy koodilla 1.

## Sopimus

- Käyttöpaikka antaa kentälle `:id`:n. Komponentti ei generoi id:tä renderöinnissä.
- Komponentti johtaa `label for`, `<id>-ohje`, `<id>-virhe` sekä `aria-describedby`, `aria-invalid` ja `aria-required`.
- `:data-cy` on oletuksena `:id`. `:luokka` viedään juurielementille.
- Valintaryhmän arvo voi olla mikä tahansa arvo (keyword, luku, merkkijono); komponentti vie arvon sellaisenaan `:vaihda-fn`:lle.
- Komponentit eivät aseta ulkomarginaaleja. Välit kuuluvat käyttöpaikalle tai `pino`/`toimintoryhma`-rakenteelle.
- Bootstrap-riippuvuus kirjataan rekisterissä `:bootstrap-tila`-arvolla (`:ei-riippuvuutta`, `:osittain-riippuvainen`, `:riippuvainen`, `:maarittelematta`).
- Jokaisella rekisterimerkinnällä on `:suositus` (`:uuteen-ui`, `:vanha`, `:ei-suositusta`) ja `:korvaa`-lista (tekstit vanhoista vastineista, tyhjä vektori jos ei korvausta). Sopimusruutu näyttää molemmat.
- Uudessa merkinnässä (`:suositus :uuteen-ui`) kielletyt asetusnimet ovat `:class`, `:classes`, `:disabled?`, `:disabloi`, `:valitse-fn` ja `:valinta-fn`. Käytä `:luokka`, `:disabled` ja `:vaihda-fn`. `-fn`-päätteisen asetuksen tyypin on oltava `:funktio` ja `?`-päätteisen `:boolean`. Vanhat merkinnät (`:vanha`, `:ei-suositusta`) saavat käyttää vanhoja nimiä, mutta tyyppisäännöt koskevat vain uusia merkintöjä.
- Palautekomponentit ovat ryhmässä `:palautteet`. Yhteinen varianttisanasto on `palaute-variantit` (`:onnistui`, `:info`, `:varoitus`, `:virhe`). Komponenttien olemassa olevia variantteja ei ole vielä nimetty uudelleen.

## Tunnetut rajoitukset

- `kentat/tee-kentta` tukee vain `:elementin-id`:tä, ei `aria-describedby`- tai `aria-invalid`-attribuutteja. Kenttäryhmän ARIA-yhteys toimii natiiveille kentille (valintaryhmä), mutta `tee-kentta`-kentille se vaatii erillisen muutoksen `kentat.cljs`:ään.
- Tarkastelusivun esikatselut (CLJS) validoidaan selaimessa. Rakennetestit kattavat vain cljc-puolen hiccup-rakenteen.
- Tarjoushinnat-näkymän `urakka-haku-id` on edelleen render-aikainen `gensym`.
- Livi-pudotusvalikko on rekisteröity, mutta sen sopimusta ei ole vielä määritelty uudelleen. Nappi on merkitty `:osittain-riippuvaiseksi`, koska Bootstrap-luokat tulevat käyttöpaikan `:luokka`-optiosta.
- Kenttätyyppi `:valinta` renderöityy livi-pudotusvalikon kautta, joten sille ei ole omaa korttia. Sen Bootstrap-riippuvuus on sama kuin livi-pudotusvalikolla.
- Gridit (`perusgrid`, `muokkausgrid`) ovat `:riippuvainen`, koska ne käyttävät `.panel`-, `.panel-default`- ja `btn-xs`-luokkia.
- Checkbox-ryhmä on `:osittain-riippuvainen`: yksi palsta on puhdas, useampi palsta käyttää `col-sm-*`-luokkia.
- Modal on `:riippuvainen`, koska `modal-container` käyttää yhä `.modal`-, `.modal-dialog`- ja `.modal-content`-luokkia.

## Rajauksen ulkopuolella

Älä laajenna tätä kokonaisuutta ilman erillistä päätöstä:

- olemassa olevien Bootstrap-käyttöpaikkojen yleinen porttaus
- dropdownien fokus-, Escape- ja ulkopuolisen klikkauksen uuden sopimuksen määrittely
- domain-tilan, Tuck-tapahtumien tai backend-kutsujen siirtäminen shared-komponenttiin
- kaikkien nappien, kenttien, gridien tai modaalien yhdistäminen tähän namespaceen
- näkymäkohtaisen CSS:n muuttaminen yleiseksi vain luokkien yhdenmukaistamisen vuoksi
- laaja nimeämis-, LESS- tai Bootstrap-overridejen siivous

## Review-portti

Review hyväksyy uuden muutoksen vain, jos kaikki täyttyvät:

- public API ja vastuunjako ovat näkyvissä tarkastelusivulla tai lähikoodin dokumentaatiossa
- käyttöpaikka käyttää vain dokumentoituja `data-cy`-ankkureita ja saavutettavuusrooleja
- domain-tila pysyy näkymässä
- normaali- ja olennaiset rajatilat on validoitu
- `lein compile-dev`, tarvittaessa `npm run build-css`, editoridiagnostiikka ja selainpassi ovat vihreät
- uusi käyttöpaikka ei lisää suoraa Bootstrap-markupia
- muutoksen laajuus on yksi komponenttisopimus tai yksi tuotantokäyttöpaikka

## Seuraava sallittu askel

Siirrä yksi uusi tuotannon UI-alue komponenttien käyttöön ja lisää tarvittavat komponentit rekisteriin yksi kerrallaan. Bootstrap-porttaus tehdään erillisenä viipaleena vasta, kun kyseisen komponentin sopimus on tarkastelusivulla vakaa.
