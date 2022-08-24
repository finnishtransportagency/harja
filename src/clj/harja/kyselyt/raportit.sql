-- name: hae-raporttien-suoritustiedot
SELECT count(raportti),
       raportti
  FROM raportti_suoritustieto
 WHERE suoritus_alkuaika
     BETWEEN :alkupvm AND :loppupvm  AND
     (raportti = :raportti::TEXT OR :raportti::TEXT IS NULL) AND
     (:rooli::TEXT IS NULL OR rooli ILIKE concat('%', :rooli::TEXT, '%')) AND
     (:urakkarooli::TEXT IS NULL OR urakkarooli ILIKE concat('%', :urakkarooli::TEXT, '%')) AND
     (:organisaatiorooli::TEXT IS NULL OR organisaatiorooli ILIKE concat('%', :organisaatiorooli::TEXT, '%')) AND
     (suoritustyyppi = :formaatti::TEXT OR :formaatti::TEXT IS NULL)
 GROUP BY raportti
 ORDER BY count(raportti) DESC;

-- name: luo-suoritustieto<!
insert into 
       raportti_suoritustieto (raportti, rooli, urakkarooli, organisaatiorooli,
                               konteksti, suorittajan_organisaatio,
                               parametrit, aikavali_alkupvm, aikavali_loppupvm,
                               urakka_id, hallintayksikko_id, suoritustyyppi)
       values (:raportti, :rooli, :urakkarooli, :organisaatiorooli,
               :konteksti, :suorittajan_organisaatio,
               :parametrit::jsonb, :aikavali_alkupvm, :aikavali_loppupvm, 
               :urakka_id, :hallintayksikko_id, :suoritustyyppi)
               returning raportti_suoritustieto.id;

-- name: paivita-suorituksen-kesto<!
update raportti_suoritustieto
   set suoritus_valmis = NOW() where id = :id;

-- name: paivita_raportti_cachet
SELECT paivita_raportti_cachet();

-- name: paivita_raportti_toteutuneet_materiaalit
SELECT paivita_raportti_toteutuneet_materiaalit();

-- name: paivita_raportti_pohjavesialueiden_suolatoteumat
SELECT paivita_raportti_pohjavesialueiden_suolatoteumat();

-- name: paivita_raportti_toteuma_maarat
SELECT paivita_raportti_toteuma_maarat();
