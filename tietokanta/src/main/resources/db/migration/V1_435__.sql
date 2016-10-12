ALTER TYPE ilmoituksenselite
RENAME TO ilmoituksenselite_temp;

CREATE TYPE ilmoituksenselite AS ENUM (
  'tyomaajarjestelyihinLiittyvaIlmoitus',
  'kuoppiaTiessa',
  'kelikysely',
  'soratienKuntoHuono',
  'saveaTiella',
  'liikennettaVaarantavaEsteTiella',
  'irtokiviaTiella',
  'kevyenLiikenteenVaylaanLiittyvaIlmoitus',
  'raivausJaKorjaustoita',
  'auraustarve',
  'yliauraus',
  'kaivonKansiRikki',
  'kevyenLiikenteenVaylatOvatLiukkaita',
  'routaheitto',
  'avattavatPuomit',
  'tievalaistusVioittunutOnnettomuudessa',
  'muuKyselyTaiNeuvonta',
  'soratienTasaustarve',
  'tieTaiTienReunaOnPainunut',
  'siltaanLiittyvaIlmoitus',
  'polynsidontatarve',
  'liikennevalotEivatToimi',
  'kunnossapitoJaHoitotyo',
  'vettaTiella',
  'aurausvallitNakemaesteena',
  'ennakoivaVaroitus',
  'levahdysalueeseenLiittyvaIlmoitus',
  'sohjonPoisto',
  'liikennekeskusKuitannutLoppuneeksi',
  'muuToimenpidetarve',
  'hiekoitustarve',
  'tietOvatJaatymassa',
  'jaatavaaSadetta',
  'tienvarsilaitteisiinLiittyvaIlmoitus',
  'oljyaTiella',
  'sahkojohtoOnPudonnutTielle',
  'tieOnSortunut',
  'tievalaistusVioittunut',
  'testilahetys',
  'tievalaistuksenLamppujaPimeana',
  'virkaApupyynto',
  'tiemerkintoihinLiittyvaIlmoitus',
  'tulvavesiOnNoussutTielle',
  'niittotarve',
  'kuormaOnLevinnytTielle',
  'tieOnLiukas',
  'tiellaOnEste',
  'harjaustarve',
  'hoylaystarve',
  'tietyokysely',
  'paallystevaurio',
  'rikkoutunutAjoneuvoTiella',
  'mustaaJaataTiella',
  'kevyenLiikenteenVaylillaOnLunta',
  'hirviaitaVaurioitunut',
  'korvauskysely',
  'puitaOnKaatunutTielle',
  'rumpuunLiittyvaIlmoitus',
  'lasiaTiella',
  'liukkaudentorjuntatarve',
  'alikulkukaytavassaVetta',
  'kevyenliikenteenAlikulkukaytavassaVetta',
  'tievalaistuksenLamppuPimeana',
  'kevyenLiikenteenVaylatOvatJaisiaJaLiukkaita',
  'kuoppa',
  'toimenpidekysely',
  'pysakkiinLiittyvaIlmoitus',
  'nakemaalueenRaivaustarve',
  'vesakonraivaustarve',
  'muuttuvatOpasteetEivatToimi',
  'tievalaistus',
  'vesiSyovyttanytTienReunaa',
  'raskasAjoneuvoJumissa',
  'myrskyvaurioita',
  'kaidevaurio',
  'liikennemerkkeihinLiittyvaIlmoitus',
  'siirrettavaAjoneuvo',
  'tielleOnVuotanutNestettaLiikkuvastaAjoneuvosta',
  'tapahtumaOhi',
  'kevyenLiikenteenVaylatOvatjaatymassa',
  'tietOvatjaisiaJamarkia',
  'kiertotienKunnossapito'
);

ALTER TABLE ilmoitus
  RENAME COLUMN selitteet TO selitteet_temp;
ALTER TABLE asiakaspalauteluokka
  RENAME COLUMN selitteet TO selitteet_temp;
ALTER TABLE ilmoitus
  ADD selitteet ilmoituksenselite [];
ALTER TABLE asiakaspalauteluokka
  ADD selitteet ilmoituksenselite [];

UPDATE ilmoitus
SET
  selitteet = selitteet_temp :: TEXT :: ilmoituksenselite [];

UPDATE asiakaspalauteluokka
SET selitteet = selitteet_temp :: TEXT :: ilmoituksenselite [];

ALTER TABLE ilmoitus
  DROP COLUMN selitteet_temp;
ALTER TABLE asiakaspalauteluokka
  DROP COLUMN selitteet_temp;

DROP TYPE ilmoituksenselite_temp CASCADE;