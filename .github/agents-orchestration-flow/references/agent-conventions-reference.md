# Agenttien Yhteiset Käytännöt

## Tavoite

Tämä tiedosto on ei-subagent-agenttien yhteinen viitelähde.

Se kokoaa yhteen:
- rakenteen ja normalisointisäännöt
- nimeämisen ja taksonomian
- direct- ja delegated-käytön yhteisen sopimuksen

Tätä tiedostoa käytetään, kun agentin promptia normalisoidaan, nimetään tai lukitaan toimimaan sekä suoraan käyttäjän ajamana että orkestroijan delegoimana.

## Rajaus

Tämä koskee vain ei-subagent-agentteja tässä workspace:ssa.

Ei koske:
- Atlas-perheen erityistapauksia
- `*-subagent.agent.md` tiedostoja
- workspace:n ulkopuolisia yleisiä skills-tiedostoja

## Kieli ja merkistö

Kirjoita agenttien ohjeet, kuvaukset ja muu tavallinen teksti normaalilla suomen kielellä, mukaan lukien ääkköset.

ASCII-oletus koskee vain koodia, tunnisteita, polkuja, komentoja, esimerkkisyntaksia ja muuta teknistä sisältöä, jossa ASCII on tarkoituksenmukainen tai vaadittu.

Älä translitteroi suomenkielistä tekstiä muotoon `Tama`, `kayta` tai `yhteiset kaytannot`, ellei kyse ole koodista tai teknisestä syntaksista.

## Perusperiaate

Pidetään vastuunjako vakaana:
- agentti = käyttäytyminen, workflow, päätössäännöt ja output
- `domain/` = workspace-kohtainen yhteinen domain-tieto
- instruction = tiedostotyyppiin sidotut säännöt
- skill = laajempi uudelleenkäytettävä workflow tai virallinen ulkoinen referenssi

Agentti ei kanna laajoja domain-manuaaleja, jos sama tieto voidaan osoittaa yhdellä viitteellä.

## Yhteinen Frontmatter

Ei-subagent-agentin frontmatterissa käytetään vain aidosti tarpeellisia kenttiä tässä järjestyksessä:

```yaml
---
name: example-agent
description: Short clear description
tools: ['search', 'read', 'edit']
agents: ['other-agent-if-needed']
handoffs:
  - label: Example handoff
    agent: target-agent
    prompt: Example prompt
model: Optional model override
argument-hint: Optional invocation hint
---
```

Säännöt:
- älä jätä tyhjiä placeholder-kenttiä
- `name` on lowercase kebab-case ja voi alkaa järjestysprefiksillä kuten `00-` tai `10-`
- tiedostonimen ja `name`-kentän tulee vastata toisiaan
- `description` kuvaa agentin ydintehtävän yhdellä rivillä
- suosi yhtenäistä mallia `<Category> agent for <primary responsibility>` silloin kun se sopii agenttiin luontevasti
- `tools` listaa vain aidosti tarvittavat työnkulut
- `agents` ja `handoffs` pidetään minimissä

## Yhteinen Osiojärjestys

Kaikki ei-subagent-agentit noudattavat samaa runkoa:

1. `Role`
2. `Scope`
3. `Workflow`
4. `Decision Rules`
5. `Output Contract`
6. `References`

Lisäsäännöt:
- `References` on aina viimeinen osio
- otsikoiden nimet pidetään samoina agenttiperheestä riippumatta
- perhekohtainen ero syntyy sisällöstä ja painotuksesta, ei rakenteesta

## Osioiden Vähimmäissisältö

### Role
- mitä agentti tekee
- mitä agentti ei tee
- mikä on ensisijainen vastuu

### Scope
- `In Scope`
- `Out Of Scope`
- `Escalate When` tai vastaava rajausosio

### Workflow
- selkeät vaiheet
- jokaisessa vaiheessa tavoite
- sallitut toiminnot
- continue/stop-sääntö

