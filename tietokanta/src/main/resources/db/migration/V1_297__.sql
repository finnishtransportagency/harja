CREATE TABLE asiakaspalauteluokka (
  id        SERIAL PRIMARY KEY,
  nimi      VARCHAR(128),
  selitteet ilmoituksenselite []
);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Auraus ja sohjonpoisto',
        ARRAY ['auraustarve',
        'sohjonPoisto',
        'kevyenLiikenteenVaylillaOnLunta']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Liukkaudentorjunta',
        ARRAY ['hiekoitustarve',
        'kevyenLiikenteenVaylatOvatJaisiaJaLiukkaita',
        'kevyenLiikenteenVaylatOvatLiukkaita',
        'kevyenLiikenteenVaylillaOnLunta',
        'liukkaudentorjuntatarve',
        'raskasAjoneuvoJumissa',
        'tieOnLiukas',
        'tietOvatJaatymassa',
        'jaatavaaSadetta',
        'mustaaJaataTiella']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Polanteen tasaus',
        ARRAY ['hoylaystarve']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Muu talvihoito',
        ARRAY ['aurausvallitNakemaesteena']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito',
        ARRAY ['avattavatPuomit',
        'liikennemerkkeihinLiittyvaIlmoitus',
        'liikennevalotEivatToimi',
        'muuttuvatOpasteetEivatToimi',
        'tienvarsilaitteisiinLiittyvaIlmoitus']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Puhtaanapito ja kalusteiden hoito',
        ARRAY ['avattavatPuomit',
        'harjaustarve',
        'irtokiviaTiella',
        'lasiaTiella',
        'levahdysalueeseenLiittyvaIlmoitus',
        'liikennettaVaarantavaEsteTiella',
        'pysakkiinLiittyvaIlmoitus',
        'saveaTiella',
        'tiellaOnEste']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Viheralueiden hoito',
        ARRAY ['niittotarve',
        'nakemaalueenRaivaustarve',
        'vesakonraivaustarve']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Kuivatusjärjestelmän kaivojen, putkistojen ja pumppaamoiden hoito',
        ARRAY ['alikulkukaytavassaVetta',
        'kaivonKansiRikki',
        'rumpuunLiittyvaIlmoitus',
        'avattavatPuomit']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Rumpujen kunnossapito',
        ARRAY []
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapito',
        ARRAY ['hirviaitaVaurioitunut',
        'kaidevaurio']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Päällysteiden paikkaus',
        ARRAY ['kuoppa',
        'paallystevaurio',
        'routaheitto']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Päällystettyjen teiden sorapientareen kunnossapito',
        ARRAY []
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Siltojen ja laitureiden hoito',
        ARRAY ['siltaanLiittyvaIlmoitus']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Sorateiden hoito',
        ARRAY ['polynsidontatarve',
        'soratienKuntoHuono',
        'soratienTasaustarve']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Äkillinen hoitotyö',
        ARRAY ['virkaApupyynto',
        'vesiSyovyttanytTienReunaa',
        'sahkojohtoOnPudonnutTielle',
        'puitaOnKaatunutTielle',
        'tulvavesiOnNoussutTielle',
        'tieOnSortunut',
        'oljyaTiella',
        'kuormaOnLevinnytTielle',
        'myrskyvaurioita',
        'tieTaiTienReunaOnPainunut',
        'vettaTiella',
        'rikkoutunutAjoneuvoTiella',
        'raivausJaKorjaustoita']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Lupa-asiat',
        ARRAY ['korvauskysely']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Tiemerkinnät',
        ARRAY ['tiemerkintoihinLiittyvaIlmoitus']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Korjaus ja investointihankkeet (tietyöt)',
        ARRAY ['tyomaajarjestelyihinLiittyvaIlmoitus']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Valaistus',
        ARRAY ['tievalaistusVioittunut',
        'tievalaistusVioittunutOnnettomuudessa',
        'tievalaistuksenLamppujaPimeana']
        :: ilmoituksenselite []);

INSERT INTO asiakaspalauteluokka (nimi, selitteet)
VALUES ('Muu',
        ARRAY ['liikennekeskusKuitannutLoppuneeksi',
        'testilahetys',
        'muuToimenpidetarve',
        'toimenpidekysely'
        ]
        :: ilmoituksenselite []);