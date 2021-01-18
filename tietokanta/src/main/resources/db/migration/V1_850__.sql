alter table kustannusarvioitu_tyo add column indeksikorjattu numeric;
alter table johto_ja_hallintokorvaus add column indeksikorjattu_palkka numeric;
alter table kiinteahintainen_tyo add column indeksikorjattu_summa numeric;

create type suunnittelu_kategoriat as enum ('hankintakustannukset', 'erillishankinnat', 'johto- ja hallintokorvaus', 'hoidonjohtopalkkio', 'tavoite- ja kattohinta', 'tilaajan vararahastot');

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
vahvistaja integer references kayttaja(id) not null,
vahvistus_pvm timestamp
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