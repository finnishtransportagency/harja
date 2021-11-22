-- Lisätään toteutuneet_kustannukset tauluun tieto indeksikorjatusta summasta sekä ajanhetkestä, jolloin se on vahvistettu
ALTER TABLE toteutuneet_kustannukset
    ADD COLUMN summa_indeksikorjattu      NUMERIC,
    ADD COLUMN indeksikorjaus_vahvistettu TIMESTAMP;


-- Lisätään indeksikorjatut summat toteutuneet_kustannukset tauluun, kun tämä funktio ajetaan.
CREATE OR REPLACE FUNCTION siirra_budjetoidut_tyot_toteumiin(pvm DATE) RETURNS VOID AS
$$
BEGIN
    -- Automaattisesti toteumaksi lasketaan tehtäväryhmä: Erillishankinnat (W)
    -- Ja tehtävät: Toimistotarvike- ja ICT-kulut, Hoitourakan työnjohto sekä Hoidonjohtopalkkio
    INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, summa_indeksikorjattu, indeksikorjaus_vahvistettu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                          sopimus_id, urakka_id, luotu, rivin_tunnistin)
    SELECT k.vuosi,
           k.kuukausi,
           k.summa,
           k.summa_indeksikorjattu,
           k.indeksikorjaus_vahvistettu,
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
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) < date_trunc('month', pvm)
      -- Siirretään vain ne, joita ei ole vielä siirretty
      AND k."siirretty?" = false
      -- Siirretään vain indeksikorjauksen vahvistetut asiat
      AND k.indeksikorjaus_vahvistettu IS NOT NULL
      AND k.summa_indeksikorjattu IS NOT NULL
      -- Siirretään vain tietyn tehtäväryhmän tehtäviä tai tietyn toimenpidekoodin tehtäviä
      AND (k.tehtavaryhma = (SELECT id FROM tehtavaryhma
                             WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c') -- Erillishankinnat (W)
        OR k.tehtava in (SELECT id FROM toimenpidekoodi t WHERE t.yksiloiva_tunniste IN
                                                                ('8376d9c4-3daf-4815-973d-cd95ca3bb388', -- "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                                                                 'c9712637-fbec-4fbd-ac13-620b5619c744', -- Hoitourakan työnjohto
                                                                 '53647ad8-0632-4dd3-8302-8dfae09908c8'))) -- Hoidonjohtopalkkio
    ON CONFLICT DO NOTHING;

    -- Tästä taulusta (johto_ja_hallintokorvaus) siirretään kaikki rivit
    INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, summa_indeksikorjattu, indeksikorjaus_vahvistettu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                          sopimus_id, urakka_id, luotu, rivin_tunnistin)
    SELECT j.vuosi,
           j.kuukausi,
           (j.tunnit * j.tuntipalkka * j."osa-kuukaudesta") AS summa,
           (j.tunnit * j.tuntipalkka_indeksikorjattu * j."osa-kuukaudesta") AS summa_indeksikorjattu,
           j.indeksikorjaus_vahvistettu                     AS indeksikorjaus_vahvistettu,
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
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', j.vuosi, j.kuukausi, 1)::DATE))) < date_trunc('month', pvm)
      -- Ja ne, joita ei ole vielä siirretty
      AND j."siirretty?" = false
    -- Ja jotka on indeksikorjauksen osalta vahvistettu
    AND j.indeksikorjaus_vahvistettu IS NOT NULL
    ON CONFLICT DO NOTHING;

    -- Päivitetään kaikkiin juuri siirrettyihin riveihin tieto, että ne on käsitelty ja siirretty
    UPDATE kustannusarvioitu_tyo k SET "siirretty?" = true
    -- Päivitetään vain edellisen kuukauden jutut
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) < date_trunc('month', pvm)
      -- Ja vain ne, joita ei ole aiemin päivitetty siirretyksi
      AND k."siirretty?" = false
      -- Päivitetään vain indeksikorjauksen vahvistetut asiat
      AND k.indeksikorjaus_vahvistettu IS NOT NULL
      AND k.summa_indeksikorjattu IS NOT NULL
      -- Päivitetään vain tietyn tehtäväryhmän tehtäviä tai tietyn toimenpidekoodin tehtäviä
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
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', j.vuosi, j.kuukausi, 1)::DATE))) < date_trunc('month', pvm)
      -- Ja vain ne, joita ei ole aiemmin päivitetty siirrettäväksi
      AND j."siirretty?" = false
      -- Ja jotka on indeksikorjauksen osalta vahvistettu
      AND j.indeksikorjaus_vahvistettu IS NOT NULL;

    -- Merkitään hoidon johdon maksuerä likaiseksi kaikissa voimassaolevissa MH-urakoissa
    UPDATE maksuera
    SET likainen = TRUE,
        muokattu = current_timestamp
    WHERE toimenpideinstanssi IN
          (select tpi.id
           from toimenpideinstanssi tpi
                    join toimenpidekoodi tpk on tpi.toimenpide = tpk.id and tpk.koodi = '23151' -- 'MHU ja HJU Hoidon johto'
                    join urakka u on tpi.urakka = u.id and u.tyyppi = 'teiden-hoito'
                                        -- Maksueriä voi lähettää Sampoon vielä 3 kk urakan päättymisen jälkeen.
                                         and (u.loppupvm + interval '1 month' * 3) > pvm);



