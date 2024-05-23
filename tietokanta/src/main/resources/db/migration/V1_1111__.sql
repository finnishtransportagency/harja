-- Rahavaraus id:n lisäys ja populointi 

--   ============================
--     toteutuneet_kustannukset
--   ============================
--   Lisää rahavaraus_id sarake
ALTER TABLE toteutuneet_kustannukset ADD COLUMN rahavaraus_id INT REFERENCES rahavaraus (id);

--   Etsi rahavaraus_id, Äkilliset hoitotyöt 
UPDATE toteutuneet_kustannukset
   SET rahavaraus_id = ra.id
  FROM (SELECT id FROM rahavaraus WHERE nimi = 'Äkilliset hoitotyöt') ra
 WHERE tyyppi = 'akillinen-hoitotyo' 
   AND ra.id IS NOT NULL;

--   Vahinkojen korvaukset
UPDATE toteutuneet_kustannukset
   SET rahavaraus_id = ra.id
  FROM (SELECT id FROM rahavaraus WHERE nimi = 'Vahinkojen korvaukset') ra
 WHERE tyyppi = 'vahinkojen-korjaukset' 
   AND ra.id IS NOT NULL;


--   ============================
--          kulu_kohdistus
--   ============================
--   id lisätty jo (_1096), mutta populointia ei - Äkilliset hoitotyöt
UPDATE kulu_kohdistus
   SET rahavaraus_id = ra.id
  FROM (SELECT id FROM rahavaraus WHERE nimi = 'Äkilliset hoitotyöt') AS ra
 WHERE maksueratyyppi = 'akillinen-hoitotyo'
   AND ra.id IS NOT NULL;

--   Maksuerätyyppi 'muu', kohdellaan vahinkojen kovauksena laskutusyhteenvedossa, joten etsi sille rahavaraus id
UPDATE kulu_kohdistus
   SET rahavaraus_id = ra.id
  FROM (SELECT id FROM rahavaraus WHERE nimi = 'Vahinkojen korvaukset') AS ra
 WHERE maksueratyyppi = 'muu'
   AND ra.id IS NOT NULL;


--   ============================
--       kustannusarvioitu_tyo
--   ============================
--   id lisätty jo(_1096), mutta populointia ei - Äkilliset hoitotyöt
UPDATE kustannusarvioitu_tyo
   SET rahavaraus_id = ra.id
  FROM (SELECT id FROM rahavaraus WHERE nimi = 'Äkilliset hoitotyöt') ra
 WHERE tyyppi = 'akillinen-hoitotyo' 
   AND ra.id IS NOT NULL;

--   Vahinkojen korvaukset
UPDATE kustannusarvioitu_tyo
   SET rahavaraus_id = ra.id
  FROM (SELECT id FROM rahavaraus WHERE nimi = 'Vahinkojen korvaukset') ra
 WHERE tyyppi = 'vahinkojen-korjaukset' 
   AND ra.id IS NOT NULL;
