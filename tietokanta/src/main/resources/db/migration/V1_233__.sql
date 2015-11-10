-- Lisää uuden urakkatyypin: siltakorjaus

DROP MATERIALIZED VIEW sillat_alueurakoittain;
DROP MATERIALIZED VIEW urakoiden_alueet;
DROP MATERIALIZED VIEW pohjavesialueet_urakoittain;

ALTER TYPE urakkatyyppi RENAME TO _urtyyppi;

CREATE TYPE urakkatyyppi AS ENUM ('hoito', 'paallystys', 'paikkaus', 'tiemerkinta', 'valaistus', 'siltakorjaus');

ALTER TABLE urakka RENAME COLUMN tyyppi TO _tyyppi;

ALTER TABLE urakka ADD tyyppi urakkatyyppi;

ALTER TABLE materiaalikoodi DROP COLUMN urakkatyyppi;
ALTER TABLE materiaalikoodi ADD COLUMN urakkatyyppi urakkatyyppi;
ALTER TABLE ilmoitus DROP COLUMN urakkatyyppi;
ALTER TABLE ilmoitus ADD COLUMN urakkatyyppi urakkatyyppi;

UPDATE urakka SET tyyppi = _tyyppi::text::urakkatyyppi;

ALTER TABLE urakka DROP COLUMN _tyyppi;
DROP TYPE _urtyyppi;

CREATE MATERIALIZED VIEW sillat_alueurakoittain AS
       SELECT u.id as urakka, s.id as silta
         FROM urakka u
	      JOIN hanke h ON u.hanke = h.id
	      JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro
	      JOIN silta s ON ST_CONTAINS(ST_ENVELOPE(au.alue), s.alue)
        WHERE u.tyyppi = 'hoito'::urakkatyyppi;

CREATE MATERIALIZED VIEW urakoiden_alueet AS
SELECT u.id, u.tyyppi,

  CASE
  WHEN u.tyyppi='hoito' THEN au.alue
  ELSE u.alue
  END

FROM urakka u
  LEFT JOIN hanke h ON u.hanke = h.id
  LEFT JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro;

CREATE MATERIALIZED VIEW pohjavesialueet_urakoittain AS
  WITH
      urakat_alueet AS (
        SELECT u.id, au.alue
        FROM urakka u
          JOIN hanke h ON u.hanke = h.id
          JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro
        WHERE u.tyyppi = 'hoito'::urakkatyyppi),
      pohjavesialue_alue AS (
        SELECT p.nimi, p.tunnus, ST_UNION(p.alue) as alue
        FROM pohjavesialue p GROUP BY nimi, tunnus)
  SELECT pa.nimi, pa.tunnus, pa.alue, ua.id as urakka
  FROM pohjavesialue_alue pa
    CROSS JOIN urakat_alueet ua
  WHERE ST_CONTAINS(ua.alue, pa.alue);