### Decision Rules
- `Always`
- `Ask First`
- `Never`

### Output Contract
- yksi eksplisiittinen loppurakenne
- saman perheen agenteilla mahdollisimman yhtenäinen muoto
- suosi tiivistä header-kenttien ja laajempien sisältölohkojen yhdistelmää yhden `###`-otsikon per scalar-kenttä sijaan

### References
- vain lyhyet viitteet yhteisiin lähteisiin
- ei pitkää domain-sisällön duplikaattia

## Taksonomia Ja Nimeäminen

Ei-subagent-agentit jaetaan neljään perheeseen:

### Flow
- omistaa prosessin vaiheen
- tyypillinen nimi: `10-15-flow-*`
- esimerkit: `10-flow-plan`, `11-flow-implement`, `12-flow-review`, `13-flow-simplify`, `14-flow-verify`, `15-flow-closeout`

### Review
- review, review-analyysi tai review-palautteen toteutus
- tyypillinen nimi: `review-*`
- esimerkit: `review-validation`, `review-style`, `review-fix`, `review-pre-pr`, `review-explain`

### Orchestrate
- koordinoi rajattua muutosta tai koko delivery-polun kulkua
- tyypillinen nimi: `00-09-orchestrate-*`
- esimerkit: `00-orchestrate-delivery`, `01-orchestrate-small-change`

### Support
- tarjoaa erityisosaamista ilman oman päävirran omistusta
- tyypillinen nimi: `support-*`
- esimerkit: `support-explore`, `support-research`, `support-test`, `support-root-cause`, `support-explain`, `support-ux`, `support-pr-merge-history`, `support-refactor-impact`, `support-sql-explain`, `support-worktree`, `support-issue-spec`, `support-agent-format`

Nimeämissäännöt:
- käytä muotoa `NN-family-purpose` tai `family-purpose`
- suosi tehtävää kuvaavaa sanaa, ei metaforaa
- yksi nimi kertoo yhden ensisijaisen roolin
- säilytä saman perheen nimet rinnakkaisina

## Direct Ja Delegated Käytön Sopimus

Agentti toimii samalla identiteetillä riippumatta siitä, ajaako sitä käyttäjä vai orkestroija.

Sama pysyy:
- agentti-id
- vastuu
- input-rakenne
- output-rakenne
- päätössäännöt

Vaihtua saa vain kutsun konteksti:
- `direct`
- `delegated`

Context mode ei saa muuttaa agentin ydintehtävää.

## Yhteinen Input-Sopimus

Kun agenttia kuvataan tai delegoidaan, inputissa tulee olla tunnistettavissa:
- objective
- scope
- constraints
- success criteria
- context mode tarvittaessa

## Yhteinen Output-Sopimus

Kaikilla direct- ja delegated-käyttöön sovitetuilla agenteilla tulee olla tunnistettava loppurakenne:
- `status`
- `summary`
- agentin ydindeliverable
- `next step`
- avoimet kysymykset tarvittaessa

Suositellut ylatasoiset statukset:
- `completed`
- `in_progress`
- `needs_input`
- `blocked`
- `failed`

## Outputin Visualisointi

Luettavuuden vuoksi suosi seuraavaa mallia:
- näytä lyhyet scalar-kentät tiiviinä header-kenttinä ennen pidempiä lohkoja
- käytä omia lohko-otsikoita vain kentille, jotka sisältävät listan, perustelun tai useamman rivin sisältöä
- pidä kenttien nimet vakaina, vaikka visualisointitapa kevenee

Hyviä header-kenttiä ovat usein:
- `Status`
- `Active Subphase`
- `Review Recommended`
- `Next Step`

Hyviä sisältölohkoja ovat usein:
- `Summary`
- `Files Changed`
- `Behavior Changed`
- `Validation Run`
- `Remaining Risk`
- `Findings`
- `Recommendations`

