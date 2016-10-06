ALTER TYPE ilmoittajatyyppi
RENAME TO ilmoittajatyyppi_temp;

ALTER TYPE ilmoituksenselite
RENAME TO ilmoituksenselite_temp;

CREATE TYPE ilmoittajatyyppi AS ENUM (
  'viranomainen',
  'muu',
  'asukas',
  'tienkayttaja',
  'urakoitsija',
  'vagtrafikant'
);

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
  'tielleOnVuotanutNestettäLiikkuvastaAjoneuvosta',
  'tapahtumaOhi',
  'kevyenLiikenteenVaylatOvatjaatymassa',
  'tietOvatjaisiäJamarkia',
  'kiertotienKunnossapito'
);

ALTER TABLE ilmoitus
  RENAME COLUMN ilmoittaja_tyyppi TO ilmoittaja_tyyppi_temp;
ALTER TABLE ilmoitus
  RENAME COLUMN selitteet TO selitteet_temp;
ALTER TABLE ilmoitus
  ADD ilmoittaja_tyyppi ilmoittajatyyppi;
ALTER TABLE ilmoitus
  ADD selitteet ilmoituksenselite [];

UPDATE ilmoitus
SET
  ilmoittaja_tyyppi = ilmoittaja_tyyppi_temp :: TEXT :: ilmoittajatyyppi,
  selitteet         = selitteet_temp :: TEXT :: ilmoituksenselite [];

ALTER TABLE ilmoitus
  DROP COLUMN ilmoittaja_tyyppi_temp;
ALTER TABLE ilmoitus
  DROP COLUMN selitteet_temp;

DROP TYPE ilmoituksenselite_temp CASCADE;
DROP TYPE ilmoittajatyyppi_temp CASCADE;


