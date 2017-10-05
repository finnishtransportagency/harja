-- Lisätään hienoja aluksia
INSERT INTO vv_alus (mmsi, nimi, lisatiedot, luoja) VALUES
  (230990040, 'Rohmu', 'Hieno laiva', (SELECT id
                                       FROM kayttaja
                                       WHERE kayttajanimi = 'tero')),
  (230111580, 'Ronsu', '', (SELECT id
                            FROM kayttaja
                            WHERE kayttajanimi = 'tero')),
  (230941190, 'Cuba Libre', '', (SELECT id
                                 FROM kayttaja
                                 WHERE kayttajanimi = 'tero')),
  (230011240, 'Ampiainen', '', (SELECT id
                                FROM kayttaja
                                WHERE kayttajanimi = 'tero')),
  (230983750, 'Humalaja', '', (SELECT id
                               FROM kayttaja
                               WHERE kayttajanimi = 'tero')),
  (230110850, 'Sierra Nevada', '', (SELECT id
                                    FROM kayttaja
                                    WHERE kayttajanimi = 'tero')),
  (230030440, 'Karhu', '', (SELECT id
                            FROM kayttaja
                            WHERE kayttajanimi = 'tero')),
  (230939680, 'Savannin taluttaja', 'Erikoiskalustoa kyydissä', (SELECT id
                                                                 FROM kayttaja
                                                                 WHERE kayttajanimi = 'tero')),
  (230118650, 'Meripoika', '', (SELECT id
                                FROM kayttaja
                                WHERE kayttajanimi = 'tero')),
  (230085750, 'Aimo', '', (SELECT id
                           FROM kayttaja
                           WHERE kayttajanimi = 'tero')),
  (230078710, 'Pahvilaatikko', '', (SELECT id
                                    FROM kayttaja
                                    WHERE kayttajanimi = 'tero')),
  (230099160, 'Vanha poika', '', (SELECT id
                                  FROM kayttaja
                                  WHERE kayttajanimi = 'tero')),
  (230113670, 'Leski', '', (SELECT id
                            FROM kayttaja
                            WHERE kayttajanimi = 'tero')),
  (230942290, 'Sienimetsä', '', (SELECT id
                                 FROM kayttaja
                                 WHERE kayttajanimi = 'tero')),
  (230980890, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230085710, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997510, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230368000, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230031001, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230087740, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230942790, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997360, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230353000, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230942970, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230028680, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997470, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997550, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230940250, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230940290, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230111560, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230024450, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997310, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230669000, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230093090, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230941700, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230094240, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230094210, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230938340, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230982380, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230939410, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230046150, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230108280, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230668000, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230093590, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230983550, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230942250, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230094190, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230115770, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230111670, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230112880, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230010760, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997540, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230111270, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230050100, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230938900, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230941350, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230938740, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230117980, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230938050, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230056260, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230939690, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230938890, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230977590, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230021990, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230994590, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230030000, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230118060, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230943610, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230028000, '', '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero'));

-- Liitetään muutama alus urakkaan

INSERT INTO vv_alus_urakka (alus, urakka, luoja)
VALUES (230990040, (SELECT id
                    FROM urakka
                    WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        (SELECT id
         FROM kayttaja
         WHERE kayttajanimi = 'tero'));
INSERT INTO vv_alus_urakka (alus, urakka, luoja)
VALUES (230111580, (SELECT id
                    FROM urakka
                    WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        (SELECT id
         FROM kayttaja
         WHERE kayttajanimi = 'tero'));
INSERT INTO vv_alus_urakka (alus, urakka, luoja)
VALUES (230941190, (SELECT id
                    FROM urakka
                    WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        (SELECT id
         FROM kayttaja
         WHERE kayttajanimi = 'tero'));

-- Lisää Rohmulle reittipisteitä

INSERT INTO vv_alus_sijainti (alus, sijainti, aika)
VALUES
  (230111580, point(3141767.3069556984, 3976597.579811954), '2017-09-05 23:01:18.822000'),
  (230111580, point(2806903.170666764, 4195938.182243582), '2017-10-05 15:47:48.779000'),
  (230111580, point(2791246.5561552388, 4197196.334044084), '2017-10-05 15:46:18.362000'),
  (230111580, point(2871884.0769665544, 4095642.2710512094), '2017-10-05 07:02:14.440000'),
  (230111580, point(2792868.2722486374, 4159962.387951176), '2017-10-03 14:54:21.384000'),
  (230111580, point(3277735.832690282, 4496480.798635741), '2017-09-23 12:15:18.395000'),
  (230111580, point(2960550.943000939, 3997933.7090460034), '2017-09-14 09:21:28.879000'),
  (230111580, point(3303221.8787546675, 4505835.4894918855), '2017-08-06 11:55:13.316000'),
  (230111580, point(2725951.8932428183, 4031744.664115552), '2017-08-28 04:02:03.925000'),
  (230111580, point(2664107.4795862716, 4014885.453144356), '2017-09-07 00:24:49.883000'),
  (230111580, point(3166032.2490339926, 3899989.9020077223), '2017-10-05 15:47:42.632000'),
  (230111580, point(3355202.687406264, 4617327.461599002), '2017-10-05 15:46:07.267000'),
  (230111580, point(3405849.726350086, 3936104.2655446827), '2017-10-05 15:48:52.916000'),
  (230111580, point(2609242.6314362907, 3964535.5614276105), '2017-09-13 08:06:01.448000'),
  (230111580, point(3213293.666755903, 3974449.4596298235), '2017-10-05 14:09:31.177000'),
  (230111580, point(3238442.3553591766, 3970663.361861761), '2017-10-05 14:23:37.362000')

