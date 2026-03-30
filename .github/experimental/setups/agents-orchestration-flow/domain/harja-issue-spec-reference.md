# Harja issue spec reference

## Tavoite

Tämä dokumentti auttaa issue-spec-agenttia muuntamaan ticketin tai Jira-kuvauksen Harjan oikeaan domain-kieleen ja tuottamaan uskottavan specin tai Jira-ready issue -kuvauksen ilman arvailua.

## Milloin käyttää

Käytä tätä referenssiä, kun tehtävään kuuluu:
- ticketin tai Jira-kuvauksen tarkentaminen speciksi
- hyväksytyn specin tai suunnitelman kääntäminen Jira-ready issueksi
- issue-tekstin terminologian kääntäminen Harjan oikeisiin käsitteisiin
- edge casejen, rajoitteiden ja avoimien kysymysten kerääminen ennen toteutussuunnittelua
- iteratiivisesti päivittyvän `plans/<topic-slug>/spec.md`-tiedoston ylläpito

Tyypilliset agentit:
- `support-issue-spec`

## Ydinohjeet

### Terminologian kartoitus

Kun ticketissa käytetään yleistä tai epätarkkaa sanastoa, kartoita ainakin:
- vastaava käyttöliittymän termi
- vastaava domain-käsite koodissa
- vastaava API-, endpoint-, skeema- tai tietomallitasoinen termi, jos sellainen on olennainen

Jos ticketin sana ja sovelluksen todellinen sana eroavat toisistaan, kirjaa molemmat:
- issue wording
- mapped application term

### Plan tai spec -> Jira issue

Kun lähtömateriaalina on jo hyväksytty spec tai suunnitelma, purista siitä Jira-ready issue, joka:
- säilyttää Harjan oikean terminologian
- ei kadota olennaisia rajoja tai acceptance-shapea
- ei liioittele valmiutta, jos open questions ovat yhä auki

Tarkista ainakin:
- onko materiaali yhden Jira-issuen kokoinen vai pitääkö se pilkkoa
- mitä kannattaa nostaa kuvaustekstiin, mitä acceptance criteriaan, ja mitä open questions -listaan
- mitkä tekniset yksityiskohdat ovat tickettiin liian matalan tason toteutusdetaljeja

### Mitä koodista kannattaa selvittää

Ennen kuin vaatimus kovettuu, tarkista mahdollisuuksien mukaan:
- missä käyttöliittymässä tai prosessivaiheessa toiminto oikeasti elää
- onko olemassa vastaava tai osittain samanlainen toteutus
- mitä valinnainen tai pakollinen validointi jo tekee
- liittyykö käyttötapaukseen authorization-, workflow- tai tilasiirtymärajoitteita
- mitä edge caseja nykyinen toteutus tai data jo paljastaa

### Specin työskentelymuoto

Issue-spec on iteratiivinen työdokumentti. Pidä näkyvissä ainakin:
- ticketin tavoite omin sanoin tarkennettuna
- mapped terminology
- nykyinen ymmärretty user flow
- boundaries ja ei-tavoitteet
- edge cases ja limitations
- open questions
- change log tai päivitysmerkinnät, kun spec elää usean kierroksen ajan

### Dokumenttihygienia

`plans/<topic-slug>/spec.md` on yleinen source-of-truth -dokumentti, ei asiakas-, sopimus- tai urakkakohtainen muistio.

Älä jätä speciin irrallisia huomioita kuten:
- yksittäisen asiakkaan erityistoive, ellei siitä ole päätetty yleistä tuotteen sääntöä
- yhden urakan, sopimuksen tai pilotin tapauskohtaista havaintoa, ellei se muuta yleistä käyttäytymistä
- tilapäistä debug- tai selvityshavaintoa, joka ei kuulu pysyvään määrittelyyn

Jos tällainen tieto on tärkeä, kirjoita se yleisenä tuotteen tai domainin sääntönä ilman viittausta yksittäiseen asiakkaaseen tai urakkaan.

Esimerkki:
- huono: `Urakassa X asiakkaalle Y näytetään eri varoitus`
- parempi: `Varoitus näytetään vain niissä urakkatyypeissä, joissa käyttäjällä on oikeus kyseiseen toimenpiteeseen`

### Tallennussijainti

Oletustallennus on `plans/<topic-slug>/spec.md`.

Suosi olemassa olevaa aihehakemistoa, jos sellainen on jo käytössä.
Jos aihehakemistoa ei ole vielä olemassa, johda `topic-slug` ensisijaisesti nykyisen feature branchin nimestä.
Jos feature branchia ei ole, branch on geneerinen eikä kuvaa tehtävää, tai branchin ja tehtävän välillä on ristiriita, kysy käyttäjältä slug ennen tallennusta.

Normalisoi branch-pohjainen `topic-slug` näin:
- muunna pieniksi kirjaimiksi
- korvaa `/` ja `_` merkillä `-`
- tiivistä peräkkäiset `-` merkit yhdeksi
- poista alusta ticket-prefiksi muodossa `<kirjaimet>-<numerot>-`, jos loppuosa on edelleen kuvaava

Jos käyttäjä tai repository-konteksti vaatii toisen sijainnin, nosta se eksplisiittisesti esiin. Jos issue-draft tuotetaan vain vastaukseen, sano se suoraan.

### Milloin siirtyä `10-flow-plan`-agenttiin

Siirry `10-flow-plan`-agenttiin, kun:
- open questions on ratkaistu riittävän hyvin
- tuotteen termit ja rajat on lukittu uskottavasti
- issue-specin tarkoitus muuttuu toteutuksen vaiheistamiseksi

## Ristiviitteet

- Käytä `harja-planning-and-spec-reference.md`, kun issue-specistä siirrytään toteutussuunnitelmaan tai `plans/`-konvention mukaiseen dokumenttiin.
- Käytä `harja-feature-implementation-reference.md`, kun terminologia tai muutosalue pitää ankkuroida Harjan todelliseen feature-rakenteeseen.
- Käytä `harja-validation-review-reference.md`, kun issue koskee write-pathia, authorizationia tai validation-logiikkaa.

## Käyttöohje agentille

- viittaa tähän dokumenttiin, kun tehtävä koskee Jira-kuvauksen tarkentamista speciksi tai specin puristamista Jira-ready issueksi
- tarkenna terminologia koodista ja käyttöliittymästä ennen kuin lukitset vaatimuskielen
- pidä tallennettu spec yleisellä tuote- ja domain-tasolla ilman irrallisia asiakas- tai urakkakohtaisia muistiinpanoja
- pidä agenttiprompti tutkimus-, tarkennus- ja päätöksentekokeskeisenä

## Rajaus

Tämä dokumentti ei määritä:
- lopullista implementation plan -rakennetta
- pakollista toteutusvaiheiden jakoa
- review- tai verify-vaiheen sopimuksia

Ne kuuluvat edelleen planning-, review- ja verify-agenttien vastuulle.
