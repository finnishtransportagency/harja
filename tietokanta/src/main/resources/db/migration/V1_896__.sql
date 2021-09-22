create table raporttien_suoritustiedot (
       id serial primary key,
       raportti varchar(256) not null,
       rooli varchar(256) not null,
       konteksti varchar(32) not null,
       suorittajan_organisaatio integer references organisaatio(id),
       parametrit jsonb,
       suoritus_valmis timestamp,
       aikavali_alkupvm timestamp,
       aikavali_loppupvm timestamp,
       urakka_id integer references urakka(id),
       hallintayksikko_id integer references organisaatio(id),
       luotu timestamp default current_timestamp);
       
