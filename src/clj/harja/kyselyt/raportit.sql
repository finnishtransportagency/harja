-- name: hae-raporttien-suoritustiedot
select *, extract(epoch from rs.suoritus_valmis - rs.luotu) as kesto from raportti_suoritustieto;

-- name: luo-suoritustieto<!
insert into 
       raportti_suoritustieto (raportti, rooli, konteksti, suorittajan_organisaatio, 
                                  parametrit, aikavali_alkupvm, aikavali_loppupvm, 
                                  urakka_id, hallintayksikko_id, suoritustyyppi) 
       values (:raportti, :rooli, :konteksti, :suorittajan_organisaatio, 
               :parametrit::jsonb, :aikavali_alkupvm, :aikavali_loppupvm, 
               :urakka_id, :hallintayksikko_id, :suoritustyyppi)
               returning raportti_suoritustieto.id;

-- name: paivita-suorituksen-kesto<!
update raportti_suoritustieto
set suoritus_valmis = :valmispvm where id = :id;

-- name: paivita_raportti_cachet
SELECT paivita_raportti_cachet();

-- name: paivita_raportti_toteutuneet_materiaalit
SELECT paivita_raportti_toteutuneet_materiaalit();

-- name: paivita_raportti_pohjavesialueiden_suolatoteumat
SELECT paivita_raportti_pohjavesialueiden_suolatoteumat();

-- name: paivita_raportti_toteuma_maarat
SELECT paivita_raportti_toteuma_maarat();