Suositeltu renderointimalli:

```md
Status: completed
Active Subphase: Validate
Next Step: run 12-flow-review

Summary:
Short explanation of what changed and why.

Validation Run:
- targeted test: passed
- nearby suite: passed
```

Vältä oletuksena:
- emoji-pohjaista rakennetta
- koko outputin viemistä markdown-taulukkoon

Taulukko sopii vain pieneen metadata-headeriin, jos agenttiperheestä saadaan sillä aidosti luettavampi.

## Lukitut Agenttiperhekohtaiset Outputit

### support-explore
- relevant files
- why each file matters
- likely next files or next agent

### support-research
- relevant files
- key functions or structures
- conventions or patterns
- implementation options
- open questions

### 10-flow-plan
- overview
- requirements
- implementation phases
- testing
- risks
- open questions

### 11-flow-implement
- files changed
- behavior changed
- validation run
- remaining risk

### 12-flow-review
- review status
- summary
- findings
- recommendations
- next step

### 13-flow-simplify
- summary
- simplifications
- validation
- remaining risk
- next step

### 14-flow-verify
- verification target
- evidence checked
- browser-error check
- result
- blockers
- next step

UI-verifyssa raportoi aina eksplisiittisesti, tehtiinkö selaimen console- ja page error -tarkistus vai mikä esti sen.

### 15-flow-closeout
- closeout target
- source of truth updated
- documentation updated
- local cleanup
- open follow-ups

### 00-orchestrate-delivery
- current phase
- active delegation
- delegations
- phase results
- delivery state
- next step

### 01-orchestrate-small-change
- summary
- files changed
- validation
- flow review outcome
- remaining risk
- next step

### review-pre-pr
- change summary
- critical issues
- important issues
- testing recommendations
- PR readiness
- PR description draft
- next step

### review-fix
- review points implemented
- files changed
- validation run
- unresolved point or blocker
- next step

## Perhekohtaiset Painotukset

### Flow-agentit
- sama käyttäytyminen direct + delegated -tilassa
- output kelpaa seuraavalle vaiheelle
- ei Atlas-tyyppistä parent-seremoniaa

### Review-agentit
- löydöt ensin
- evidenssi vaaditaan
- severity pidetään vakaana

### Orchestrate-agentit
- aktiivinen mutta rajattu koordinointi sallittu
- `00-orchestrate-delivery` ei toteuta muutosta itse
- `01-orchestrate-small-change` saa toteuttaa vain pienen rajatun muutoksen

### Support-agentit
- erityisosaaminen ilman oman päävirran omistusta
- read-only oletus, ellei agentin luonne muuta vaadi

## Refaktorointisäännöt

Kun normalisoit agenttia:
1. vakioi frontmatter
2. vakioi kuuden osion runko
3. siirrä rajaukset Scope-osioon
4. siirrä pakottavat säännöt Decision Rules -osioon
5. lukitse yksi selkeä Output Contract
6. korvaa domain-duplikaatti viitteellä

Poista agentista:
- pitkät domain-checklistit
- laajat esimerkkikokoelmat
- tiedostotyyppikohtaiset formatting-ohjeet, jos niille on jo instruction
- sama konventioteksti useassa promptissa

Jätä agenttiin:
- rooli
- workflow
- päätössäännöt
- output contract
- lyhyet viittaukset tarpeellisiin lähteisiin

## Milloin Eskaloida

Kysy tai eskaloi, jos:
- refaktorointi vaatisi agentin uudelleennimeämisen
- agentin perhe ei ole selvästi pääteltävissä
- tiedosto sekoittaa rakenteen, vastuun ja laajan domain-manuaalin niin pahasti, ettei pieni normalisointi riitä

Jos työ menee yksinkertaista rakennenormalisointia laajemmaksi agent-customization-tason muutokseksi, käytä siihen erikseen agent-customization-skillia.
