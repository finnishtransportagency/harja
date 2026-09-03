# PRD: Ylläpitourakoiden työpöytä (MVP)

## 1. Yleiskuvaus
Ylläpitourakoiden työpöytä on Harjaan toteutettava uusi etusivunäkymä, joka kokoaa urakanvalvojalle yhdellä silmäyksellä tärkeimmän tilannekuvan hänen vastuullaan olevista ylläpitourakoista.

Tavoitteena on vähentää tarvetta siirtyä Harjan eri osioiden välillä, helpottaa kiireellisten tai puutteellisten asioiden tunnistamista ja parantaa urakkatiedon ajantasaisuutta.

MVP-vaiheessa työpöytä toimii ensisijaisesti tilannekuvanäkymänä ja navigointipisteenä: käyttäjä näkee keskeiset puutteet, määräajat ja statukset, ja voi siirtyä niiden perusteella Harjan tarkempiin alaosioihin tekemään varsinaiset toimenpiteet.

## 2. Ongelma
Nykytilassa ylläpitourakoiden kokonaistilanteen seuraaminen Harjassa on hankalaa. Urakkaan liittyvät tehtävät, ilmoitukset, määräajat ja puutteet ovat hajautuneet eri näkymiin. Vaikka tieto on saatavilla, käyttäjän täytyy itse muistaa tarkistaa useita eri osioita ja muodostaa kokonaiskuva niistä manuaalisesti.

Tämä aiheuttaa seuraavia ongelmia:
- urakan kokonaiskuvan saaminen on hidasta
- useita urakoita hoitavan urakanvalvojan on vaikea nähdä nopeasti, missä urakassa on tärkeimmät haasteet
- puutteelliset tai vanhentuneet tiedot voivat jäädä huomaamatta
- järjestelmän tiedon laatu kärsii, kun puuttuvia tietoja ei tunnisteta ajoissa

## 3. Tavoite
Näyttää ylläpitourakoiden tilannekuva yhdellä silmäyksellä urakanvalvojalle.

## 4. Kohdekäyttäjät

### Ensisijainen käyttäjä
- Urakanvalvoja

### Toissijainen käyttäjä
- Urakoitsija

## 5. Käyttäjätarve
Urakanvalvojana haluan nähdä yhdestä näkymästä vastuullani olevien ylläpitourakoiden keskeiset keskeneräiset asiat, määräajat, puutteet ja muutokset, jotta osaan puuttua oikeisiin asioihin oikeaan aikaan.

Urakoitsijana haluan nähdä oman urakkani puuttuvat tai keskeneräiset tiedot yhdestä näkymästä, jotta voin täydentää ne ajoissa ilman erillistä huomautusta.

## 6. MVP-laajuus

### Sisältyy MVP:hen
MVP sisältää työpöytänäkymän, jossa esitetään ainakin seuraavat kokonaisuudet:

#### 6.1 Kalenteri / määräaikamuistuttaja
Näyttää tulevat ja myöhässä olevat määräajat. Kalenteri on keskeinen osa tilannekuvaa ja näyttää vain asiat, jotka vaativat huomiota. Tehtyjä tai kunnossa olevia asioita ei näytetä.

Esimerkkitapahtumia:
- yleiset tiedot täytettävä määräpäivään mennessä
- muistutus ohjeisiin tutustumisesta hoitokauden alussa
- muistutus paikkauskohteiden lisäämisestä kauden alussa
- muistutus paikkauskohteiden toteumien ilmoittamisesta määräpäivään mennessä
- muistutus kohteettomien paikkausten toteumien ilmoittamisesta määräpäivään mennessä
- muistutus sakkojen ja bonusten kirjaamisesta
- muistutus POT-lomakkeiden lähettämisestä YHA-järjestelmään
- muut aikataulutetut tapahtumat

#### 6.2 Paikkauskohteiden tilanne
Näyttää paikkauskohteisiin liittyvän tilanteen ja puutteet.

Sisältää ainakin:
- käsittelemättömät kohteiden tilaukset
- paikkauskohteiden raportoinnin tila: aloittamatta / kesken / valmis
- paikkauskohteiden raportoinnin puutteet:
  - toteumat puuttuvat
  - POT-lomake puuttuu
  - kohdetta ei ole merkitty valmiiksi
  - toteutunut hinta puuttuu
  - tiemerkintätyötä ei ole kuitattu valmiiksi
- kohteettomien paikkausten raportoinnin puutteet:
  - toteumat puuttuvat
  - toteutunut hinta puuttuu
- kustannustiedon kattavuus, esim. kuinka monelle suunnitellulle kohteelle on merkitty toteutunut hinta

#### 6.3 POT-lomakkeiden tilanne
Näyttää POT-lomakkeiden käsittelyn tilan. Näyttää sekä päällystys- että paikkauskohteiden POT-lomakkeet.

