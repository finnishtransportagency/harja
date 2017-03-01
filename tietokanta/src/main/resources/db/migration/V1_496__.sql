CREATE TYPE raakaainetyyppi AS ENUM ('raskas_polttooljy', 'kevyt_polttooljy', 'nestekaasu');

CREATE TABLE urakkatyypin_indeksi (
  id SERIAL PRIMARY KEY,
  urakkatyyppi urakkatyyppi NOT NULL,
  indeksinimi TEXT NOT NULL,
  raakaaine raakaainetyyppi DEFAULT NULL,
  koodi TEXT DEFAULT NULL,
  UNIQUE (urakkatyyppi, indeksinimi)
);

CREATE TABLE paallystysurakan_indeksit (
  id SERIAL PRIMARY KEY,
  urakka integer REFERENCES urakka(id) NOT NULL,
  urakkavuosi INTEGER NOT NULL,

  indeksi_polttooljyraskas INTEGER REFERENCES urakkatyypin_indeksi(id),
  indeksi_polttooljykevyt INTEGER REFERENCES urakkatyypin_indeksi(id),
  indeksi_nestekaasu INTEGER REFERENCES urakkatyypin_indeksi(id),

  lahtotason_vuosi INTEGER,
  lahtotason_kuukausi INTEGER,

  -- muokkausmetatiedot
  poistettu BOOLEAN DEFAULT FALSE,
  muokkaaja INTEGER,
  muokattu TIMESTAMP,
  luoja INTEGER,
  luotu TIMESTAMP DEFAULT NOW(),

  CHECK (urakkavuosi > 1970 AND urakkavuosi < 2050),
  CHECK (lahtotason_kuukausi > 0 AND lahtotason_kuukausi < 13),
  CHECK (lahtotason_vuosi > 1970 AND lahtotason_vuosi < 2050)
);

CREATE UNIQUE INDEX on paallystysurakan_indeksit (urakka, urakkavuosi) WHERE poistettu IS NOT TRUE;

INSERT INTO urakkatyypin_indeksi(urakkatyyppi, indeksinimi, koodi, raakaaine)
VALUES
  ('hoito'::urakkatyyppi, 'MAKU 2005', NULL, NULL),
  ('hoito'::urakkatyyppi, 'MAKU 2010', NULL, NULL),
  ('tiemerkinta'::urakkatyyppi, 'MAKU 2010', NULL, NULL),
  ('paallystys'::urakkatyyppi, 'Platts: FO 3,5%S CIF NWE Cargo', 'ABWGL03', 'raskas_polttooljy'), -- bitumin arvoa varten
  ('paikkaus'::urakkatyyppi, 'Platts: FO 3,5%S CIF NWE Cargo', 'ABWGL03', 'raskas_polttooljy'),
  ('paallystys'::urakkatyyppi, 'Platts: ULSD 10ppmS CIF NWE Cargo', 'ABWHK03', 'kevyt_polttooljy'), -- kevyt polttoöljy
  ('paikkaus'::urakkatyyppi, 'Platts: ULSD 10ppmS CIF NWE Cargo', 'ABWHK03', 'kevyt_polttooljy'),
  ('paallystys'::urakkatyyppi, 'Platts: Propane CIF NWE 7kt+', 'PMUEE03', 'nestekaasu'),  -- nestekaasu
  ('paikkaus'::urakkatyyppi, 'Platts: Propane CIF NWE 7kt+', 'PMUEE03', 'nestekaasu');
