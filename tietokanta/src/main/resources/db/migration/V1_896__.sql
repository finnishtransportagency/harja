create table raportti_suoritustieto (
       id serial primary key,
       raportti varchar(256) not null,
       rooli varchar(256) not null,
       konteksti varchar(32) not null,
       suorittajan_organisaatio integer references organisaatio(id),
       parametrit jsonb,
       suoritustyyppi varchar(32), -- esim excel, pdf, selain
       suoritus_alkuaika timestamp default current_timestamp,    
       suoritus_valmis timestamp,
       aikavali_alkupvm timestamp, -- hakuparametreista
       aikavali_loppupvm timestamp,
       urakka_id integer references urakka(id),
       hallintayksikko_id integer references organisaatio(id));
       
