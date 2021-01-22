-- toteutuneet_kustannukset tauluun siirretään kustannuksia, yksinkertaistamaan toteutuneiden kustannusten seurantaa

CREATE TABLE toteutuneet_kustannukset
(
    id                  serial primary key, -- sisäinen ID
    vuosi               smallint not null CHECK (1900 < vuosi AND vuosi < 2200),
    kuukausi            smallint not null CHECK (13 > kuukausi AND kuukausi > 0),
    summa               numeric,
    tyyppi              toteumatyyppi,
    tehtava             integer REFERENCES toimenpidekoodi (id),
    tehtavaryhma        integer REFERENCES tehtavaryhma (id),
    toimenpideinstanssi integer  not null REFERENCES toimenpideinstanssi (id),
    sopimus_id          integer REFERENCES sopimus (id),
    urakka_id           integer REFERENCES urakka (id),
    luotu               timestamp,
    muokattu            timestamp,
    muokkaaja           integer references kayttaja (id),
    unique (toimenpideinstanssi, tehtava, sopimus_id, urakka_id, vuosi, kuukausi)
);

create index toteutuneet_kustannukset_urakka_index
    on toteutuneet_kustannukset (urakka_id, vuosi, kuukausi);


COMMENT ON table toteutuneet_kustannukset IS
    E'Kustannusarvioitu_tyo taulusta siirretään kuukauden viimeisenä päivänä mikäli tehtäväryhmä: Erillishankinnat (W)
      tai tehtävät: Toimistotarvike- ja ICT-kulut, tiedotus, ja Hoitourakan työnjohto
      tähän toteutuneet_kustannukset tauluun, josta voidaan hakea toteutuneet kustannukset raportteihin ja kustannuksiin.';

-- Siirrä kustannusarvoitu_tyo taulusta mikäli tehtäväryhmä: Erillishankinnat (W) tai tehtävät: Toimistotarvike- ja ICT-kulut, tiedotus, ja Hoitourakan työnjohto
INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus_id,
                            urakka_id, luotu)
SELECT k.vuosi,
       k.kuukausi,
       k.summa,
       k.tyyppi,
       k.tehtava,
       k.tehtavaryhma,
       k.toimenpideinstanssi,
       k.sopimus,
       (select s.urakka FROM sopimus s where s.id = k.sopimus) as "urakka-id",
       NOW()
FROM kustannusarvioitu_tyo k
WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) <
      date_trunc('month', current_date)
  AND (k.tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c')
    OR k.tehtava in (SELECT id
                     FROM toimenpidekoodi t
                     WHERE t.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' -- "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                        OR t.yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744') -- Hoitourakan työnjohto
    );

-- Siirretään kaikki olemassaolevat rivit johto_ja_hallintakorvaus taulusta toteutuneet_kustannukset tauluun
do
$$
    declare
        tehtavaryhma_id integer := (SELECT id
                                    FROM tehtavaryhma
                                    WHERE nimi = 'Johto- ja hallintokorvaus (J)');

    BEGIN

        INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                    sopimus_id, urakka_id, luotu)
        SELECT j.vuosi,
               j.kuukausi,
               (j.tunnit * j.tuntipalkka) AS summa,
               'laskutettava-tyo' AS tyyppi,
               null as tehtava,
               tehtavaryhma_id,
               (SELECT tpi.id AS id
                FROM toimenpideinstanssi tpi
                         JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                         JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
                     maksuera m
                WHERE tpi.urakka = j."urakka-id"
                  AND m.toimenpideinstanssi = tpi.id
                  AND tpk2.koodi = '23150'),
               (SELECT id
                FROM sopimus s
                WHERE s.urakka = j."urakka-id"
                  AND s.poistettu IS NOT TRUE
                ORDER BY s.loppupvm DESC limit 1),
               j."urakka-id",
               NOW()
        FROM johto_ja_hallintokorvaus j
        WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', j.vuosi, j.kuukausi, 1)::DATE))) <
              date_trunc('month', current_date);
    END
$$;
