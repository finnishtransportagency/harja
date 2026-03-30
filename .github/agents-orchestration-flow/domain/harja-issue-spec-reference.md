# Harja Issue Spec Reference

## Tavoite

Tämä dokumentti auttaa issue-spec-agenttia muuntamaan ticketin tai Jira-kuvauksen Harjan oikeaan domain-kieleen ja tuottamaan uskottavan specin tai Jira-ready issue -kuvauksen ilman arvailua.

## Milloin Käyttää

Käytä tätä referenssiä, kun tehtävään kuuluu:
- ticketin tai Jira-kuvauksen tarkentaminen speciksi
- hyväksytyn specin tai suunnitelman kääntäminen Jira-ready issueksi
- issue-tekstin terminologian kääntäminen Harjan oikeisiin käsitteisiin
- edge casejen, rajoitteiden ja avoimien kysymysten kerääminen ennen toteutussuunnittelua
- iteratiivisesti päivittyvän `specs/spec-<jiraid>.md`-tiedoston ylläpito

Tyypilliset agentit:
- `support-issue-spec`

## Ydinohjeet

### Terminologian Kartoitus

Kun ticketissa käytetään yleistä tai epätarkkaa sanastoa, kartoita ainakin:
- vastaava käyttöliittymän termi
- vastaava domain-käsite koodissa
- vastaava API-, endpoint-, skeema- tai tietomallitasoinen termi, jos sellainen on olennainen

Jos ticketin sana ja sovelluksen todellinen sana eroavat toisistaan, kirjaa molemmat:
- issue wording
- mapped application term

### Plan Tai Spec -> Jira Issue

Kun lähtömateriaalina on jo hyväksytty spec tai suunnitelma, purista siitä Jira-ready issue, joka:
- säilyttää Harjan oikean terminologian
- ei kadota olennaisia rajoja tai acceptance-shapea
- ei liioittele valmiutta, jos open questions ovat yhä auki

Tarkista ainakin:
- onko materiaali yhden Jira-issuen kokoinen vai pitääkö se pilkkoa
- mitä kannattaa nostaa kuvaustekstiin, mitä acceptance criteriaan, ja mitä open questions -listaan
- mitkä tekniset yksityiskohdat ovat tickettiin liian matalan tason toteutusdetaljeja

### Mitä Koodista Kannattaa Selvittää

Ennen kuin vaatimus kovettuu, tarkista mahdollisuuksien mukaan:
- missä käyttöliittymässä tai prosessivaiheessa toiminto oikeasti elää
- onko olemassa vastaava tai osittain samanlainen toteutus
- mitä valinnainen tai pakollinen validointi jo tekee
- liittyykö käyttötapaukseen authorization-, workflow- tai tilasiirtymärajoitteita
- mitä edge caseja nykyinen toteutus tai data jo paljastaa

### Specin Työskentelymuoto

Issue-spec on iteratiivinen työdokumentti. Pidä näkyvissä ainakin:
- ticketin tavoite omin sanoin tarkennettuna
- mapped terminology
- nykyinen ymmärretty user flow
- boundaries ja ei-tavoitteet
- edge cases ja limitations
- open questions
- change log tai päivitysmerkinnät, kun spec elää usean kierroksen ajan

### Tallennussijainti

Oletustallennus on `specs/spec-<jiraid>.md`.

Jos käyttäjä tai repository-konteksti vaatii toisen sijainnin, nosta se eksplisiittisesti esiin. Jos issue-draft tuotetaan vain vastaukseen, sano se suoraan.

### Milloin Siirtyä `10-flow-plan`-Agenttiin

Siirry `10-flow-plan`-agenttiin, kun:
- open questions on ratkaistu riittävän hyvin
- tuotteen termit ja rajat on lukittu uskottavasti
- issue-specin tarkoitus muuttuu toteutuksen vaiheistamiseksi

## Ristiviitteet

- Käytä `harja-planning-and-spec-reference.md`, kun issue-specistä siirrytään toteutussuunnitelmaan tai `.prd`-konvention mukaiseen dokumenttiin.
- Käytä `harja-feature-implementation-reference.md`, kun terminologia tai muutosalue pitää ankkuroida Harjan todelliseen feature-rakenteeseen.
- Käytä `harja-validation-review-reference.md`, kun issue koskee write-pathia, authorizationia tai validation-logiikkaa.

## Käyttöohje Agentille

- viittaa tähän dokumenttiin, kun tehtävä koskee Jira-kuvauksen tarkentamista speciksi tai specin puristamista Jira-ready issueksi
- tarkenna terminologia koodista ja käyttöliittymästä ennen kuin lukitset vaatimuskielen
- pidä agenttiprompti tutkimus-, tarkennus- ja päätöksentekokeskeisenä

## Rajaus

Tämä dokumentti ei määritä:
- lopullista implementation plan -rakennetta
- pakollista toteutusvaiheiden jakoa
- review- tai verify-vaiheen sopimuksia

Ne kuuluvat edelleen planning-, review- ja verify-agenttien vastuulle.
