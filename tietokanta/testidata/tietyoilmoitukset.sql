INSERT INTO tietyoilmoitus (luotu,
                           luoja,
                           ilmoittaja,
                           ilmoittaja_etunimi,
                           ilmoittaja_sukunimi,
                           ilmoittaja_matkapuhelin,
                           ilmoittaja_sahkoposti,
                           urakka,
                           urakka_nimi,
                           urakkatyyppi,
                           urakoitsijayhteyshenkilo,
                           urakoitsijayhteyshenkilo_etunimi,
                           urakoitsijayhteyshenkilo_sukunimi,
                           urakoitsijayhteyshenkilo_matkapuhelin,
                           urakoitsijayhteyshenkilo_sahkoposti,
                           tilaaja,
                           tilaajan_nimi,
                           tilaajayhteyshenkilo,
                           tilaajayhteyshenkilo_etunimi,
                           tilaajayhteyshenkilo_sukunimi,
                           tilaajayhteyshenkilo_matkapuhelin,
                           tilaajayhteyshenkilo_sahkoposti,
                           tyotyypit,
                           sijainti,
                           tr_numero,
                           tr_alkuosa,
                           tr_alkuetaisyys,
                           tr_loppuosa,
                           tr_loppuetaisyys,
                           tien_nimi,
                           kunnat,
                           alkusijainnin_kuvaus,
                           loppusijainnin_kuvaus,
                           alku,
                           loppu,
                           tyoajat,
                           vaikutussuunta,
                           kaistajarjestelyt,
                           nopeusrajoitukset,
                           tienpinnat,
                           kiertotien_mutkaisuus,
                           kiertotienpinnat,
                           liikenteenohjaus,
                           liikenteenohjaaja,
                           viivastys_normaali_liikenteessa,
                           viivastys_ruuhka_aikana,
                           ajoneuvo_max_korkeus,
                           ajoneuvo_max_leveys,
                           ajoneuvo_max_pituus,
                           ajoneuvo_max_paino,
                           huomautukset,
                           ajoittaiset_pysatykset,
                           ajoittain_suljettu_tie,
                           pysaytysten_alku,
                           pysaytysten_loppu,
                           lisatietoja)

VALUES (
  '2016-06-06 06:06:06',
  (SELECT id
   FROM kayttaja
   WHERE sahkoposti = 'yit_pk2@example.org'),
  (SELECT id
   FROM kayttaja
   WHERE sahkoposti = 'yit_pk2@example.org'),
  (SELECT etunimi
   FROM kayttaja
   WHERE sahkoposti = 'yit_pk2@example.org'),
  (SELECT sukunimi
   FROM kayttaja
   WHERE sahkoposti = 'yit_pk2@example.org'),
  (SELECT puhelin
   FROM kayttaja
   WHERE sahkoposti = 'yit_pk2@example.org'),
  (SELECT sahkoposti
   FROM kayttaja
   WHERE sahkoposti = 'yit_pk2@example.org'),
  (SELECT id
   FROM urakka
   WHERE nimi = 'Oulun alueurakka 2005-2012'),
  (SELECT nimi
   FROM urakka
   WHERE nimi = 'Oulun alueurakka 2005-2012'),
  (SELECT tyyppi
   FROM urakka
   WHERE nimi = 'Oulun alueurakka 2005-2012'),
  (SELECT id
   FROM kayttaja
   WHERE sahkoposti = 'yit_pk2@example.org'),
  (SELECT etunimi
   FROM kayttaja
   WHERE sahkoposti = 'yit_pk2@example.org'),
  (SELECT sukunimi
   FROM kayttaja
   WHERE sahkoposti = 'yit_pk2@example.org'),
  (SELECT puhelin
   FROM kayttaja
   WHERE sahkoposti = 'yit_pk2@example.org'),
  (SELECT sahkoposti
   FROM kayttaja
   WHERE sahkoposti = 'yit_pk2@example.org'),
  (SELECT id
   FROM organisaatio
   WHERE lyhenne = 'POP'),
  (SELECT nimi
   FROM organisaatio
   WHERE lyhenne = 'POP'),
  (SELECT id
   FROM kayttaja
   WHERE sahkoposti = 'tero.toripolliisi@example.com'),
  (SELECT etunimi
   FROM kayttaja
   WHERE sahkoposti = 'tero.toripolliisi@example.com'),
  (SELECT sukunimi
   FROM kayttaja
   WHERE sahkoposti = 'tero.toripolliisi@example.com'),
  (SELECT puhelin
   FROM kayttaja
   WHERE sahkoposti = 'tero.toripolliisi@example.com'),
  (SELECT sahkoposti
   FROM kayttaja
   WHERE sahkoposti = 'tero.toripolliisi@example.com'),
  '{"(Tienrakennus,Rakennetaan tietä)"}',
  st_makeline(st_makepoint(432725.469, 7214041.876), st_makepoint(433437.969, 7214482.376)),
  20,
  1,
  1,
  5,
  1,
  'Kuusamontie',
  'Oulu, Kiiminki',
  'Kuusamontien alussa',
  'Jossain Kiimingissä',
  '2016-06-06 06:06:06',
  '2017-07-07 07:07:07',
  NULL,
  'molemmat',
  'ajokaistaSuljettu',
  ARRAY ['(30, 100)'] :: tietyon_nopeusrajoitus [],
  ARRAY ['(paallystetty, 100)'] :: tietyon_tienpinta [],
  'loivatMutkat',
  ARRAY ['(murske, 100)'] :: tietyon_tienpinta [],
  'ohjataanVuorotellen',
  'liikennevalot',
  15,
  30,
  4,
  3,
  10,
  4000,
  'avotuli',
  TRUE,
  TRUE,
  '2016-06-06 06:06:06',
  '2017-07-07 07:07:07',
  'Tämä on testi-ilmoitus')
