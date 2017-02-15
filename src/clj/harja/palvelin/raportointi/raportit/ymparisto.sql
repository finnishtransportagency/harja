-- name: hae-ymparistoraportti-tiedot
SELECT
  u.id AS urakka_id,
  u.nimi AS urakka_nimi,
  umkh.talvihoitoluokka AS luokka,
  mk.id AS materiaali_id,
  mk.nimi AS materiaali_nimi,
  mk.yksikko AS materiaali_yksikko,
  date_trunc('month', umkh.pvm) AS kk,
  umkh.maara AS maara
FROM urakka u
  JOIN urakan_materiaalin_kaytto_hoitoluokittain umkh ON u.id = umkh.urakka
  JOIN materiaalikoodi mk ON mk.id = umkh.materiaalikoodi
WHERE (:urakka::INTEGER IS NULL OR u.id = :urakka)
      AND (:hallintayksikko::INTEGER IS NULL OR u.hallintayksikko = :hallintayksikko)
      AND (umkh.pvm::DATE BETWEEN :alkupvm AND :loppupvm)
      AND (:urakkatyyppi::urakkatyyppi IS NULL OR u.tyyppi = :urakkatyyppi::urakkatyyppi)
UNION
SELECT
  u.id AS urakka_id,
  u.nimi AS urakka_nimi,
  NULL AS luokka,
  mk.id AS materiaali_id,
  mk.nimi AS materiaali_nimi,
  mk.yksikko AS materiaali_yksikko,
  date_trunc('month', t.alkanut) AS kk,
  SUM(tm.maara)
FROM toteuma t
  JOIN toteuma_materiaali tm ON t.id = tm.toteuma
  JOIN urakka u ON t.urakka = u.id AND u.urakkanro IS NOT NULL
  JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
WHERE (t.alkanut :: DATE BETWEEN :alkupvm AND :loppupvm)
      AND t.poistettu IS NOT TRUE
      AND tm.poistettu IS NOT TRUE
      AND (:urakka::integer IS NULL OR u.id = :urakka)
      AND (:hallintayksikko::integer IS NULL OR u.hallintayksikko = :hallintayksikko)
      AND (:urakkatyyppi::urakkatyyppi IS NULL OR u.tyyppi = :urakkatyyppi::urakkatyyppi)
GROUP BY u.id, u.nimi, mk.id, mk.nimi, date_trunc('month', t.alkanut), mk.yksikko
UNION
SELECT
  u.id as urakka_id, u.nimi as urakka_nimi,
  NULL as luokka,
  mk.id as materiaali_id, mk.nimi as materiaali_nimi,
  mk.yksikko AS materiaali_yksikko,
  NULL as kk,
  SUM(s.maara) as maara
FROM materiaalin_kaytto s
  JOIN materiaalikoodi mk ON s.materiaali = mk.id
  JOIN urakka u ON s.urakka = u.id AND u.urakkanro IS NOT NULL
WHERE s.poistettu IS NOT TRUE
      AND (s.alkupvm, s.loppupvm) OVERLAPS (:alkupvm, :loppupvm)
      AND (:urakka::integer IS NULL OR s.urakka = :urakka)
      AND (:hallintayksikko::integer IS NULL OR u.hallintayksikko = :hallintayksikko)
      AND (:urakkatyyppi::urakkatyyppi IS NULL OR u.tyyppi = :urakkatyyppi::urakkatyyppi)
GROUP BY u.id, u.nimi, mk.id, mk.nimi, mk.yksikko;

-- name: hae-materiaalit
-- Hakee materiaali id:t ja nimet
SELECT id,nimi FROM materiaalikoodi


-- name: testi
WITH RECURSIVE kuukaudet (kk) AS (
  -- Haetaan kaikki kuukaudet alkupvm-loppupvm välillä
  VALUES (date_trunc('month', '2005-10-01'::date))
  UNION ALL
  (SELECT date_trunc('month', kk + interval '1 month')
     FROM kuukaudet
    WHERE kk + interval '1 month' < '2006-09-30')
), urakat AS (
  SELECT id, nimi -- rivit kaikille käynnissä
    FROM urakka   -- oleville urakoille
   WHERE (alkupvm BETWEEN '2005-10-01' AND '2006-09-30'
          OR
	  loppupvm BETWEEN '2005-10-01' AND '2006-09-30')
	 AND
	 hallintayksikko = 9
), hoitoluokat AS (
  --      Is, I, Ib, TIb, II,III, K1, K2
  VALUES (1),(2),(3),(4),(5),(6),(7),(8)
)
SELECT -- haetaan käytetyt määrät per materiaali ja kk
       NULL as luokka, kkt.kk, mk.id as materiaali_id, mk.nimi as materiaali_nimi,
       urk.id as urakka_id, urk.nimi as urakka_nimi,
       (SELECT SUM(tm.maara)
          FROM toteuma_materiaali tm
	       JOIN toteuma t ON tm.toteuma=t.id
         WHERE tm.materiaalikoodi = mk.id
     AND t.poistettu IS NOT TRUE
	   AND t.urakka = urk.id
	   AND date_trunc('month', t.alkanut) = kkt.kk) as maara
  FROM kuukaudet kkt
       CROSS JOIN materiaalikoodi mk
       CROSS JOIN urakat urk
UNION -- haetaan reittipisteiden toteumat luokiteltuna
SELECT hl.column1 as luokka, kkt.kk, mk.id as materiaali_id, mk.nimi as materiaali_nimi,
       urk.id as urakka_id, urk.nimi as urakka_nimi,
       (SELECT SUM(rm.maara)
          FROM reitti_materiaali rm
	       JOIN reittipiste rp1 ON rm.reittipiste=rp1.id
	       JOIN toteuma t1 ON rp1.toteuma=t1.id
	 WHERE rm.materiaalikoodi = mk.id
	   AND t1.poistettu IS NOT TRUE
	   AND rp1.talvihoitoluokka = hl.column1
	   AND t1.urakka = urk.id
	   AND date_trunc('month', rp1.aika) = kkt.kk) as maara
  FROM kuukaudet kkt
       CROSS JOIN materiaalikoodi mk
       CROSS JOIN urakat urk
       CROSS JOIN hoitoluokat hl
UNION
SELECT -- Haetaan suunnitelmat materiaaleille
       NULL as luokka,
       NULL as kk, -- tyhjä kk pvm kertoo suunnitelman
       mks.id as materiaali_id, mks.nimi as materiaali_nimi,
       urk.id as urakka_id, urk.nimi as urakka_nimi,
       (SELECT SUM(s.maara)
          FROM materiaalin_kaytto s
	 WHERE s.materiaali = mks.id
	   AND s.urakka = urk.id
	   AND (s.alkupvm BETWEEN '2005-10-01' AND '2006-09-30'
	        OR
		s.loppupvm BETWEEN '2005-10-01' AND '2006-09-30')) as maara
  FROM materiaalikoodi mks
       CROSS JOIN urakat urk;
