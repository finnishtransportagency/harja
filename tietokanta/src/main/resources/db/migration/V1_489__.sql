CREATE TABLE urakkatyypin_indeksi (
  id SERIAL PRIMARY KEY,
  urakkatyyppi urakkatyyppi NOT NULL,
  indeksinimi TEXT NOT NULL,
  materiaali TEXT DEFAULT NULL,
  koodi TEXT DEFAULT NULL,
  UNIQUE (urakkatyyppi, indeksinimi)
);

CREATE TABLE paallystysurakan_indeksit (
  id SERIAL PRIMARY KEY,
  urakka integer REFERENCES urakka(id) NOT NULL,
  urakkavuosi INTEGER,

  indeksi_polttooljyraskas INTEGER REFERENCES urakkatyypin_indeksi(id),
  indeksi_polttooljykevyt INTEGER REFERENCES urakkatyypin_indeksi(id),
  indeksi_nestekaasu INTEGER REFERENCES urakkatyypin_indeksi(id),

  lahtotaso_vuosi INTEGER,
  lahtotaso_kuukausi INTEGER,

  -- muokkausmetatiedot
  poistettu BOOLEAN DEFAULT FALSE,
  muokkaaja INTEGER,
  muokattu TIMESTAMP,
  luoja INTEGER,
  luotu TIMESTAMP DEFAULT NOW(),

  CHECK (urakkavuosi > 1970 AND urakkavuosi < 2050),
  CHECK (lahtotaso_kuukausi > 0 AND lahtotaso_kuukausi < 13),
  CHECK (lahtotaso_vuosi > 1970 AND lahtotaso_vuosi < 2050),

  CONSTRAINT uniikki_paallystysindeksi UNIQUE (urakka, urakkavuosi)
);


INSERT INTO urakkatyypin_indeksi(urakkatyyppi, indeksinimi, koodi, materiaali)
VALUES
  ('hoito'::urakkatyyppi, 'MAKU 2005', NULL, NULL),
  ('hoito'::urakkatyyppi, 'MAKU 2010', NULL, NULL),
  ('tiemerkinta'::urakkatyyppi, 'MAKU 2010', NULL, NULL),
  ('paallystys'::urakkatyyppi, 'Platts: FO 3,5%S CIF NWE Cargo', 'ABWGL03', 'raskas_po'), -- bitumin arvoa varten
  ('paikkaus'::urakkatyyppi, 'Platts: FO 3,5%S CIF NWE Cargo', 'ABWGL03', 'raskas_po'),
  ('paallystys'::urakkatyyppi, 'Platts: ULSD 10ppmS CIF NWE Cargo', 'ABWHK03', 'kevyt_po'), -- kevyt polttoöljy
  ('paikkaus'::urakkatyyppi, 'Platts: ULSD 10ppmS CIF NWE Cargo', 'ABWHK03', 'kevyt_po'),
  ('paallystys'::urakkatyyppi, 'Platts: Propane CIF NWE 7kt+', 'PMUEE03', 'nestekaasu'),  -- nestekaasu
  ('paikkaus'::urakkatyyppi, 'Platts: Propane CIF NWE 7kt+', 'PMUEE03', 'nestekaasu');
