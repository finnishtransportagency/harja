ALTER TABLE tehtava
ADD COLUMN "nayta-jarjestelmakirjaus-tehtavatoteumissa" BOOLEAN

COMMENT ON COLUMN tehtava.nopeusrajoitus is 'Tehtävän mahdollisen reittitoteuman muodostukseen käytetty nopeusrajoitus km/h. Mikäli reittipisteiden välinen nopeus ylittää tämän, ei piirretä viivaa. Default 108 tulee aiemmin kovakoodatusta arvosta.';
