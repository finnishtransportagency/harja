# Harja Worktree Reference

## Tavoite

Tämä dokumentti kokoaa Harjan worktree-käytännön turvalliselle tasolle ilman, että agentti kovakoodaa helper-skriptien tarkkaa APIa.

## Milloin Käyttää

Käytä tätä referenssiä, kun tehtävä koskee Harja-worktreen luontia, poistamista tai helper-skriptien turvallista käyttöä.

Tyypilliset agentit:
- `support-worktree`

## Ydinohjeet

### Tyypillinen Sijainti Harjassa

Harjassa worktree-helperit sijaitsevat tyypillisesti polussa `sh/git-worktree/`.
Tarkista aina helperin usage, otsakekommentit tai muu paikallinen dokumentaatio ennen ajoa.

### Suositeltu Toimintamalli

1. Etsi ensin helper-skripti ja käytä sitä ensisijaisesti, jos `sh/git-worktree/` löytyy.
2. Tarkista branch, kohdepolku ja mahdolliset pakolliset parametrit ennen ajoa.
3. Jos helperin käyttö ei ole selvä, pysähdy ja raportoi usage mieluummin kuin arvaa.
4. Luota helperin tulosteeseen seuraavissa askelissa: worktree-polku, käynnistysskripti, portit ja muut ympäristöhuomiot.
5. Tee poisto vain eksplisiittisestä pyynnöstä ja suosi poisto-helperia, jos sellainen on olemassa.

Helper voi hoitaa myös muita asioita kuin pelkän `git worktree add` -komennon, kuten porttien valinnan, riippuvuuksien asennuksen tai käynnistysskriptin luonnin.

### Mitä Agentin Kannattaa Raportoida

Kun Harja-worktree on luotu, raportoi ainakin:
- luodun worktree-polun
- käytetyn helper-skriptin tai komennon
- mahdollisen käynnistysskriptin polun
- olennaiset portit tai resource-huomiot, jos helper ne tulostaa
- selkeän seuraavan komennon, jolla worktree voidaan käynnistää tai avata

Kun luonti estyy, raportoi ainakin:
- puuttuva helper tai epäselvä usage
- olemassa oleva worktree-polku
- puuttuva branch
- puuttuva prerequisite kuten git, npm tai docker, jos helper siihen nojaa

## Ristiviitteet

- Käytä `harja-delivery-closeout-reference.md`, kun worktree liittyy flown lopetukseen ja poistoon.

## Käyttöohje Agentille

- viittaa tähän dokumenttiin, kun tehtävä koskee worktree-helperien käyttöä eikä skriptien tarkkaa APIa pidä arvata
- tarkista aina helperin oma usage ennen ajoa
- raportoi helperin tulosteesta seuraavat askeleet eksplisiittisesti

## Rajaus

Tämä dokumentti ei lukitse:
- tarkkaa skriptin argumenttijärjestystä
- tarkkoja optioiden nimiä
- tarkkaa porttirangea tai tietokanta-konvention toteutusta

Nuo voivat muuttua, joten käytössä olevan repoversion skripti on aina tarkempi totuuslähde kuin tämä referenssi.
