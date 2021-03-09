-- jos vahvistamisen jälkeen suunnitelmaa muokataan, tallennetaan selite sille. muokkaus liittyy aina johonkin yksittäiseen riviin, joka voi olla jhk/kiint.tyo/kust.tyo
create table suunnittelu_kustannussuunnitelman_muutos (
id serial primary key,
johto_ja_hallintokorvaus int references johto_ja_hallintokorvaus(id),
kiinteahintainen_tyo int references kiinteahintainen_tyo(id),
kustannusarvioitu_tyo int references kustannusarvioitu_tyo(id),
kuvaus text not null,
selite text not null,
maara numeric not null,
luotu timestamp default now(),
luoja integer references kayttaja(id) not null,
urakka integer references urakka(id) not null,
muokattu timestamp,
muokkaaja integer references kayttaja(id)
);