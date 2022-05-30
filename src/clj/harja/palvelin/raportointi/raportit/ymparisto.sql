-- name: hae-ymparistoraportti-tiedot
-- Haetaan kuinka paljon jokaista materiaalia on käytetty. Tämä on "summarivi" hoitoluokittaisille riveille,
-- lisäksi tälle riville otetaan mukaan frontin kautta raportoidut käytöt, jolle ei ole hoitoluokkatietoa.
SELECT
  u.id AS urakka_id,
  u.nimi AS urakka_nimi,
  NULL AS luokka,
  NULL AS soratiehoitoluokka,
  mk.id AS materiaali_id,
  mk.nimi AS materiaali_nimi,
  mk.yksikko AS materiaali_yksikko,
  mk.materiaalityyppi AS materiaali_tyyppi,
  date_trunc('month', rtm.paiva) AS kk,
  SUM(rtm.kokonaismaara) AS maara
FROM raportti_toteutuneet_materiaalit rtm
  JOIN urakka u ON rtm."urakka-id" = u.id AND u.urakkanro IS NOT NULL
  JOIN materiaalikoodi mk ON rtm."materiaali-id" = mk.id
WHERE (:urakka::INTEGER IS NULL OR u.id = :urakka)
      AND (:hallintayksikko::INTEGER IS NULL OR u.hallintayksikko = :hallintayksikko)
      AND (rtm.paiva::DATE BETWEEN :alkupvm AND :loppupvm)
      AND u.tyyppi IN ('hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi)
      AND mk.materiaalityyppi != 'erityisalue'
GROUP BY u.id, u.nimi, mk.id, mk.nimi, mk.materiaalityyppi, mk.yksikko, date_trunc('month', rtm.paiva)
UNION
-- Haetaan hoitoluokittaiset käytöt urakan_materiaalin_kaytto_hoitoluokittain taulusta.
SELECT
  u.id AS urakka_id,
  u.nimi AS urakka_nimi,
  hl.hoitoluokka as luokka,
  umkh.soratiehoitoluokka AS soratiehoitoluokka,
  mk.id AS materiaali_id,
  mk.nimi AS materiaali_nimi,
  mk.yksikko AS materiaali_yksikko,
  mk.materiaalityyppi AS materiaali_tyyppi,
  date_trunc('month', umkh.pvm) AS kk,
  SUM(umkh.maara) AS maara
FROM urakka u
  JOIN urakan_materiaalin_kaytto_hoitoluokittain umkh ON u.id = umkh.urakka
  LEFT JOIN LATERAL (select normalisoi_talvihoitoluokka(umkh.talvihoitoluokka::INTEGER, umkh.pvm) AS hoitoluokka) hl ON TRUE
  JOIN materiaalikoodi mk ON mk.id = umkh.materiaalikoodi
WHERE (:urakka::INTEGER IS NULL OR u.id = :urakka)
      AND (:hallintayksikko::INTEGER IS NULL OR u.hallintayksikko = :hallintayksikko)
      AND (umkh.pvm::DATE BETWEEN :alkupvm AND :loppupvm)
      -- Murskeille ja jätteille ei näytetä hoitoluokkakohtaista luokittelua
      AND mk.nimi NOT IN ('Murskeet', 'Jätteet kaatopaikalle')
      AND (:urakkatyyppi::urakkatyyppi IS NULL OR
           CASE
               WHEN (:urakkatyyppi::urakkatyyppi = 'hoito' OR :urakkatyyppi::urakkatyyppi = 'teiden-hoito') THEN
                       u.tyyppi IN ('hoito', 'teiden-hoito')
               ELSE
                       u.tyyppi = :urakkatyyppi::urakkatyyppi
               END)
      AND mk.materiaalityyppi != 'erityisalue'
GROUP BY u.id, u.nimi, mk.id, mk.nimi, mk.materiaalityyppi, date_trunc('month', umkh.pvm), hl.hoitoluokka, umkh.soratiehoitoluokka
UNION
-- Liitä lopuksi mukaan suunnittelutiedot. Kuukausi on null, josta myöhemmin
-- rivi tunnistetaan suunnittelutiedoksi.
SELECT
  u.id as urakka_id, u.nimi as urakka_nimi,
  NULL as luokka,
  NULL AS soratiehoitoluokka,
  mk.id as materiaali_id, mk.nimi as materiaali_nimi,
  mk.yksikko AS materiaali_yksikko,
  mk.materiaalityyppi AS materiaali_tyyppi,
  NULL as kk,
  SUM(s.maara) as maara
FROM materiaalin_kaytto s
  JOIN materiaalikoodi mk ON s.materiaali = mk.id
  JOIN urakka u ON s.urakka = u.id AND u.urakkanro IS NOT NULL
WHERE s.poistettu IS NOT TRUE
      AND (s.alkupvm, s.loppupvm) OVERLAPS (:alkupvm, :loppupvm)
      AND (:urakka::integer IS NULL OR s.urakka = :urakka)
      AND (:hallintayksikko::integer IS NULL OR u.hallintayksikko = :hallintayksikko)
      AND (:urakkatyyppi::urakkatyyppi IS NULL OR
           CASE
               WHEN (:urakkatyyppi::urakkatyyppi = 'hoito' OR :urakkatyyppi::urakkatyyppi = 'teiden-hoito') THEN
                       u.tyyppi IN ('hoito', 'teiden-hoito')
               ELSE
                       u.tyyppi = :urakkatyyppi::urakkatyyppi
               END)
      AND mk.materiaalityyppi != 'erityisalue'
GROUP BY u.id, u.nimi, mk.id, mk.nimi, mk.yksikko, mk.materiaalityyppi
UNION
-- Liitä myös tehtävät ja määrät sivun suunnittelutiedot MHU urakoiden osalta.
-- toimenpidekoodit on mäpätty materiaaleihin erikseen materiaaliluokan ja materiaalikoodin avulla
-- Ja jätä suolauksen suunnitellut määrät ulos, koska ne haetaan taas hieman eri logiikalla
SELECT
    u.id as urakka_id, u.nimi as urakka_nimi,
    NULL as luokka,
    NULL AS soratiehoitoluokka,
    mk.id as materiaali_id,
    coalesce(mk.nimi, ml.nimi) as materiaali_nimi,
    coalesce(mk.yksikko, ml.yksikko) AS materiaali_yksikko,
    coalesce(mk.materiaalityyppi, ml.materiaalityyppi) AS materiaali_tyyppi,
    NULL as kk,
    SUM(ut.maara) as maara
FROM urakka_tehtavamaara ut
         JOIN urakka u ON ut.urakka = u.id AND u.urakkanro IS NOT NULL
         JOIN toimenpidekoodi tk ON ut.tehtava = tk.id AND tk.materiaaliluokka_id IS NOT NULL
         JOIN materiaaliluokka ml ON tk.materiaaliluokka_id = ml.id AND ml.materiaalityyppi != 'talvisuola'
         LEFT JOIN materiaalikoodi mk ON tk.materiaalikoodi_id = mk.id
WHERE ut.poistettu IS NOT TRUE
  -- Hox: ympäristöraportti voidaan hakea kuukaudelle, mutta suunnittelutieto on olemassa vain vuositasolla
  AND ut."hoitokauden-alkuvuosi" = EXTRACT(YEAR from :alkupvm::DATE)
  AND (:urakka::integer IS NULL OR ut.urakka = :urakka)
  AND (:hallintayksikko::integer IS NULL OR u.hallintayksikko = :hallintayksikko)
  -- Rajoitetaan koskemaan pelkästään teiden-hoito (MHU) tyyppisiin urakohin
  AND u.tyyppi = 'teiden-hoito'
GROUP BY u.id, u.nimi, mk.id, mk.nimi, mk.yksikko, mk.materiaalityyppi, ml.nimi, ml.yksikko, ml.materiaalityyppi;

-- name: hae-materiaalit
-- Hakee materiaali id:t ja nimet
-- Huomaa, että tämän kyselyn pitää palauttaa samat sarakkeet, kuin mitkä ympäristöraportissa haetaan
-- "materiaali_*" nimeen
SELECT id,nimi, yksikko, materiaalityyppi as tyyppi FROM materiaalikoodi
 WHERE materiaalityyppi != 'erityisalue';