Sisältää ainakin:
- aloittamatta
- kesken
- käsittelemättä
- lähettämättä YHA:an
- virheelliset

#### 6.4 Viimeisimmät muutokset
Listaus siitä, mitä olennaisia urakan tietoja on muokattu ja milloin ja kuka on muuttanut. 

#### 6.5 Graafi / tilannevisualisointi
Esimerkiksi urakkakohtainen palkkigraafi toteutuneista euromääristä. Tarkoitus on tukea nopeaa havaintoa siitä, puuttuuko jostakin urakasta kustannuskirjauksia tai muuta olennaista tilannetietoa.

#### 6.6 Ohjeet
Listaus tai linkit käyttöohjeisiin.

#### 6.7 Ajankohtaista
Sisältää esimerkiksi:
- uutiset
- klinikat
- Harjan päivitykset

#### 6.8 Suodatus
Käyttäjä voi rajata näkymän esimerkiksi yhteen valittuun urakkaan.

#### 6.9 Porautuminen
Käyttäjä voi siirtyä työpöydältä tarkempaan näkymään Harjan alaosioon.

### Ei sisälly MVP:hen
- varsinaisten muokkaus- tai hyväksyntätoimintojen tekeminen suoraan työpöydällä
- laaja tehtävien hallinta työpöydällä
- kaikkien mahdollisten Harjan tietosisältöjen täydellinen koonti ensimmäisessä versiossa
- täysin automatisoitu kaikkien deadlinejen hallinta, jos niiden tietoa ei vielä ole järjestelmässä

## 7. Keskeiset käyttäjätarinat
1. Urakanvalvojana haluan nähdä kaikki vastuullani olevat ylläpitourakat samassa näkymässä, jotta voin nopeasti tunnistaa missä urakassa on eniten toimenpiteitä vaativia asioita.
2. Urakanvalvojana haluan nähdä määräaikaan liittyvät keskeneräiset asiat kalenterissa, jotta huomaan myöhässä olevat ja lähestyvät deadlinet ajoissa.
3. Urakanvalvojana haluan nähdä, missä paikkauskohteissa on puutteita, jotta voin siirtyä tarkempaan näkymään ja selvittää asian.
4. Urakanvalvojana haluan nähdä, mitkä POT-lomakkeet ovat kesken, lähettämättä tai virheellisiä, jotta voin puuttua niihin ennen määräaikoja.
5. Urakanvalvojana haluan nähdä viimeisimmät olennaiset muutokset urakan tiedoissa, jotta pysyn ajan tasalla ilman että tarkistan useita eri näkymiä.
6. Urakanvalvojana haluan suodattaa näkymän yhteen urakkaan, jotta voin tarkastella yksittäisen urakan tilannetta tarkemmin.
7. Urakoitsijana haluan nähdä oman urakkani puuttuvat tiedot työpöydällä, jotta voin täydentää ne ajoissa.

## 8. Toiminnalliset vaatimukset

### 8.1 Yleiset vaatimukset
- Järjestelmän tulee tarjota työpöytänäkymä ylläpitourakoille.
- Järjestelmän tulee näyttää urakanvalvojalle kaikki hänen omat urakkansa samassa näkymässä.
- Järjestelmän tulee näyttää urakoitsijalle vain hänen oman urakkansa tiedot.
- Järjestelmän tulee mahdollistaa näkymän suodattaminen yksittäiseen urakkaan.
- Järjestelmän tulee toimia ensisijaisesti tilannekuvan ja navigoinnin välineenä.

### 8.2 Kalenteri / määräaikamuistuttaja
- Järjestelmän tulee näyttää käyttäjälle määräaikaan sidotut keskeneräiset tehtävät.
- Järjestelmän tulee korostaa myöhässä olevat asiat punaisella.
- Järjestelmän tulee korostaa lähestyvät määräajat keltaisella.
- Järjestelmän ei tule näyttää tehtyjä tai kunnossa olevia asioita kalenterissa.
- Järjestelmän tulee tukea sekä nykyisestä datasta johdettavia että myöhemmin erikseen mallinnettavia deadlineja.

### 8.3 Tila- ja lukumääräkortit
- Järjestelmän tulee näyttää keskeisistä kokonaisuuksista lukumäärät.
- Järjestelmän tulee näyttää mahdollisuuksien mukaan myös suhteellinen osuus, esimerkiksi `5/220`.
- Paikkauskohteiden osalta järjestelmän tulee näyttää ainakin:
  - käsittelemättömät tilaukset
  - raportoinnin tila
  - puutteelliset kohteet
  - kustannustiedon kattavuus
