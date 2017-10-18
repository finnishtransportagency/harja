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
  (230011240, 'Ampiainen', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                                FROM kayttaja
                                WHERE kayttajanimi = 'tero')),
  (230983750, 'Humalaja', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                               FROM kayttaja
                               WHERE kayttajanimi = 'tero')),
  (230110850, 'Sierra Nevada', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                                    FROM kayttaja
                                    WHERE kayttajanimi = 'tero')),
  (230030440, 'Karhu', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                            FROM kayttaja
                            WHERE kayttajanimi = 'tero')),
  (230939680, 'Savannin taluttaja', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), 'Erikoiskalustoa kyydissä', (SELECT id
                                                                 FROM kayttaja
                                                                 WHERE kayttajanimi = 'tero')),
  (230118650, 'Meripoika', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                                FROM kayttaja
                                WHERE kayttajanimi = 'tero')),
  (230085750, 'Aimo', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                           FROM kayttaja
                           WHERE kayttajanimi = 'tero')),
  (230078710, 'Pahvilaatikko', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                                    FROM kayttaja
                                    WHERE kayttajanimi = 'tero')),
  (230099160, 'Vanha poika', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                                  FROM kayttaja
                                  WHERE kayttajanimi = 'tero')),
  (230113670, 'Leski', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                            FROM kayttaja
                            WHERE kayttajanimi = 'tero')),
  (230942290, 'Sienimetsä', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                                 FROM kayttaja
                                 WHERE kayttajanimi = 'tero')),
  (230980890, 'A', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230085710, 'B', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997510, 'C', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230368000, 'D', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230031001, 'E', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230087740, 'F', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230942790, 'G', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997360, 'H', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230353000, 'I', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230942970, 'J', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230028680, 'K', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997470, 'L', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997550, 'M', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230940250, 'N', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230940290, 'O', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230111560, 'P', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230024450, 'Q', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997310, 'R', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230669000, 'S', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230093090, 'T', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230941700, 'U', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230094240, 'V', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230094210, 'W', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230938340, 'X', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230982380, 'Y', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230939410, 'Å', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230046150, 'Ä', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230108280, 'Ö', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230668000, '1', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230093590, '2', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230983550, '3', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230942250, '4', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230094190, '5', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230115770, '6', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230111670, '7', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230112880, '8', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230010760, '9', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230997540, '10', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230111270, '11', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230050100, '12', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230938900, '13', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230941350, '14', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230938740, '15', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230117980, '16', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230938050, '17', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230056260, '18', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230939690, '19', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230938890, '20', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230977590, '21', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230021990, '22', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230994590, '23', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230030000, '24', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230118060, '25', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230943610, '26', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
                       FROM kayttaja
                       WHERE kayttajanimi = 'tero')),
  (230028000, '27', (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'), '', (SELECT id
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