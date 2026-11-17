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
| Tarkastelusivun entry | `tyokalut/ui_komponenttien_tarkastelu_nakyma.cljs` |
| Tarkastelurunko (navigaatio, kortti, sopimus, `ankkuri`) | `tyokalut/ui_komponenttien_tarkastelu/kehys.cljs` |
| Esikatselut ryhmittäin | `tyokalut/ui_komponenttien_tarkastelu/{alasvedot,toiminnot,lomakkeet}.cljs` |
| Sopimus- ja rekisteritestit (`lein test`) | `test/cljc/harja/ui/komponentit_test.cljc`, `test/cljc/harja/ui/komponentit/rekisteri_test.cljc` |
| Tyylit | `dev-resources/less/komponentit.less`, `dev-resources/less/pages/ui_komponenttien_tarkastelu.less` |

Uuden komponentin lisääminen:

1. Lisää rekisterimerkintä `rekisteri.cljc`:ään (`:id`, `:ryhma`, `:nimi`, `:kuvaus`, `:api`, `:bootstrap-tila`).
2. Lisää esikatselu ryhmän tiedostoon ja exporttaa se `esikatselut`-kartassa rekisterin `:id`:llä.
3. Lisää sopimustesti, jos komponentti on shared-komponentti.
4. `rekisteri/virheet`-testi epäonnistuu, jos rekisterin tiedot ovat puutteelliset.

## Sopimus

- Käyttöpaikka antaa kentälle `:id`:n. Komponentti ei generoi id:tä renderöinnissä.
- Komponentti johtaa `label for`, `<id>-ohje`, `<id>-virhe` sekä `aria-describedby`, `aria-invalid` ja `aria-required`.
- `:data-cy` on oletuksena `:id`. `:luokka` viedään juurielementille.
- Valintaryhmän arvo voi olla mikä tahansa arvo (keyword, luku, merkkijono); komponentti vie arvon sellaisenaan `:vaihda-fn`:lle.
- Komponentit eivät aseta ulkomarginaaleja. Välit kuuluvat käyttöpaikalle tai `pino`/`toimintoryhma`-rakenteelle.
- Bootstrap-riippuvuus kirjataan rekisterissä `:bootstrap-tila`-arvolla (`:ei-riippuvuutta`, `:osittain-riippuvainen`, `:riippuvainen`, `:maarittelematta`).

## Tunnetut rajoitukset

- `kentat/tee-kentta` tukee vain `:elementin-id`:tä, ei `aria-describedby`- tai `aria-invalid`-attribuutteja. Kenttäryhmän ARIA-yhteys toimii natiiveille kentille (valintaryhmä), mutta `tee-kentta`-kentille se vaatii erillisen muutoksen `kentat.cljs`:ään.
- Tarkastelusivun esikatselut (CLJS) validoidaan selaimessa. Rakennetestit kattavat vain cljc-puolen hiccup-rakenteen.
- Tarjoushinnat-näkymän `urakka-haku-id` on edelleen render-aikainen `gensym`.
- Livi-pudotusvalikko ja nappi on rekisteröity, mutta niiden sopimusta ei ole vielä määritelty uudelleen.

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
