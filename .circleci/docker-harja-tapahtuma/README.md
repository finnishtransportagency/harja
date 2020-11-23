# Käyttö

Tämä sisältää Apachen httpd serverin, jota käytetään erinnäisten tapahtumien julkaisemiseen ja kuunteluun.
Jokainen julkaistu tapahtuma tallennetaan omaksi tiedostokseen, joka mahdollisesti sisältää jotain dataa.
Data on muodossa `NIMI=ARVO`.

Kun jotain tapahtumaa kysellään serveriltä, palauttaa se `404`, jos tapahtumaa ei ole luotu tai `200`, jos se on.

Kun jotain tapahtumaa ollaan luomassa, pitää argumenteiksi antaa URL:in mukana vähintään `tapahtuma`.
Jos halutaan tallentaa jotain dataa, niin ne voi antaa nimi-arvo pareina argsien mukana.

Jos menet konttiin ja asennat `curl`:in niin voit luoda tapahtuman esim näin:
 
`curl "localhost:80/luo-tapahtuma?tapahtuma=foo&arg1=bar&arg2=baz"`

# Konfiguraatio

Konfiguraatio on perus konffi, jota on muokkailtu muutamasta kohdasta. Konffi on luotu
 [Apachen Docker hub](https://hub.docker.com/_/httpd/) ohjeiden mukaan näin:

```
docker run --rm httpd:2.4 cat /usr/local/apache2/conf/httpd.conf > my-httpd.conf
```

#### Konffi muokkaukset

- Dokumentin rootiksi on määritetty `/usr/local/apache2/tapahtumat`
- `DocumentRoot`:in `Options` on `None`
- Määriteltiin `Location` `/luo-tapahtuma`, joka laukaisee `CGI`:n `luo-tapahtuma.sh`

# Skriptit

Osaa skripteistä käytetään tässä kontissa ja muut on helppereitä muille konteille.

### Tässä kontissa

#### luo-tapahtuma.sh

`luo-tapahtuma.sh` ajetaan aina kun polkuun `/luo-tapahtuma` tehdään kutsu ja se luo tahatuma tiedoston ja laittaa
sinne kutsun mukana tulleet argumentit.

Skripti palauttaa `422`, jos argumentit on oikeassa muodossa, mutta ne on sisällöltään väärin.

### Helpperi skriptit

#### julkaise-tapahtuma.sh

Tämän avulla voi julkaista tapahtuman `curl`:in avulla. Ottaa maksimissaan 4 argumenttia tässä järjestyksessä:
TAPAHTUMA, ARGS, PALVELIN, PORTTI. Näistä ainoastaan TAPAHTUMA on pakko antaa. ARGS pitää olla muodossa
`arg1=arvo1&arg2=arvo2&arg3=arvo3`...

#### lue-arvot-ymparistomuuttujiin.sh

Tämä skripti olettaa, että jonku kuunnellun tapahtuman palauttama vastaus on tallennettu tiedostoon `tapahtuman_arvot`.
Sisältö on muotoa `arg1=arvo1`, jotka laitetaan sitten ympäristömuuttujiin.

#### odota-tapahtumaa.sh

Tälle pitää antaa kolme argumenttia TAPAHTUMA, PALVELIN ja PORTTI.

Skripti odottelee annettua TAPAHTUMA:a 2 min. Jos tapahtumaa ei siinä ajassa ole tapahtunut,
lopettaa kuuntelun ja exit 1. Jos tapahtuma on käynyt tuossa ajassa, lukee sen vastauksen tiedostoon
`tapahtuman_arvot`