# Ehdotus suunnitteludokumenttien rakenteeksi

## Tavoite

Korvata epäselvä `.prd`-hakemisto yhdellä selkeällä rakenteella, joka toimii sekä yhdelle aiheelle että useille saman aiheen alidokumenteille.

Ehdotus nojaa olemassa olevaan repositoryn juuritason `plans/`-hakemistoon, joten uusi malli sopii nykyiseen rakenteeseen ilman uutta ylätason kansiota.

## Ehdotettu rakenne

Oletushakemisto on `plans/`.

Perusmalli:

```text
plans/
  <topic-slug>/
    plan.md
    spec.md
    tasks/
      <task-slug>.md
```

Laajennettu malli Jira- tai issue-vetoisiin aiheisiin:

```text
plans/
  <topic-slug>/
    plan.md
    spec.md
    issues/
      <jira-id>.md
    tasks/
      <task-slug>.md
```

## Miksi tämä on parempi

- `plans/` kertoo tarkoituksen suoremmin kuin `.prd/`.
- Yhden aiheen kaikki dokumentit pysyvät samassa kansiossa.
- Rakenne toimii sekä yhdelle dokumentille että useille ala-aiheille ilman uusia nimeämiskikkoja.
- Agenttien ei tarvitse päätellä, tallennetaanko tiedosto `.prd/`- vai `specs/`-hakemistoon.
- Closeout ja myöhempi ylläpito helpottuvat, koska yksi aihe löytyy yhdestä paikasta.

## Nimeämiskäytäntö

- aihehakemiston `topic-slug` johdetaan ensisijaisesti nykyisen feature branchin nimestä
- branch normalisoidaan slugiksi näin: pienet kirjaimet, `/` ja `_` -> `-`, peräkkäiset `-` tiivistetään, ja alusta poistetaan ticket-prefiksi muodossa `<kirjaimet>-<numerot>-`, jos loppuosa jää kuvaavaksi
- jos feature branchia ei ole, branch on geneerinen, tai branch ei vastaa tehtävää, slug kysytään käyttäjältä ennen tallennusta
- aihehakemisto: `plans/<topic-slug>/`
- päätason toteutussuunnitelma: `plan.md`
- päätason spesifikaatio: `spec.md`
- tarkentavat toteutustehtävät: `tasks/<task-slug>.md`
- Jira- tai issue-lähtöiset tarkenteet tarvittaessa: `issues/<jira-id>.md`

## Miten tämä korvaa nykyiset mallit

- nykyinen `.prd/[feature-name]-prd.md` korvautuu muodolla `plans/<topic-slug>/plan.md`
- nykyinen `.prd/[feature]/[component]-spec.md` korvautuu muodolla `plans/<topic-slug>/tasks/<component>.md` tai `plans/<topic-slug>/spec.md` riippuen siitä, onko kyse päätason specistä vai tarkenteesta
- nykyinen `specs/spec-<jiraid>.md` voidaan siirtää muotoon `plans/<topic-slug>/spec.md` tai `plans/<topic-slug>/issues/<jira-id>.md`, jos issue halutaan sitoa näkyvästi tiettyyn aiheeseen

## Vaikutus agenteihin

Päivitettävät tiedostot vähintään:

- `10-flow-plan.agent.md`
- `support-issue-spec.agent.md`
- `15-flow-closeout.agent.md`
- `domain/harja-planning-and-spec-reference.md`
- `domain/harja-issue-spec-reference.md`
- `domain/harja-delivery-closeout-reference.md`
- mahdolliset README- tai setup-ohjeet, joissa viitataan `.prd`-hakemistoon

## Suositeltu siirtymämalli

1. Ota `plans/` käyttöön uutena oletusrakenteena agenteissa.
2. Salli siirtymävaiheessa vanhat `.prd`- ja `specs/`-polut vain olemassa oleville tiedostoille.
3. Tee kaikki uudet suunnitelmat ja specit vain `plans/`-rakenteeseen.
4. Siirrä vanhat dokumentit vähitellen aihehakemistoihin, kun niitä seuraavan kerran päivitetään.

## Suositus

Suositeltu oletusmalli on:

```text
plans/
  <topic-slug>/
    plan.md
    spec.md
    tasks/
      <task-slug>.md
```

Tämä on selkein kompromissi luettavuuden, skaalautuvuuden ja agenttien yksinkertaisen päätöslogiikan välillä.