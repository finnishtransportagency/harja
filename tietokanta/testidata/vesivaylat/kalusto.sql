-- Lisätään hienoja aluksia
INSERT INTO vv_alus (mmsi, nimi, urakoitsija, lisatiedot, luoja) VALUES
  (230990040, 'Rohmu', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), 'Hieno laiva', (SELECT id
                                       FROM kayttaja
                                       WHERE kayttajanimi = 'tero')),
  (230111580, 'Ronsu', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                            FROM kayttaja
                            WHERE kayttajanimi = 'tero')),
  (230941190, 'Cuba Libre', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                                 FROM kayttaja
                                 WHERE kayttajanimi = 'tero')),
  (230011240, 'Ampiainen', NULL, '', (SELECT id
                                FROM kayttaja
                                WHERE kayttajanimi = 'tero')),
  (230983750, 'Humalaja', NULL, '', (SELECT id
                               FROM kayttaja
                               WHERE kayttajanimi = 'tero')),
  (230110850, 'Sierra Nevada', NULL, '', (SELECT id
                                    FROM kayttaja
                                    WHERE kayttajanimi = 'tero')),
  (230030440, 'Karhu', NULL, '', (SELECT id
                            FROM kayttaja
                            WHERE kayttajanimi = 'tero')),
  (230939680, 'Savannin taluttaja', NULL, 'Erikoiskalustoa kyydissä', (SELECT id
                                                                 FROM kayttaja
                                                                 WHERE kayttajanimi = 'tero')),
  (230118650, 'Meripoika', NULL, '', (SELECT id
                                FROM kayttaja
                                WHERE kayttajanimi = 'tero')),
  (230085750, 'Aimo', NULL, '', (SELECT id
                           FROM kayttaja
                           WHERE kayttajanimi = 'tero')),
  (230078710, 'Pahvilaatikko', NULL, '', (SELECT id
                                    FROM kayttaja
                                    WHERE kayttajanimi = 'tero')),
  (230099160, 'Vanha poika', NULL, '', (SELECT id
                                  FROM kayttaja
                                  WHERE kayttajanimi = 'tero')),
  (230113670, 'Leski', NULL, '', (SELECT id
                            FROM kayttaja
                            WHERE kayttajanimi = 'tero')),
  (230942290, 'Sienimetsä', NULL, '', (SELECT id
                                 FROM kayttaja
                                 WHERE kayttajanimi = 'tero')),
  (230980890, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230085710, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997510, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230368000, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230031001, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230087740, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230942790, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997360, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230353000, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230942970, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230028680, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997470, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997550, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230940250, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230940290, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230111560, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230024450, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997310, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230669000, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230093090, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230941700, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230094240, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230094210, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230938340, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230982380, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230939410, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230046150, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230108280, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230668000, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230093590, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230983550, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230942250, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230094190, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230115770, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230111670, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230112880, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230010760, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997540, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230111270, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230050100, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230938900, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230941350, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230938740, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230117980, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230938050, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230056260, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230939690, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230938890, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230977590, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230021990, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230994590, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230030000, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230118060, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230943610, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230028000, '', NULL, '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero'));

-- Liitetään muutama alus urakkaan

INSERT INTO vv_alus_urakka (alus, lisatiedot,  urakka, luoja)
VALUES (230990040, 'Käytettiin kerran',  (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'), (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero'));
INSERT INTO vv_alus_urakka (alus, lisatiedot, urakka, luoja)
VALUES (230111580, 'Tarvitaan tässä urakassa',  (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'), (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero'));
INSERT INTO vv_alus_urakka (alus, lisatiedot, urakka, luoja)
VALUES (230941190, 'Käytetään urakassa välillä', (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'), (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero'));