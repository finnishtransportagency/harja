ALTER TABLE tietyoilmoitus
  ADD "urakoitsijan-ytunnus" CHAR(9),
  ADD COLUMN tila LAHETYKSEN_TILA,
  ADD COLUMN lahetetty TIMESTAMP,
  ADD COLUMN lahetysid VARCHAR(255);

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('tloik', 'tietyoilmoituksen-lahetys');

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('tloik', 'tietyoilmoituksen-vastaanotto');

-- Jostain syystä view ei päivity repeatable migraation kautta, vaan se pitää tehdä numeroidussa migraatiossa

DROP VIEW IF EXISTS tietyoilmoitus_pituus;

CREATE VIEW tietyoilmoitus_pituus AS
  SELECT
    tti.*,
    CASE
    WHEN (tti.osoite).losa IS NOT NULL
      THEN
        ST_Length(tr_osoitteelle_viiva3(
                      (tti.osoite).tie, (tti.osoite).aosa,
                      (tti.osoite).aet, (tti.osoite).losa,
                      (tti.osoite).let))
    ELSE
      0
    END
      AS pituus
  FROM tietyoilmoitus tti;