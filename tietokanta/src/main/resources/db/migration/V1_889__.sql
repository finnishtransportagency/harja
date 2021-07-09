alter table kustannusarvioitu_tyo
drop constraint "kustannusarvioitu_tyo_toimenpideinstanssi_tehtava_sopimus_v_key";

alter table johto_ja_hallintokorvaus
drop constraint "uniikki_johto_ja_hallintokorvaus";

alter table kiinteahintainen_tyo
drop constraint "kiinteahintainen_tyo_toimenpideinstanssi_tehtavaryhma_tehta_key";

-- indeksikorjattu hinta tallennetaan omana rivinään ja tätä merkkaa boolean
-- versio on juokseva numero, joka lähtee nollasta. jos kustannussuunnitelmaa muokataan jälkeenpäin, nostetaan versionumeroa tietoja tallennettaessa. näin saadaan sitten muodostettua historiatiedot.
alter table kustannusarvioitu_tyo
add column indeksikorjattu boolean not null default false,
add column versio numeric not null default 0,
add constraint uniikki_kustannusarvioitu_tyo unique (toimenpideinstanssi, tehtava, sopimus, vuosi, kuukausi, versio, indeksikorjattu);

alter table johto_ja_hallintokorvaus
add column indeksikorjattu boolean not null default false,
add column versio numeric not null default 0,
ADD CONSTRAINT uniikki_johto_ja_hallintokorvaus EXCLUDE ("urakka-id" WITH =, "toimenkuva-id" WITH =, vuosi WITH =, kuukausi WITH =, indeksikorjattu WITH =, versio WITH =, ei_ennen_urakka("ennen-urakkaa", id) WITH =);

alter table kiinteahintainen_tyo
add column indeksikorjattu boolean not null default false,
add column versio numeric not null default 0,
add constraint uniikki_kiinteahintainen_tyo unique (toimenpideinstanssi, tehtavaryhma, tehtava, sopimus, vuosi, kuukausi, versio, indeksikorjattu);

create type suunnittelu_osio as enum ('hankintakustannukset', 'erillishankinnat', 'johto-ja-hallintokorvaus', 'hoidonjohtopalkkio', 'tavoite-ja-kattohinta', 'tilaajan-rahavaraukset');

create table suunnittelu_kustannussuunnitelman_tila (
id serial primary key,
urakka integer references urakka(id) not null,
osio suunnittelu_osio,
hoitovuosi integer not null,
vahvistettu boolean not null default false,
luoja integer references kayttaja(id) not null,
luotu timestamp default now(),
muokattu timestamp,
muokkaaja integer references kayttaja(id),
vahvistaja integer references kayttaja(id),
vahvistus_pvm timestamp,
constraint urakka_osio_hoitovuosi unique (urakka, osio, hoitovuosi)
);

-- jos vahvistamisen jälkeen suunnitelmaa muokataan, tallennetaan selite sille. muokkaus liittyy aina johonkin yksittäiseen riviin, joka voi olla jhk/kiint.tyo/kust.tyo
create table suunnittelu_kustannussuunnitelman_muutos (
id serial primary key,
johto_ja_hallintokorvaus int references johto_ja_hallintokorvaus(id),
kiinteahintainen_tyo int references kiinteahintainen_tyo(id),
kustannusarvioitu_tyo int references kustannusarvioitu_tyo(id),
kuvaus text not null,
selite text not null,
muutos numeric not null,
vuosi numeric not null,
luotu timestamp default now(),
luoja integer references kayttaja(id) not null,
urakka integer references urakka(id) not null
);