- POT-lomakkeiden osalta järjestelmän tulee näyttää ainakin:
  - aloittamatta
  - kesken
  - käsittelemättä
  - lähettämättä YHA:an
  - virheelliset

### 8.4 Porautuminen ja navigointi
- Käyttäjän tulee voida siirtyä työpöydältä suoraan siihen Harjan alaosioon, jossa varsinainen toimenpide tehdään.
- Siirtymän tulee mahdollisuuksien mukaan kohdistua oikeaan urakkaan, kohteeseen tai lomakkeeseen.

### 8.5 Viimeisimmät muutokset
- Järjestelmän tulee näyttää listaus viimeisimmistä olennaisista muutoksista.
- Listauksen tulee sisältää ainakin tiedon siitä, mitä muutettiin ja milloin.

### 8.6 Graafit
- Järjestelmän tulee voida näyttää vähintään yksi urakoiden tiedot rinnakkain esittävä visualisointi, joka tukee tilannekuvan muodostamista.
- Ensimmäisen version visualisointi voi olla yksinkertainen palkkigraafi, jossa esitetään jokaiseen urakkaan kirjatut kustannukset erillisenä palkkeina.

### 8.7 Ohjeet ja ajankohtaista
- Järjestelmän tulee näyttää linkit ohjeisiin.
- Järjestelmän tulee näyttää ajankohtaiset tiedotteet, kuten uutiset, klinikat ja Harjan päivitykset.

## 9. UX-periaatteet
- Työpöydän tulee tarjota tilannekuva yhdellä silmäyksellä.
- Käyttäjän tulee tunnistaa tärkeimmät poikkeamat ilman, että hänen tarvitsee avata useita näkymiä.
- Poikkeamat, puutteet ja määräajat tulee esittää selkeästi visuaalisin keinoin, jotka täyttävät saavutettavuusvaatimukset WCAG AA-taso..
- Työpöydän ei tule kuormittaa käyttäjää liian suurella määrällä yksityiskohtia.
- Työpöydän päärooli on ohjata käyttäjä oikeaan paikkaan, ei korvata Harjan alaosioita.

## 10. Onnistumismittarit
MVP:n onnistumista voidaan arvioida esimerkiksi seuraavilla mittareilla:

- puutteellisten raportointitietojen määrä vähenee
- paikkauskohteisiin liittyvät keskeneräiset tiedot jäävät harvemmin täyttämättä
- POT-lomakkeiden käsittely ja lähetys tapahtuu useammin määräaikaan mennessä
- urakanvalvojat tunnistavat puutteet aiemmin
- urakoitsijat täydentävät puuttuvia tietoja aiemmin ilman erillistä huomautusta
- käyttäjät saavat kokonaiskuvan urakan tilanteesta nopeammin kuin nykytilassa

## 11. Riskit, rajoitteet ja riippuvuudet

### Riskit
- Työpöytä on riippuvainen useista Harjan eri osioista ja niiden datan laadusta.
- Kaikkia deadlineja ei voida johtaa suoraan nykyisestä järjestelmädatasta.
- Graafien tai laajan koontinäkymän suorituskykyvaikutuksia ei vielä tunneta.

### Rajoitteet
- Ohjeet-osion linkit käyttöohjeisiin joudutaan todennäköisesti määrittelemään manuaalisesti.
- Osa kalenterin määräpäivistä vaatii erillistä mallinnusta.
- MVP:ssä työpöytä toimii katselu- ja navigointinäkymänä, ei varsinaisena työskentelynäkymänä.

### Riippuvuudet
- tiedon saatavuus Harjan eri alaosioista
- käyttöoikeuslogiikka roolikohtaisesti
- mahdollinen integraatio tai sääntölogiikka deadlinejen muodostamiseen
- mahdollinen YHA-prosessiin liittyvä tiedon saatavuus POT-lomakkeiden osalta

## 12. Avoimet kysymykset
- Mitkä kaikki deadline-tyypit voidaan muodostaa automaattisesti nykyisestä datasta?
- Mitkä työpöydän kortit tai komponentit toteutetaan ensimmäisessä MVP-julkaisussa ja mitkä myöhemmin?
- Millä tarkkuudella viimeisimmät muutokset voidaan näyttää teknisesti?
- Mitkä visualisoinnit tuottavat eniten arvoa ensimmäisessä versiossa?
- Tarvitaanko käyttäjäkohtaisia asetuksia tai personointia myöhemmissä vaiheissa?

## 13. MVP-yhteenveto
MVP:n ydin on tarjota urakanvalvojalle yksi näkymä, josta hän näkee:
- mitä pitää tehdä pian tai heti
- missä urakassa on puutteita tai keskeneräisiä asioita
- mitkä tiedot eivät ole ajan tasalla
- mistä pääsee suoraan oikeaan Harjan alaosioon korjaamaan tilanteen
