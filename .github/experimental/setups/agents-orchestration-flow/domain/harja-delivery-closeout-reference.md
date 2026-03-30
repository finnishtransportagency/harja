# Harja delivery closeout reference

## Tavoite

Tämä dokumentti kokoaa Harjan flow-loppupään closeout-odotukset ilman, että yksittäinen flow-agentti kovakoodaa projektikohtaisia yksityiskohtia omaan runkoonsa.

Periaate:
- PR-ready tila ei yksin riitä closeoutiksi
- alkuperäisen source-of-truth -dokumentin tulee vastata toimitettua lopputulosta
- docs-muutokset on tehtävä tai eksplisiittisesti todettava tarpeettomiksi
- paikallinen worktree siivotaan vain, jos se on turvallista ja oikeasti toivottua

## Milloin käyttää

Käytä tätä referenssiä, kun tehtävään kuuluu:
- delivery flown lopettaminen paikallisesti
- alkuperäisen specin, issue-specin tai suunnitelman päivittäminen toteutuneeseen lopputulokseen
- relevantin dokumentaation päivittäminen closeout-vaiheessa
- mahdollisen worktreen turvallinen sulkeminen closeoutin yhteydessä

Tyypilliset agentit:
- `15-flow-closeout`

## Ydinohjeet

### Source-of-truth -tarkistus

Closeoutissa kannattaa tarkistaa ensin, mikä toimi muutoksen alkuperäisenä totuuslähteenä:
- `plans/<topic-slug>/plan.md`
- `plans/<topic-slug>/spec.md`
- issue-spec-tyyppinen tarkennusdokumentti
- siirtymävaiheessa `specs/spec-<jiraid>.md`
- siirtymävaiheessa `.prd`-hakemistossa oleva suunnitelma tai spec
- muu eksplisiittisesti sovittu planning- tai spec-dokumentti

Jos sellainen löytyy, agentin tulee päivittää siihen ainakin:
- toteutunut lopputulos olennaisella tasolla
- poikkeamat alkuperäisestä odotuksesta
- jäljelle jääneet follow-upit tai known limitations

Jos `plans/`-rakenteesta ei löydy ilmeistä totuuslähdettä, käytä nykyisestä feature branchista normalisoitua `topic-slugia` oletushakuvihjeenä:
- muunna branch pieniksi kirjaimiksi
- korvaa `/` ja `_` merkillä `-`
- tiivistä peräkkäiset `-` merkit yhdeksi
- poista alusta ticket-prefiksi muodossa `<kirjaimet>-<numerot>-`, jos loppuosa jää kuvaavaksi

Jos tällä logiikalla löytyy useita uskottavia vaihtoehtoja tai ei yhtään uskottavaa osumaa, kysy käyttäjältä ennen closeout-päivitystä.

### Dokumentaatio

Closeoutissa kannattaa tarkistaa, muuttuiko jokin seuraavista:
- käyttäjän näkemä toiminnallisuus tai käyttöpolku
- setup- tai kehitysohje
- operatiivinen tai ympäristöihin liittyvä käytäntö
- muu dokumentoitu prosessi, jonka pitäisi seurata toteutusta

Jos relevanttia dokumentaatiota ei tarvittu, agentin kannattaa sanoa se eksplisiittisesti eikä jättää asiaa auki.

### Follow-upit ja rajoitteet

Closeout ei tarkoita, että kaiken pitäisi näyttää täydelliseltä.

Harjassa closeout-raporttiin kannattaa jättää näkyviin ainakin:
- tietoisesti deferoidut jatkotoimet
- known limitations
- avoimet kysymykset, jotka eivät blokkaa toimitusta mutta vaikuttavat seuraaviin kierroksiin
- mahdolliset manuaaliset jatkoaskeleet

### Worktree cleanup

Jos työ on tehty worktreessa, closeout-vaiheessa voi olla tarve sulkea se.

Tämä kannattaa tehdä vain, kun:
- user haluaa worktreen pois
- uncommitted changes eivät katoa hiljaisesti
- local-only commits tai muu paikallinen tila eivät vaadi talteenottoa ensin
- repositoryssa on selkeä helper tai turvallinen poistopolku

Jos repositoryssa on worktree-helper poistoon, sitä kannattaa suosia. Worktree cleanup voi samalla pysäyttää prosesseja tai siivota worktree-kohtaisia paikallisia resursseja.

### Mitä muuta on yleensä tarpeellista

Closeoutissa hyödyllisiä lisätarkistuksia ovat usein:
- onko PR-draft tai muu julkaisuteksti jo olemassa ja linjassa lopputuloksen kanssa
- onko verify- tai review-vaiheen olennaiset havainnot vielä näkyviin kirjattuina
- onko alkuperäinen spec edelleen ristiriidassa toteutuksen kanssa jossain rajatapauksessa
- pitääkö worktree jättää auki siksi, että PR:n jälkeen odotetaan vielä nopeita lisäkorjauksia

## Ristiviitteet

- Käytä `harja-planning-and-spec-reference.md`, kun closeoutissa päivitettävä totuuslähde on `plans/`-rakenteen suunnitelma tai spec, tai siirtymävaiheen legacy-dokumentti.
- Käytä `harja-worktree-reference.md`, kun closeoutiin kuuluu worktreen turvallinen sulkeminen.

## Käyttöohje agentille

- viittaa tähän dokumenttiin, kun tehtävä koskee flown lopetusta, dokumentaation synkronointia tai closeout-checklistaa
- poimi raporttiin vain toteutuneeseen muutokseen liittyvät loppupään tarkistukset
- pidä closeout eksplisiittisenä vaiheena, ei hiljaisena sivuvaikutuksena

## Rajaus

Tämä dokumentti ei määritä:
- PR:n lopullista julkaisuhetkea
- merge-prosessia
- review- tai verify-vaiheen omaa output-sopimusta

Ne kuuluvat edelleen review-, verify- ja mahdollisen release- tai PR-käytännön vastuulle.
