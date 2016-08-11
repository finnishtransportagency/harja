ALTER TYPE suoritettavatehtava RENAME TO _suoritettavatehtava;

-- Lisää ylläpidon ajoneuvojen tehtävät suoritettaviin tehtäviin
CREATE TYPE suoritettavatehtava AS
ENUM (
  'auraus ja sohjonpoisto',
  'aurausviitoitus ja kinostimet',
  'harjaus',
  'kelintarkastus',
  'koneellinen niitto',
  'koneellinen vesakonraivaus',
  'l- ja p-alueiden puhdistus',
  'liikennemerkkien puhdistus',
  'liik. opast. ja ohjausl. hoito seka reunapaalujen kun.pito',
  'linjahiekoitus',
  'lumensiirto',
  'lumivallien madaltaminen',
  'ojitus',
  'paallysteiden juotostyot',
  'paallysteiden paikkaus',
  'paannejaan poisto',
  'palteen poisto',
  'pinnan tasaus',
  'pistehiekoitus',
  'paallystetyn tien sorapientareen taytto',
  'siltojen puhdistus',
  'sorastus',
  'sorapientareen taytto',
  'sorateiden muokkaushoylays',
  'sorateiden polynsidonta',
  'sorateiden tasaus',
  'sulamisveden haittojen torjunta',
  'suolaus',
  'tiestotarkastus',
  'asfaltointi',
  'tiemerkinta',
  'kuumennus',
  'sekoitus tai stabilointi',
  'turvalaite'
  'muu');

ALTER TABLE tyokonehavainto RENAME COLUMN tehtavat TO _tehtavat;

ALTER TABLE tyokonehavainto ADD tehtavat suoritettavatehtava[];

UPDATE tyokonehavainto
SET tehtavat = _tehtavat :: TEXT :: suoritettavatehtava[];

ALTER TABLE tyokonehavainto DROP COLUMN _tehtavat;
DROP TYPE __suoritettavatehtava;
