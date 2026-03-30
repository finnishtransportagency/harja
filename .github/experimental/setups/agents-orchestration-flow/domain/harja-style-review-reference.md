# Harja style review reference

## Tavoite

Tämä dokumentti kokoaa Harja-spesifin style-reviewn minimitason taustatiedon ilman, että agenttiin kopioidaan laajaa stylesheet-manuaalia.

## Milloin käyttää

Käytä tätä referenssiä, kun tarkastat LESS- tai CSS-tiedostoja Harjassa ja tarvitset projektin nykyiset tyyliodotukset, legacy-velan tulkinnan tai priorisointimallin.

Tyypilliset agentit:
- `review-style`
- `support-ux`, kun UX-ehdotus vaatii stylesheet-tason tarkistusta

## Ydinohjeet

### Konteksti ja totuuslähteet

Projektissa elää rinnakkain legacy-tyylejä, uudempaa design-kieltä ja välimuotoja. Review'n tarkoitus ei ole pakottaa täyttä uudelleenkirjoitusta, vaan tunnistaa:
- selvä legacy-velka
- aidot ristiriidat saman alueen sisällä
- korkean hyödyn modernisointikohteet

Arvioi löydöksiä erityisesti näitä vasten:
- `dev-resources/less/vayla/colors.less`
- `dev-resources/less/vayla/typography.less`
- `dev-resources/less/vayla/yleiset.less`

### Mitä tarkistaa

Tarkista ensisijaisesti:
- hardkoodatut pikselit spacing- tai typografiapäätöksissä
- inline-värit yhteisten muuttujien sijaan
- suora typografia yhteisten apureiden sijaan
- legacy-tyylit alueilla, joiden pitäisi seurata uudempaa mallia
- duplikaatit, ristiriidat ja override-kasat saman komponenttialueen sisällä

### Priorisointi

Korkea prioriteetti:
- legacy-tyylit uudella alueella
- useat ristiriitaiset tyylimallit samassa feature-kokonaisuudessa
- vaikeasti ylläpidettävät override-ketjut

Keskitaso:
- toistuvat hardkoodatut pikselit
- toistuvat inline-värit
- paikalliset typografiapoikkeamat

Matala prioriteetti:
- yksittäinen vanha arvo vakaassa legacy-alueessa
- poikkeama, jonka korjaus olisi kallis mutta hyöty pieni

### Hakuvihjeet ja raportointi

Hyödyllisiä hakukuvioita:
- hardkoodatut pikselit: `:\s*\d+px`
- inline-värit: `(#[0-9a-fA-F]{3,6}|rgb\(|rgba\()`
- suora typografia: `font-(family|size|weight):`
- legacy-luokat: `vaylatyylit|Vaylatyylit`

Hyvä raportti kertoo:
- mikä löydös on
- miksi se on ongelma juuri Harjan nykytilassa
- missä tiedostoissa ilmiötä esiintyy
- kannattaako korjata heti vai migroida suunnitellusti

## Ristiviitteet

- Käytä `harja-feature-implementation-reference.md`, kun style-löydös täytyy suhteuttaa featuren rakenteeseen tai muutosalueeseen.
- Käytä `harja-planning-and-spec-reference.md`, kun löydös pitäisi nostaa suunnitelmalliseksi migraatioksi eikä vain yksittäiseksi korjaukseksi.

## Käyttöohje agentille

- viittaa tähän dokumenttiin, kun tehtävä koskee Harjan LESS- tai CSS-reviewta
- poimi raporttiin vain olennaiset tyylivelan, ristiriitojen ja priorisoinnin havainnot
- pidä agenttiprompti findings- ja evidence-keskeisenä

## Rajaus

Tämä dokumentti ei korvaa yleistä UX-arviointia tai featuren toteutusrakennetta kuvaavia referenssejä.
