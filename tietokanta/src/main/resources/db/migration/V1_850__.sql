alter table kustannusarvioitu_tyo add column indeksikorjattu numeric;
alter table johto_ja_hallintokorvaus add column indeksikorjattu_palkka numeric;
alter table kiinteahintainen_tyo add column indeksikorjattu_summa numeric;

--- (def ^{:private true} tallennettava-asia->tehtava
  -- {:hoidonjohtopalkkio                         "c9712637-fbec-4fbd-ac13-620b5619c744"
   -- :toimistokulut                              "8376d9c4-3daf-4815-973d-cd95ca3bb388"
   --:kolmansien-osapuolten-aiheuttamat-vahingot {:talvihoito               "49b7388b-419c-47fa-9b1b-3797f1fab21d"
  --                                              :liikenneympariston-hoito "63a2585b-5597-43ea-945c-1b25b16a06e2"
--                                                :sorateiden-hoito         "b3a7a210-4ba6-4555-905c-fef7308dc5ec"}
--   :akilliset-hoitotyot                        {:talvihoito               "1f12fe16-375e-49bf-9a95-4560326ce6cf"
--                                                :liikenneympariston-hoito "1ed5d0bb-13c7-4f52-91ee-5051bb0fd974"
--                                                :sorateiden-hoito         "d373c08b-32eb-4ac2-b817-04106b862fb1"}})
--
-- (def ^{:private true} tallennettava-asia->tehtavaryhma
--  {:erillishankinnat        "37d3752c-9951-47ad-a463-c1704cf22f4c"
--   :rahavaraus-lupaukseen-1 "0e78b556-74ee-437f-ac67-7a03381c64f6"
--   :tilaajan-varaukset "a6614475-1950-4a61-82c6-fda0fd19bb54"})

create type suunnittelu_kategoriat as enum ('hankintakustannukset', 'erillishankinnat', 'johto-ja-hallintokorvaus', 'hoidonjohtopalkkio', 'tavoite-ja-kattohinta', 'tilaajan-vararahastot');

create table suunnittelu_kustannussuunnitelman_tila (
id serial primary key,
urakka integer references urakka(id) not null,
kategoria suunnittelu_kategoriat,
hoitovuosi integer not null,
vahvistettu boolean not null default false,
luoja integer references kayttaja(id) not null,
luotu timestamp default now(),
muokattu timestamp,
muokkaaja integer references kayttaja(id),
vahvistaja integer references kayttaja(id),
vahvistus_pvm timestamp,
constraint urakka_kategoria_hoitovuosi unique (urakka, kategoria, hoitovuosi)
);

create table suunnittelu_kustannussuunnitelman_muutos (
id serial primary key,
toimenpideinstanssi integer references toimenpideinstanssi(id),
hoitovuosi integer not null,
kuvaus text not null,
maara numeric not null,
luotu timestamp default now(),
luoja integer references kayttaja(id) not null,
urakka integer references urakka(id) not null,
muokattu timestamp,
muokkaaja integer references kayttaja(id)
);