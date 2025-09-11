DROP TABLE IF EXISTS mhu_muutos_historia;
DROP TABLE IF EXISTS mhu_muutos_liite_historia;
DROP TABLE IF EXISTS mhu_muutos_kustannusvaikutus_historia;
DROP TABLE IF EXISTS mhu_muutos_tehtava_ja_maaraluettelo_historia;
DROP TABLE IF EXISTS mhu_muutos_kulu_historia;


-- TODO: Tarkista mitä asioita ei haluta kopioida historiatauluihin LIKE komennolla käyttäen EXCLUDING
-- Historiataulut --
-- https://www.postgresql.org/docs/current/sql-createtable.html#SQL-CREATETABLE-PARMS-LIKE

CREATE TABLE mhu_muutos_historia
(
    LIKE mhu_muutos EXCLUDING CONSTRAINTS EXCLUDING INDEXES
);
-- Varmistetaan että samaa (id, versio)-yhdistelmää on vain yksi
CREATE UNIQUE INDEX mhu_muutos_historia_id_versio ON mhu_muutos_historia (id, versio);

-- Tee validi_aikana sarakkeelle indeksi
CREATE INDEX mhu_muutos_historia_validi_idx ON mhu_muutos_historia USING GIST (validi_aikana);

--

CREATE TABLE mhu_muutos_liite_historia
(
    LIKE mhu_muutos_liite EXCLUDING CONSTRAINTS EXCLUDING INDEXES
);

--

CREATE TABLE mhu_muutos_kustannusvaikutus_historia
(
    LIKE mhu_muutos_kustannusvaikutus EXCLUDING CONSTRAINTS EXCLUDING INDEXES
);

--

CREATE TABLE mhu_muutos_tehtava_ja_maaraluettelo_historia
(
    LIKE mhu_muutos_tehtava_ja_maaraluettelo EXCLUDING CONSTRAINTS EXCLUDING INDEXES
);

--

CREATE TABLE mhu_muutos_kulu_historia
(
    LIKE mhu_muutos_kulu EXCLUDING CONSTRAINTS EXCLUDING INDEXES
);
