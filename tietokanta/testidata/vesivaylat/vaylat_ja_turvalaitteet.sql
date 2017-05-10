INSERT INTO vv_vayla
(nimi, "vatu-id", tyyppi)
VALUES
  ('Hietasaaren läntinen rinnakkaisväylä', 1, 'kauppamerenkulku'::vv_vaylatyyppi);

INSERT INTO vv_vayla
(nimi, "vatu-id", tyyppi)
VALUES
  ('Akonniemen väylät', 2, 'kauppamerenkulku'::vv_vaylatyyppi);

INSERT INTO vv_turvalaite
(nimi, tunniste, tyyppi, vayla)
    VALUES
      ('Hietasaaren pienempi poiju', '1234', 'poiju', (SELECT id FROM vv_vayla WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

INSERT INTO vv_turvalaite
(nimi, tunniste, tyyppi, vayla)
VALUES
  ('Hietasaaren poiju', '12345', 'poiju', (SELECT id FROM vv_vayla WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

INSERT INTO vv_turvalaite
(nimi, tunniste, tyyppi, vayla)
VALUES
  ('Hietasaaren viitta', '12346', 'viitta', (SELECT id FROM vv_vayla WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

INSERT INTO vv_turvalaite
(nimi, tunniste, tyyppi, vayla)
VALUES
  ('Hietasaaren kyltti', '12347', 'kiintea', (SELECT id FROM vv_vayla WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

INSERT INTO vv_turvalaite
(nimi, tunniste, tyyppi, vayla)
VALUES
  ('Akonniemen kyltti', '12348', 'kiintea', (SELECT id FROM vv_vayla WHERE nimi = 'Akonniemen väylät'));