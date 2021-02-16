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