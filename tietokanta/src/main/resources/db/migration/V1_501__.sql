-- Ylläpidon indeksimuutokset

DROP TABLE paallystysurakan_indeksit;

-- Muutetaan raaka-aine tekstiksi
ALTER TABLE urakkatyypin_indeksi
ALTER COLUMN raakaaine TYPE text USING raakaaine::text;


-- Päivitetään raskas_polttooljy => bitumi
UPDATE urakkatyypin_indeksi
   SET raakaaine = 'bitumi'
 WHERE raakaaine = 'raskas_polttooljy';

-- Dropataan tarpeettomaksi jäävä ENUM tyyppi
DROP TYPE raakaainetyyppi;

CREATE TABLE paallystysurakan_indeksi (
  id SERIAL PRIMARY KEY,
  urakka integer REFERENCES urakka (id),
  indeksi integer REFERENCES urakkatyypin_indeksi (id),
  lahtotason_vuosi integer,
  lahtotason_kuukausi integer,

  -- muokkausmetatiedot
  poistettu BOOLEAN DEFAULT FALSE,
  muokkaaja INTEGER,
  muokattu TIMESTAMP,
  luoja INTEGER,
  luotu TIMESTAMP DEFAULT NOW(),

  CHECK (lahtotason_kuukausi > 0 AND lahtotason_kuukausi < 13),
  CHECK (lahtotason_vuosi > 1970 AND lahtotason_vuosi < 2050)
);

CREATE UNIQUE INDEX uniikki_urakka_indeksi
    ON paallystysurakan_indeksi (urakka, indeksi)
 WHERE poistettu IS NOT TRUE;
