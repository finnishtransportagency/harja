ALTER TABLE tietyoilmoitus
  ADD "urakoitsijan-ytunnus" CHAR(9),
  ADD COLUMN tila LAHETYKSEN_TILA,
  ADD COLUMN lahetetty TIMESTAMP,
  ADD COLUMN lahetysid VARCHAR(255);

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('tloik', 'tietyoilmoituksen-lahetys');

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('tloik', 'tietyoilmoituksen-vastaanotto');
