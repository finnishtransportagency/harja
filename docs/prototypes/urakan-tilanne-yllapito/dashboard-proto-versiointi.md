# Dashboard-proton versiointi

Tämä tiedosto kertoo, mitä asiakkaalle näytettyyn protoon on muuttunut. Se ei korvaa Git-historiaa eikä käyttäjätarveasiakirjaa.

## Käytäntö

- Näytä nykyinen versio proton alatunnisteessa muodossa `v0.1`.
- Kasvata versionumeroa, kun asiakaskierroksen perusteella tehdään uusi näkyvä muutos.
- Pieni kirjoitusvirheen tai teknisen tarkistuksen korjaus ei yksin tarvitse uutta versiota.
- Kirjaa jokaisesta uudesta versiosta tavoite, näkyvä muutos ja tehty tarkistus.
- Pidä HTML-tiedoston alatunnisteen versionumero ja tämän lokin viimeisin versio samoina.

## Versiot

### v0.2 · 27.8.2026

- Tavoite: vertailla kolmea erilaista dashboard-rakennetta ennen high fidelity -toteutusta.
- Muutos: lisättiin `dashboard-vaihtoehdot.html`, jossa URL-parametrilla valittavat A-, B- ja C-näkymät sekä näppäimistöllä toimiva vaihtopalkki.
- Varmistus: staattinen DOM- ja JavaScript-smoke-tarkistus läpäisi; nykyinen `index.html` säilyi muuttumattomana.

### v0.1 · 24.8.2026

- Tavoite: muodostaa urakanvalvojalle ylläpitourakoiden tilannekuva.
- Muutos: lähtöversiossa ovat määräajat, puutteet, urakoiden vertailu, valitun urakan tiedot ja simuloitu porautuminen.
- Varmistus: dashboard-proton DOM- ja vuorovaikutustarkistus läpäisi.

## Seuraava versio

Lisää uusi versio tämän kohdan yläpuolelle ennen kuin asiakaskierroksen hyväksymä näkyvä muutos jää proton nykyiseksi lähtötilaksi.
