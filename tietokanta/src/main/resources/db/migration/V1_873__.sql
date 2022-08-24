-- toteutuneet_kustannukset tauluun ei siirretty Hoidonjohtopalkkio -tehtävää kustannusarvioitu_tyo -taulusta
-- Päivitetään funktio, jolla siirretään tiedot kustannusarvioitu_tyo ja johto_ja_hallintakorvaus tauluista toteutuneet_kustannukset tauluun
CREATE OR REPLACE FUNCTION siirra_budjetoidut_tyot_toteumiin() RETURNS VOID AS
$$
BEGIN
    -- Automaattisesti toteumaksi lasketaan tehtäväryhmä: Erillishankinnat (W)
    -- Ja tehtävät: Toimistotarvike- ja ICT-kulut, Hoitourakan työnjohto sekä Hoidonjohtopalkkio
    INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                          sopimus_id, urakka_id, luotu, rivin_tunnistin)
    SELECT k.vuosi,
           k.kuukausi,
           k.summa,
           k.tyyppi,
           k.tehtava,
           k.tehtavaryhma,
           k.toimenpideinstanssi,
           k.sopimus,
           (select s.urakka FROM sopimus s where s.id = k.sopimus) as "urakka-id",
           NOW(),
           MD5(concat(k.id, k.vuosi, k.kuukausi, k.summa, k.tyyppi, k.tehtava, k.tehtavaryhma,
                      k.toimenpideinstanssi, k.sopimus, k.luotu, k.luoja, k.muokattu, k.muokkaaja)::TEXT)
    FROM kustannusarvioitu_tyo k
    -- Siirretään vain edellisen kuukauden jutut. Ei siis kuluvan kuukauden hommia
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) < date_trunc('month', current_date)
      -- Siirretään vain ne, joita ei ole vielä siirretty
      AND k."siirretty?" = false
      AND (k.tehtavaryhma = (SELECT id FROM tehtavaryhma
                             WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c') -- Erillishankinnat (W)
            OR k.tehtava in (SELECT id FROM toimenpidekoodi t WHERE t.yksiloiva_tunniste IN
                               ('8376d9c4-3daf-4815-973d-cd95ca3bb388', -- "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                                'c9712637-fbec-4fbd-ac13-620b5619c744', -- Hoitourakan työnjohto
                                '53647ad8-0632-4dd3-8302-8dfae09908c8'))) -- Hoidonjohtopalkkio
    ON CONFLICT DO NOTHING;

    -- Tästä taulusta (johto_ja_hallintokorvaus) siirretään kaikki rivit
    INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                          sopimus_id, urakka_id, luotu, rivin_tunnistin)
    SELECT j.vuosi,
           j.kuukausi,
           (j.tunnit * j.tuntipalkka * j."osa-kuukaudesta") AS summa,
           'laskutettava-tyo'                               AS tyyppi,
           null                                             as tehtava,
           (SELECT id FROM tehtavaryhma WHERE nimi = 'Johto- ja hallintokorvaus (J)'),
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
            ORDER BY s.loppupvm DESC
            limit 1),
           j."urakka-id",
           NOW(),
           MD5(concat(j.id, j."urakka-id", j."toimenkuva-id", j.tunnit, j.tuntipalkka,
                      j.luotu, j.luoja, j.muokattu, j.muokkaaja, j.vuosi, j.kuukausi,
                      j."ennen-urakkaa", j."osa-kuukaudesta")::TEXT)
    FROM johto_ja_hallintokorvaus j
    -- Siirretään vain edellisen kuukauden jutut
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', j.vuosi, j.kuukausi, 1)::DATE))) < date_trunc('month', current_date)
      -- Ja ne, joita ei ole vielä siirretty
      AND j."siirretty?" = false
    ON CONFLICT DO NOTHING;

    -- Päivitetään kaikkiin juuri siirrettyihin riveihin tieto, että ne on käsitelty ja siirrety
    UPDATE kustannusarvioitu_tyo k SET "siirretty?" = true
    -- Päivitetään vain edellisen kuukauden jutut
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) < date_trunc('month', current_date)
      -- Ja vain ne, joita ei ole aiemin päivitetty siirretyksi
      AND k."siirretty?" = false
      AND (k.tehtavaryhma =
           (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c')
            OR
            k.tehtava in (SELECT id FROM toimenpidekoodi t WHERE t.yksiloiva_tunniste IN
                            ('8376d9c4-3daf-4815-973d-cd95ca3bb388', -- "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                             'c9712637-fbec-4fbd-ac13-620b5619c744', -- Hoitourakan työnjohto
                             '53647ad8-0632-4dd3-8302-8dfae09908c8'))); -- Hoidonjohtopalkkio

    -- Päivitetään kaikkiin juuri siirrettyihin riveihin tieto, että ne on käsitelty ja siirrety
    UPDATE johto_ja_hallintokorvaus j
    SET "siirretty?" = true
    -- Päivitetään vain edellisen kuukauden jutut
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', j.vuosi, j.kuukausi, 1)::DATE))) < date_trunc('month', current_date)
      -- Ja vain ne, joita ei ole aiemmin päivitetty siirrettäväksi
      AND j."siirretty?" = false;

END;
$$ LANGUAGE plpgsql;

-- Siirretään Hoindonjohtopalkkio rivit toteutuneet_kustannukset tauluun ja merkataan ne siirretyksi
CREATE OR REPLACE FUNCTION temp_korjaa_kustannukset() RETURNS VOID AS
$$
BEGIN
    -- Siirretään ne Hoidonjohtopalkkio -tehtävät, joita ei ole siirretty
    INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                          sopimus_id, urakka_id, luotu, rivin_tunnistin)
    SELECT k.vuosi,
           k.kuukausi,
           k.summa,
           k.tyyppi,
           k.tehtava,
           k.tehtavaryhma,
           k.toimenpideinstanssi,
           k.sopimus,
           (select s.urakka FROM sopimus s where s.id = k.sopimus) as "urakka-id",
           NOW(),
           MD5(concat(k.id, k.vuosi, k.kuukausi, k.summa, k.tyyppi, k.tehtava, k.tehtavaryhma,
                      k.toimenpideinstanssi, k.sopimus, k.luotu, k.luoja, k.muokattu, k.muokkaaja)::TEXT)
    FROM kustannusarvioitu_tyo k
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) < date_trunc('month', current_date)
      AND k."siirretty?" = false
      AND k.tehtava in (SELECT id FROM toimenpidekoodi t
                         WHERE t.yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8') -- Hoidonjohtopalkkio
    ON CONFLICT DO NOTHING;

    -- Ja päivitetään kustannusarvioitu_tyo tauluun niiden tilaksi siirretty? = true
    UPDATE kustannusarvioitu_tyo k SET "siirretty?" = true
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) < date_trunc('month', current_date)
      AND k."siirretty?" = false
      AND k.tehtava in (SELECT id FROM toimenpidekoodi tpk
                         WHERE tpk.yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8'); -- Hoidonjohtopalkkio
END ;
$$ LANGUAGE plpgsql;

-- Ajetaan yllä tehty funktio, joka poistaa virheelliset rivit
select temp_korjaa_kustannukset();