END;
$$ LANGUAGE plpgsql;


-- Päivitetään kaikki jo siirretytkin luvut uudestaan toteutuneet_kustannukset tauluun, koska niille ei ole
-- aiemmin laskettu indeksikorjattua summaa
-- Lisätään indeksikorjatut summat toteutuneet_kustannukset tauluun kokonaisuudessaan uudestaan
-- Tätä funktiota ei saa ajaa kuin kerran. Sen ajaminen vie n. 5 sekuntia
CREATE OR REPLACE FUNCTION siirra_budjetoidut_tyot_toteumiin_uudestaan(pvm DATE) RETURNS VOID AS
$$
BEGIN
    raise notice 'Ennen ekaa inserttiä';
    INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, summa_indeksikorjattu, indeksikorjaus_vahvistettu,
                                          tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                          sopimus_id, urakka_id, luotu, rivin_tunnistin)
    SELECT k.vuosi,
           k.kuukausi,
           k.summa,
           k.summa_indeksikorjattu as si,
           k.indeksikorjaus_vahvistettu,
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
         -- Siirretään edelleen vain kaikki menneet (alkaen edellisestä kuukaudesta)
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) < date_trunc('month', pvm)
      -- Nyt ei kiinnitetä huomiota siihen, onko rivi jo siirretty
      -- AND k."siirretty?" = false
      -- Siirretään vain indeksikorjauksen vahvistetut asiat
      AND k.indeksikorjaus_vahvistettu IS NOT NULL
      AND k.summa_indeksikorjattu IS NOT NULL
      -- Siirretään vain tietyn tehtäväryhmän tehtäviä tai tietyn toimenpidekoodin tehtäviä
      -- Automaattisesti toteumaksi lasketaan tehtäväryhmä: Erillishankinnat (W)
      -- Ja tehtävät: Toimistotarvike- ja ICT-kulut, Hoitourakan työnjohto sekä Hoidonjohtopalkkio
      AND (k.tehtavaryhma = (SELECT id FROM tehtavaryhma
                             WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c') -- Erillishankinnat (W)
        OR k.tehtava in (SELECT id FROM toimenpidekoodi t WHERE t.yksiloiva_tunniste IN
                                                                ('8376d9c4-3daf-4815-973d-cd95ca3bb388', -- "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                                                                 'c9712637-fbec-4fbd-ac13-620b5619c744', -- Hoitourakan työnjohto
                                                                 '53647ad8-0632-4dd3-8302-8dfae09908c8'))) -- Hoidonjohtopalkkio
    ON CONFLICT (rivin_tunnistin)
        DO UPDATE SET summa_indeksikorjattu = EXCLUDED.summa_indeksikorjattu,
                      indeksikorjaus_vahvistettu = EXCLUDED.indeksikorjaus_vahvistettu, muokattu = NOW();

    raise notice 'Ennen tokaa inserttiä';

    -- Tästä taulusta (johto_ja_hallintokorvaus) siirretään kaikki rivit
    INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, summa_indeksikorjattu, indeksikorjaus_vahvistettu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                          sopimus_id, urakka_id, luotu, rivin_tunnistin)
    SELECT j.vuosi,
           j.kuukausi,
           (j.tunnit * j.tuntipalkka * j."osa-kuukaudesta") AS summa,
           (j.tunnit * j.tuntipalkka_indeksikorjattu * j."osa-kuukaudesta") AS summa_indeksikorjattu,
           j.indeksikorjaus_vahvistettu                     AS indeksikorjaus_vahvistettu,
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
         -- Siirretään menneet
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', j.vuosi, j.kuukausi, 1)::DATE))) < date_trunc('month', pvm)
      -- Eikä kiinnitetä huomiota siihen, onko ne jo siirretty vai ei
      --AND j."siirretty?" = false
      -- Ja jotka on indeksikorjauksen osalta vahvistettu
      AND j.indeksikorjaus_vahvistettu IS NOT NULL
    ON CONFLICT (rivin_tunnistin)
        DO UPDATE SET summa_indeksikorjattu = EXCLUDED.summa_indeksikorjattu,
                      indeksikorjaus_vahvistettu = EXCLUDED.indeksikorjaus_vahvistettu, muokattu = NOW();

    -- Päivitetään kaikkiin juuri siirrettyihin riveihin tieto, että ne on käsitelty ja siirretty
    UPDATE kustannusarvioitu_tyo k SET "siirretty?" = true
                                       -- Päivitetään vain edellisen kuukauden jutut
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) < date_trunc('month', pvm)
      -- Ja vain ne, joita ei ole aiemin päivitetty siirretyksi
      AND k."siirretty?" = false
      -- Päivitetään vain indeksikorjauksen vahvistetut asiat
      AND k.indeksikorjaus_vahvistettu IS NOT NULL
      AND k.summa_indeksikorjattu IS NOT NULL
      -- Päivitetään vain tietyn tehtäväryhmän tehtäviä tai tietyn toimenpidekoodin tehtäviä
      AND (k.tehtavaryhma =
           (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c')
        OR
           k.tehtava in (SELECT id FROM toimenpidekoodi t WHERE t.yksiloiva_tunniste IN
                                                                ('8376d9c4-3daf-4815-973d-cd95ca3bb388', -- "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                                                                 'c9712637-fbec-4fbd-ac13-620b5619c744', -- Hoitourakan työnjohto
                                                                 '53647ad8-0632-4dd3-8302-8dfae09908c8'))); -- Hoidonjohtopalkkio

    -- Päivitetään kaikkiin juuri siirrettyihin riveihin tieto, että ne on käsitelty ja siirrety
    UPDATE johto_ja_hallintokorvaus j SET "siirretty?" = true
                                          -- Päivitetään vain edellisen kuukauden jutut
    WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', j.vuosi, j.kuukausi, 1)::DATE))) < date_trunc('month', pvm)
      -- Ja vain ne, joita ei ole aiemmin päivitetty siirrettäväksi
      AND j."siirretty?" = false
      -- Ja jotka on indeksikorjauksen osalta vahvistettu
      AND j.indeksikorjaus_vahvistettu IS NOT NULL;

    -- Merkitään hoidon johdon maksuerä likaiseksi kaikissa voimassaolevissa MH-urakoissa
    UPDATE maksuera
    SET likainen = TRUE,
        muokattu = current_timestamp
    WHERE toimenpideinstanssi IN
          (select tpi.id
           from toimenpideinstanssi tpi
                    join toimenpidekoodi tpk on tpi.toimenpide = tpk.id and tpk.koodi = '23151' -- 'MHU ja HJU Hoidon johto'
                    join urakka u on tpi.urakka = u.id and u.tyyppi = 'teiden-hoito'
               -- Maksueriä voi lähettää Sampoon vielä 3 kk urakan päättymisen jälkeen.
               and (u.loppupvm + interval '1 month' * 3) > pvm);
END;
$$ LANGUAGE plpgsql;

-- Poistetaan kaikki vanhat toteutuneet kustannukset
DELETE FROM toteutuneet_kustannukset;
-- Ja suoritetaan siirto
select siirra_budjetoidut_tyot_toteumiin_uudestaan(NOW()::DATE);