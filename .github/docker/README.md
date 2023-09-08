# Harjan Workflowien käyttämät Docker-imaget

GitHub Actions workflowit käyttävät seuraavia Docker-kontteja:
1. Testitietokanta (harjadb): [docker/tietokanta/Dockerfile](./tietokanta/Dockerfile)
    * Tarvitaan Harjan buildaamisessa (Specql-kirjasto tarvitsee kantayhteyden) ja testien ajamisessa
    * Tämä on modernimpi versio kehittäjien käytössä olevasta Dockerhubiin julkaistusta harjadb-imagesta.
        *  **TODO**: Otetaan tämä käyttöön myös paikallisessa kehityksessä. Katso [tietokanta/devdeb_up_uusi.sh](../../tietokanta/devdb_up_uusi.sh)
2. ActiveMQ: [.github/docker/activemq/Dockerfile](./activemq/Dockerfile)
    * Tarvitaan Integraatiotestien ajamisessa
3. Cypress: [.github/docker/cypress/Dockerfile](./cypress/Dockerfile)
    * Tarvitaan E2E-testien ajamisesssa

GitHub Actionsissa on konsepti nimeltä ["Service containers"](https://docs.github.com/en/actions/using-containerized-services/about-service-containers),
joka mahdollistaisi konttien ajon melko vaivattomasti jobin sisällä.  
Service contaireita olisi muutoin näppärä käyttää, mutta kirjoittamisen hetkellä (4.9.2023) niiden haittapuolet voittavat
valitettavasti hyödyt:
1. Service containeriin ei voi mountata hakemistoa Actions checkoutin jälkeen, vaan se käynnistyy ennen kuin checkout tapahtuu.
2. Service containerien asetuksia ei voi helposti uudelleenkäyttää kuten composite actioneita, vaan ne joutuu joka kerta
   kopioimaan jobeihin missä niitä haluaa käyttää. Tämä hankaloittaa jobien ylläpitoa.
3. Kaikkia containereita ei voi tai ei kannata ajaa serviceinä.

Näiden haittapuolien ja ylläpidon vaikeuden takia, kaikki tarvittavat Docker kontit ajetaan suoraan Docker run komennoilla.  
Jokaisen Docker-kontin käynnistämiseen on luotu uudelleenkäytettävät composite action määritykset: [.github/actions/](../actions/).  
Joillekin konteille on luotu myös konfiguraatiot ja verkkoasetukset [docker-compose.yml](./docker-compose.yml) -tiedostoon,
jotta konttien hallinta olisi mahdollisimman yksinkertaista ja asetuksia voisi helpommin ylläpitää.

Konttien käyttöön kannattaa tutustua etsimällä relevantit composite actionit ```.github/actions``` hakemistosta ja tutkia miten
niitä on käytetty workfloweissa.


# GitHub Container Registry

## Kehittäjän kirjautuminen Container Registryyn

GitHub Packages (eli käytännössä Container Registry) tukee kirjautumista ainoastaan classic personal access tokenilla.  
Lue alla olevat ohjeet tarkoin. Kehittäjä tarvitsee read/write/delete oikeudet Packages scopeen.  
Lue tarkasti varsinkin ohje, jossa neuvotaan pienentämään tokenin access scopea, jotta tokenilla ei ole liian laajat oikeudet.
>Note: By default, when you select the write:packages scope for your personal access token (classic) in the user interface, the repo scope will also be selected. 
>The repo scope offers unnecessary and broad access, which we recommend you avoid using for GitHub Actions workflows in particular. 
>For more information, see "Security hardening for GitHub Actions." 
>As a workaround, you can select just the write:packages scope for your personal access token (classic) in the user interface with this url: https://github.com/settings/tokens/new?scopes=write:packages.

**Ohjeet:**  
https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry#authenticating-with-a-personal-access-token-classic


# Docker imageiden julkaisu/päivitys

Harjan käyttämät Docker imaget julkaistaan Harjan GitHub repositorion Container Registryyn, finnishtransportagency-organisaation alle.
Container Registryyn julkaisun etuna on se, että imaget ovat mahdollisimman lähellä Actionsin workfloweja, jolloin ne latautuvat nopeasti.
Myöskään ei ole tarvetta luoda erillistä organisaatiota tai tunnuksia Dockerhubiin.

GitHubin container registry toimii siten, että repositorion imaget julkaistaan aina organisaatiokohtaisesti ja imagen Dockerfilessa
olevan Label-määrityksen avulla image paritetaan oikean repositorion kanssa.


## Uuden docker imagen julkaisu
Otetaan esimerkiksi Harjan tietokanta-imagen julkaisu.  
Alla olevat seikat on syytä huomioida, jos on tarvetta toteuttaa uusi image.

1. Dockerfilessa on LABEL:
   ```dockerfile
      FROM postgis/postgis:12-3.1
    
      # Yhdistä image harjan repositoryyn
      LABEL org.opencontainers.image.source https://github.com/finnishtransportagency/harja
      ...
   ```
   Tämä kertoo, että kyseinen image täytyy yhdistää Harjan GitHub-repositorioon julkaisun yhteydessä.
2. Buildataan image tagilla: ```docker build -t ghcr.io/finnishtransportagency/harja_harjadb:latest .```
    * Tässä on haluttu korostaa, että kyseessä on Harja-projektin image, laittamalla imagen nimen alkuun "harja_"
3. Julkaistaan image pushaamalla se normaalisti githubin container registryyn
4. Julkaistut imaget löytyvät täältä: https://github.com/orgs/finnishtransportagency/packages?repo_name=harja
    * Huomioi, että imaget ovat aina oletuksena privaatteja. Toistaiseksi ei ole tarvetta julkaista julkisia imageja.
5. Docker containerin ajoon kannattaa tehdä uudelleenkäytettävä composite action: [.github/actions](../actions)
    * Ota mallia olemassaolevista composite actioneista
6. Jos konttien täytyy viestiä keskenään tai on tarvetta monimutkaisemmille verkkoasetuksille tms,  
   kannattaa harkita asetuksien säätämistä uudelle kontille [.github/docker-compose.yml](./docker-compose.yml) tiedostoon.

## Imageiden päivitys

Imageiden Dockerfilet löytyvät polusta: ```.github/docker```.  
Yleisesti, jokaiselle Dockerfilelle on määritelty "build-image.sh" ja "push-image.sh" scriptit, jotka ajamalla
imaget voi helposti päivittää.

### Apuscriptit
Jokaisen Dockerfile kansion sisällä on ```build-image.sh``` ja ```push-image.sh``` apuscriptit.

1. **Build image**

    Esim.
    ```bash
    ./build-image.sh --tag 13-3.1
    ```
    
    Tämä tekee uudet "latest" ja "13-3.1" tagatut imaget.
    
    Katso muut optiot komennolle ajamalla:
    ```bash
    ./build-image.sh --help
    ```

     **Lisäoptiot docker buildille**  
     Build-image.sh tukee lisäoptioiden syöttöä ```docker build``` komennolle.
     Katso esimerkkiä ```.github/docker/cypress/build-image.sh``` tiedostosta.

     Lisäoptioita voi syöttää antamalla argumetiksi "--".  
     Kaikki "--" jälkeen tulevat optiot annetaan sellaisenaan 'docker build' komennolle build-image.sh scriptin sisällä.
    ```bash
    ./build-image.sh --tag 10.2.0 -- --build-arg="NPM_CYPRESS_VERSION=${NPM_CYPRESS_VERSION}"
    ```

2. **Push image**

    Käytetään komentoa:
    
    ```bash
    ./push-image.sh --tag 13-3.1
    ```
    
    Defaulttina ei päivitetä "latest"-tagia Container Registryyn.
    Jos haluat myös päivittää latest tagin, lisää optio ```--update-latest```
    
    Katso muut optiot ajamalla:
    ```bash
    ./push-image.sh --help
    ```

### Cypress-imagen päivitys
1. Navigoi polkuun: ```.github/docker/cypress/```
2. Tutustu scriptiin: build-image.sh
3. Aja scripti: ```./build-image.sh```
4. Kirjaudu sisään Github Container Registryyn classic tokenilla (Lue: "Kehittäjän kirjautuminen Container Registryyn" yllä)
5. Aja scripti: ```./push-image.sh --update-latest```


### ActiveMQ-imagen päivitys
1. Navigoi polkuun: ```.github/docker/activemq/```
2. Tutustu scriptiin: build-image.sh
3. Aja scripti: ```./build-image.sh```
4. Kirjaudu sisään Github Container Registryyn classic tokenilla (Lue: "Kehittäjän kirjautuminen Container Registryyn" yllä)
5. Aja scripti: ```./push-image.sh --update-latest```

### Tietokanta-imagen päivitys
1. Navigoi polkuun: ```.github/docker/tietokanta/```
2. Tutustu scriptiin: build-image.sh
3. Aja scripti: ```./build-image.sh --tag xx-y.y``` 
   * Harkitse minkä tagin luot/päivität (<postgresql-versio>-<postgis-versio>)
4. Kirjaudu sisään Github Container Registryyn classic tokenilla (Lue: "Kehittäjän kirjautuminen Container Registryyn" yllä)
5. Aja scripti: ```./push-image.sh --tag xx-y.y```
   * Voit päivittää samanaikaisesti myös "latest" tagin, jos on tarve:
      * ```./push-image.sh --tag xx-y.y --update-latest```
