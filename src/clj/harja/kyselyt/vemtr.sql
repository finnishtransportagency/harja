
-- name: hae-yh-toteutuneet-tehtavamaarat-ja-toteumat-aikavalilla

-- todo - onko samasta työstä tarkoitus tuottaa sekä toteutuneiden ja suunniteltujen rivit,
-- vai pitäisikö näiden jotenkin sulkea toisiaan pois, vai yhdistetäänkö ne raporttikoodissa?

SELECT tpk4.nimi as nimi,
     NULL ::integer as jarjestys,
     NULL ::numeric as "suunniteltu",
     (select extract(year from alkupvm) from urakka where id = tpi.urakka) as "hoitokauden-alkuvuosi",
     tpk4.suunnitteluyksikko,
     tpk4.yksikko,
     tpk4.id as toimenpidekoodi,
     tpi.urakka as urakka,
     tpi.nimi as toimenpide,
     ROUND(SUM(tt.maara), 2) as toteuma,
     ROUND(SUM(tm.maara), 2) as "toteutunut-materiaalimaara", -- usein tyhjä
     'yh-toteutuneet' as rivityyppi

          FROM toteuma t
                   JOIN toteuma_tehtava tt ON t.id = tt.toteuma AND tt.poistettu = FALSE
                   LEFT JOIN toteuma_materiaali tm
                             ON t.id = tm.toteuma AND tm.poistettu = FALSE,
  toimenpidekoodi tpk4, urakka u, toimenpideinstanssi tpi
  WHERE tpi.toimenpide = tpk4.emo and tpi.urakka = u.id and u.tyyppi = 'hoito' and
        (not u.poistettu) and t.urakka = u.id and tpk4.id = tt.toimenpidekoodi and
	(not tpk4.poistettu) AND
	(t.alkanut, t.paattynyt) OVERLAPS (:alkupvm, :loppupvm)
	and ((:hallintayksikko::integer is null) or (u.hallintayksikko = :hallintayksikko::integer))
  GROUP BY tpk4.id, tpk4.nimi, tpk4.yksikko, tpi.urakka, tpi.nimi


-- name: hae-yh-suunnitellut-tehtavamaarat-ja-toteumat-aikavalilla
SELECT tpk4.nimi as nimi,
         NULL ::integer as jarjestys,
	 SUM(tyo.maara) as "suunniteltu",
  	 (select extract(year from alkupvm) from urakka where id = tyo.urakka) as "hoitokauden-alkuvuosi",
	 tpk4.suunnitteluyksikko,
	 tpk4.yksikko,
	 tpk4.id as toimenpidekoodi,
	 tyo.urakka as urakka,
	 tpi.nimi as toimenpide,
	 null::numeric as "toteuma",
	 null ::numeric as "toteutunut-materiaalimaara",
	 'yh-suunnitellut' as rivityyppi
FROM yksikkohintainen_tyo tyo
  JOIN toimenpidekoodi tpk4 ON tpk4.id = tyo.tehtava,
  urakka u, toimenpideinstanssi tpi
  WHERE tpi.toimenpide = tpk4.emo and tpi.urakka = u.id and
        u.id = tyo.urakka AND u.tyyppi = 'hoito' and (not u.poistettu) and
	(u.alkupvm, u.loppupvm) OVERLAPS (:alkupvm, :loppupvm)
	and ((:hallintayksikko::integer is null) or (u.hallintayksikko = :hallintayksikko::integer))

  GROUP BY tpk4.id, tyo.urakka, tpi.nimi
