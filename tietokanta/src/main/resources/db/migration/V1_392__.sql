-- Reaaliaikaseurannan uudet tehtävät

ALTER TYPE suoritettavatehtava RENAME TO suoritettavatehtava_tmp;

CREATE TYPE suoritettavatehtava AS
 ENUM ('auraus ja sohjonpoisto',
       'suolaus',
       'pistehiekoitus',
       'linjahiekoitus',
       'pinnan tasaus',
       'liikennemerkkien puhdistus',
       'lumivallien madaltaminen',
       'sulamisveden haittojen torjunta',
       'tiestotarkastus',
       'kelintarkastus',
       'harjaus',
       'koneellinen niitto',
       'koneellinen vesakonraivaus',
       'sorateiden muokkaushoylays',
       'sorateiden polynsidonta',
       'sorateiden tasaus',
       'sorastus',
       'paallysteiden paikkaus',
       'paallysteiden juotostyot',
       'siltojen puhdistus',
       'l- ja p-alueiden puhdistus',
       'muu',
       'liuossuolaus',
       'aurausviitoitus ja kinostimet',
       'lumensiirto',
       'paannejaan poisto',

       -- Ylläpidon uudet tyypit
       'pääasfalttilevitin',
       'remix-laite',
       'sekoitus- ja stabilointijyrsin',
       'tma-laite'

       -- Nämä olivat skeemassa, mutta puuttuvat kannasta(?)
       'liikennemerkkien ym. kunnossapito', -- max. 63 merkkiä, skeemassa: "liikennemerkkien, opasteiden ja liikenteenohjauslaitteiden hoito sekä reunapaalujen kunnossapito"
       'lumen siirto',
       'palteen poisto',
       'päällystetyn tien sorapientareen täyttö');

ALTER TABLE tyokonehavainto ALTER COLUMN tehtavat TYPE suoritettavatehtava[] USING tehtavat::text::suoritettavatehtava[];
ALTER TABLE toimenpidekoodi ALTER COLUMN suoritettavatehtava TYPE suoritettavatehtava USING suoritettavatehtava::text::suoritettavatehtava;

DROP TYPE suoritettavatehtava_tmp CASCADE;