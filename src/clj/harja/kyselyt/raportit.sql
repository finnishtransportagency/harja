-- name: hae-raporttien-suoritustiedot
select * from raporttien_suoritustiedot;

-- name: luo-suoritustieto
insert into 
       raporttien_suoritustiedot (raportti, rooli, konteksti, suorittajan_organisaatio, 
                                  parametrit, aikavali_alkupvm, aikavali_loppupvm, 
                                  urakka_id, hallintayksikko_id) 
       values (:raportti, :rooli, :konteksti, :suorittajan_organisaatio, 
               :parametrit, :aikavali_alkupvm, :aikavali_loppupvm, 
               :urakka_id, :hallintayksikko_id) 
       returning id;

-- name: paivita-suorituksen-kesto
update raporttien_suoritustiedot set suoritus_valmis = :valmisaika;

-- name: paivita_raportti_cachet
SELECT paivita_raportti_cachet();

-- name: paivita_raportti_toteutuneet_materiaalit
SELECT paivita_raportti_toteutuneet_materiaalit();

-- name: paivita_raportti_pohjavesialueiden_suolatoteumat
SELECT paivita_raportti_pohjavesialueiden_suolatoteumat();

-- name: paivita_raportti_toteuma_maarat
SELECT paivita_raportti_toteuma_maarat();
