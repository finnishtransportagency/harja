-- name: hae-ymparistoraportti
SELECT *
 FROM (WITH RECURSIVE kuukaudet (kk) AS (
       -- Haetaan kaikki kuukaudet alkupvm-loppupvm välillä
       VALUES (date_trunc('month', :alkupvm::date))
       UNION ALL
       (SELECT date_trunc('month', kk + interval '1 month')
          FROM kuukaudet
         WHERE kk + interval '1 month' < :loppupvm)
     ), hoitoluokat AS (
       --      Is   I    Ib   TIb  II  III  K1   K2
       VALUES (1), (2), (3), (4), (5), (6), (7), (8)
     )
     SELECT -- haetaan käytetyt määrät per materiaali ja kk
            NULL as luokka, kkt.kk, mk.id as materiaali_id, mk.nimi as materiaali_nimi,
            (SELECT SUM(tm.maara)
               FROM toteuma_materiaali tm
     	       JOIN toteuma t ON tm.toteuma=t.id
     	       JOIN urakka u ON t.urakka=u.id	       
              WHERE tm.materiaalikoodi = mk.id
     	   AND (:urakka_annettu is false OR t.urakka = :urakka)
     	   AND (:hal_annettu is false OR u.hallintayksikko = :hal) 
     	   AND date_trunc('month', t.alkanut) = kkt.kk) as maara
       FROM kuukaudet kkt
            CROSS JOIN materiaalikoodi mk
     UNION
     SELECT -- Haetaan reittipisteiden toteumat hoitoluokittain
            hl.column1 as luokka, kkt.kk, mk.id as materiaali_id, mk.nimi as materiaali_nimi,
            (SELECT SUM(rm.maara)
               FROM reitti_materiaali rm
     	       JOIN reittipiste rp1 ON rm.reittipiste=rp1.id
     	       JOIN toteuma t1 ON rp1.toteuma=t1.id
     	       JOIN urakka u1 ON t1.urakka = u1.id
     	 WHERE rm.materiaalikoodi = mk.id
     	   AND rp1.talvihoitoluokka = hl.column1
     	   AND (:urakka_annettu is false OR t1.urakka = :urakka)
     	   AND (:hal_annettu is false OR u1.hallintayksikko = :hal)
     	   AND date_trunc('month', rp1.aika) = kkt.kk) as maara
       FROM kuukaudet kkt
            CROSS JOIN materiaalikoodi mk
            CROSS JOIN hoitoluokat hl 
     UNION 
     SELECT -- Haetaan suunnitelmat materiaaleille
            NULL as luokka,
            NULL as kk, -- tyhjä kk pvm kertoo suunnitelman
            mks.id as materiaali_id, mks.nimi as materiaali_nimi,
            (SELECT SUM(s.maara)
               FROM materiaalin_kaytto s
     	 WHERE s.materiaali = mks.id
     	   AND (s.alkupvm BETWEEN :alkupvm AND :loppupvm
     	        OR
     		s.loppupvm BETWEEN :alkupvm AND :loppupvm)) as maara
       FROM materiaalikoodi mks) AS raportti
 WHERE -- Poistetaan hoitoluokitellut rivit, joilla ei ole määrää
       NOT (luokka IS NOT NULL AND maara IS NULL);


-- name: hae-ymparistoraportti-urakoittain
-- Hakee ympäristöraportin, mutta jokaiselle urakalle on omat rivinsä
SELECT *
  FROM (WITH RECURSIVE kuukaudet (kk) AS (
        -- Haetaan kaikki kuukaudet alkupvm-loppupvm välillä
	 VALUES (date_trunc('month', :alkupvm::date))
       UNION ALL
       (SELECT date_trunc('month', kk + interval '1 month')
          FROM kuukaudet
         WHERE kk + interval '1 month' < :loppupvm)
     ), urakat AS (
       SELECT id, nimi -- rivit kaikille käynnissä
         FROM urakka   -- oleville urakoille
        WHERE (:alkupvm BETWEEN alkupvm AND loppupvm
               OR
     	  :loppupvm BETWEEN alkupvm AND loppupvm)
     	 AND
     	 (:hal_annettu = false OR hallintayksikko = :hal)
     ), hoitoluokat AS (
       --      Is   I    Ib   TIb  II  III  K1   K2
       VALUES (1), (2), (3), (4), (5), (6), (7), (8)
     )
     SELECT -- haetaan käytetyt määrät per materiaali ja kk
            NULL as luokka, kkt.kk, mk.id as materiaali_id, mk.nimi as materiaali_nimi,
            urk.id as urakka_id, urk.nimi as urakka_nimi,
            (SELECT SUM(tm.maara)
               FROM toteuma_materiaali tm
     	       JOIN toteuma t ON tm.toteuma=t.id
              WHERE tm.materiaalikoodi = mk.id
     	   AND t.urakka = urk.id
     	   AND date_trunc('month', t.alkanut) = kkt.kk) as maara
       FROM kuukaudet kkt
            CROSS JOIN materiaalikoodi mk
            CROSS JOIN urakat urk
     UNION
     SELECT -- Haetaan reittipisteiden toteumat hoitoluokittain
            hl.column1 as luokka, kkt.kk, mk.id as materiaali_id, mk.nimi as materiaali_nimi,
            urk.id as urakka_id, urk.nimi as urakka_nimi,
            (SELECT SUM(rm.maara)
               FROM reitti_materiaali rm
     	            JOIN reittipiste rp1 ON rm.reittipiste=rp1.id
     	            JOIN toteuma t1 ON rp1.toteuma=t1.id
     	      WHERE rm.materiaalikoodi = mk.id
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
     	   AND (s.alkupvm BETWEEN :alkupvm AND :loppupvm
     	        OR
     		s.loppupvm BETWEEN :alkupvm AND :loppupvm)) as maara
       FROM materiaalikoodi mks
            CROSS JOIN urakat urk) AS raportti
 WHERE -- poistetaan hoitoluokitellut rivit, joilla ei ole määrää
       NOT (luokka IS NOT NULL AND maara IS NULL)
       ORDER BY materiaali_nimi;


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
