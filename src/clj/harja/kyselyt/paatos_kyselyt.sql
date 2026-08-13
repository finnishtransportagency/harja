-- name: tee-lupauspaatos<!
-- Tee lupauspäätös
INSERT INTO paatos_lupaus (urakkaid, hoitokauden_alkuvuosi, tyyppi, tavoitehinta, tarjous_tavoitehinta, luvatut_pisteet,
                           toteutuneet_pisteet, lupausbonus, lupaussanktio, bonusprosentti, sanktioprosentti, indeksi, indeksikorotus, erilliskustannus_id, sanktio_id, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :tyyppi, :tavoitehinta, :tarjous_tavoitehinta, :luvatut_pisteet,
        :toteutuneet_pisteet, :lupausbonus, :lupaussanktio, :bonusprosentti, :sanktioprosentti, :indeksi, :indeksikorotus, :erilliskustannus_id, :sanktio_id, :luoja, NOW());

-- name: poista-lupauspaatos<!
-- Poista lupauspäätös
UPDATE paatos_lupaus
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-lupauspaatokset
-- Hae lupauspäätökset
SELECT 'Lupaukset' as nimi, *
FROM paatos_lupaus
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

-- name: hae-lupauspaatos
SELECT 'Lupaukset' as nimi, *
FROM paatos_lupaus
WHERE id = :paatos-id
  AND poistettu = FALSE;

-- name: tee-tavoitehinnan-muutos-paatos<!
-- Tee tavoitehinnan muutos päätös
INSERT INTO paatos_tavoitehinnan_muutos (urakkaid, hoitokauden_alkuvuosi, tavoitehinta, kattohinta, muokkaa_kattohinta, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :tavoitehinta, :kattohinta, :muokkaa_kattohinta, :luoja, NOW());

-- name: poista-tavoitehinnan-muutos-paatos<!
-- Poista tavoitehinnan muutos päätös
UPDATE paatos_tavoitehinnan_muutos
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-tavoitehinnan-muutos-paatokset
-- Hae tavoitehinnan muutos päätökset
SELECT 'Tavoitehinnan muutokset' as nimi, *
FROM paatos_tavoitehinnan_muutos
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

-- name: hae-tavoitehinnan-muutospaatos
-- Hae tavoitehinnan muutos
SELECT 'Tavoitehinnan muutokset' as nimi, *
FROM paatos_tavoitehinnan_muutos
WHERE id = :paatos-id
  AND poistettu = FALSE;

-- name: tee-tavoitehinnan-pysyva-muutospaatos<!
-- Tee tavoitehinnan pysyvä muutos päätös
INSERT INTO paatos_tavoitehinnan_pysyva_muutos (urakkaid, hoitokauden_alkuvuosi, kirjallisesti_sovitut_muutokset,
                                               pysyvat_muutokset, johto_ja_hallintakorvaus_muutokset, muutostyo_muutokset,
                                               toteumiin_perustuvat_muutokset, tehtava_ja_maaratoteumamuutokset,
                                               rahavarausten_muutokset, arvonvahennysten_muutokset,
                                               tavoitehinnan_muutokset_yhteensa, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :kirjallisesti-sovitut-muutokset, :pysyvat-muutokset, :johto-ja-hallintakorvaus-muutokset,
        :muutostyo-muutokset, :toteumiin-perustuvat-muutokset, :tehtava-ja-maaratoteumamuutokset,
        :rahavarausten-muutokset, :arvonvahennysten-muutokset, :tavoitehinnan-muutokset-yhteensa, :luoja, NOW());

-- name: poista-tavoitehinnan-pysyva-muutos-paatos<!
-- Poista tavoitehinnan muutos päätös
UPDATE paatos_tavoitehinnan_pysyva_muutos
   SET poistettu = TRUE,
       poistaja  = :poistaja
 WHERE id = :id;

-- name: hae-tavoitehinnan-pysyvat-muutospaatokset
-- Hae tavoitehinnan pysyvät muutospäätökset
SELECT 'Tavoitehinnan pysyvät muutokset' as nimi, *
  FROM paatos_tavoitehinnan_pysyva_muutos
 WHERE urakkaid = :urakkaid
   AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
   AND poistettu = FALSE;

-- name: hae-tavoitehinnan-pysyva-muutospaatos
-- Hae tavoitehinnan pysyvä muutos
SELECT 'Tavoitehinnan pysyvät muutokset' as nimi, *
  FROM paatos_tavoitehinnan_pysyva_muutos
 WHERE id = :paatos-id
   AND poistettu = FALSE;

-- name: tee-tavoitehinnan-ylitys-paatos<!
-- Tee tavoitehinnan ylitys päätös
INSERT INTO paatos_tavoitehinta_ylitys (urakkaid, hoitokauden_alkuvuosi, tavoitehinta, toteutuneet_kustannukset,
                                        ylityksen_maara, tilaajan_prosentti, urakoitsijan_prosentti, tilaaja_maksaa,
                                        urakoitsija_maksaa, kulu_id, viimeinen_hoitokausi, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :tavoitehinta, :toteutuneet_kustannukset, :ylityksen_maara,
        :tilaajan_prosentti, :urakoitsijan_prosentti, :tilaaja_maksaa, :urakoitsija_maksaa, :kulu_id, :viimeinen_hoitokausi, :luoja,
        NOW());

-- name: poista-tavoitehinnan-ylitys-paatos<!
-- Poista tavoitehinnan ylitys päätös
UPDATE paatos_tavoitehinta_ylitys
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-tavoitehinnnan-ylitys-paatokset
-- Hae tavoitehinnan ylitys päätökset
SELECT 'Tavoitehinnan ylitys' as nimi, *
FROM paatos_tavoitehinta_ylitys
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

-- name: hae-tavoitehinnan-ylityspaatos
SELECT 'Tavoitehinnan ylitys' as nimi, *
FROM paatos_tavoitehinta_ylitys
WHERE id = :paatos-id
  AND poistettu = FALSE;

-- name: tee-tavoitehinnan-alitus-paatos<!
-- Tee tavoitehinnan alitus päätös
INSERT INTO paatos_tavoitehinta_alitus (urakkaid, hoitokauden_alkuvuosi, hoitokauden_alun_tavoitehinta, hoitokauden_lopun_tavoitehinta, toteutuneet_kustannukset,
                                        alituksen_maara, siirron_maara, tavoitepalkkio, kulu_id, tavoitepalkkion_maksuprosentti, tavoitepalkkion_maksimi_prosentti,
                                        viimeinen_hoitokausi, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :hoitokauden_alun_tavoitehinta, :hoitokauden_lopun_tavoitehinta, :toteutuneet_kustannukset, :alituksen_maara,
        :siirron_maara, :tavoitepalkkio, :kulu_id, :tavoitepalkkion_maksuprosentti, :tavoitepalkkion_maksimi_prosentti, :viimeinen_hoitokausi, :luoja, NOW());

-- name: poista-tavoitehinnan-alitus-paatos<!
-- Poista tavoitehinnan alitus päätös
UPDATE paatos_tavoitehinta_alitus
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-tavoitehinnnan-alitus-paatokset
-- Hae tavoitehinnan alitus päätökset
SELECT 'Tavoitehinnan alitus' as nimi, *
FROM paatos_tavoitehinta_alitus
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

-- name: hae-tavoitehinnan-alituspaatos
SELECT 'Tavoitehinnan alitus' as nimi, *
FROM paatos_tavoitehinta_alitus
WHERE id = :paatos-id
  AND poistettu = FALSE;

-- name: tee-kattohinta-paatos<!
-- Tee kattohinta päätös
INSERT INTO paatos_kattohinta (urakkaid, hoitokauden_alkuvuosi, kattohinta, toteutuneet_kustannukset, ylityksen_maara,
                               urakoitsija_maksaa, siirrettava_maara, kulu_id, viimeinen_hoitokausi,
                               maksimi_siirrettava_maara, siirtorajoitus_prosentti, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :kattohinta, :toteutuneet_kustannukset, :ylityksen_maara,
        :urakoitsija_maksaa, :siirrettava_maara, :kulu_id, :viimeinen_hoitokausi,
        :maksimi_siirrettava_maara, :siirtorajoitus_prosentti, :luoja, NOW());

-- name: poista-kattohinta-paatos<!
-- Poista kattohinta päätös
UPDATE paatos_kattohinta
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-kattohinta-paatokset
-- Hae kattohinta päätökset
SELECT 'Kattohinnan ylitys' as nimi, *
FROM paatos_kattohinta
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

-- name: hae-kattohinta-paatos
-- Hae kattohinta päätös
SELECT 'Kattohinnan ylitys' as nimi, *
FROM paatos_kattohinta
WHERE id = :paatos-id
  AND poistettu = FALSE;

-- name: tee-hoitokauden-lopun-hinta-paatos<!
-- Tee hoitokauden lopun hinta päätös
INSERT INTO paatos_hoitokauden_lopun_hinta (urakkaid, hoitokauden_alkuvuosi, tavoitehinta_ennen, tavoitehinta_jalkeen,
                                            tavoitehinnan_muutokset, hoitokauden_lopun_indeksikorjaus, kattohinta,
                                            kattohintakerroin, lisaa_tavoitehintaan_lopunindeksikorjaus, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :tavoitehinta_ennen, :tavoitehinta_jalkeen, :tavoitehinnan_muutokset,
        :hoitokauden_lopun_indeksikorjaus, :kattohinta, :kattohintakerroin, :lisaa_tavoitehintaan_lopunindeksikorjaus,
        :luoja, NOW());

--name: poista-hoitokauden-lopun-hinta-paatos<!
-- Poista hoitokauden lopun hinta päätös
UPDATE paatos_hoitokauden_lopun_hinta
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-hoitokauden-lopun-hinta-paatokset
-- Hae hoitokauden lopun hintapäätökset
SELECT 'Hoitovuoden lopun tavoite- ja kattohinta' as nimi, *
FROM paatos_hoitokauden_lopun_hinta
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

-- name: hae-hoitokauden-lopun-hintapaatos
-- Hae hoitokauden lopun hintapäätökset
SELECT 'Hoitovuoden lopun tavoite- ja kattohinta' as nimi, *
FROM paatos_hoitokauden_lopun_hinta
WHERE id = :paatos-id
  AND poistettu = FALSE;

-- name: tee-hoitokauden-indeksikorjaus-paatos<!
-- Tee hoitokauden indeksikorjaus päätös
INSERT INTO paatos_hoitokauden_indeksikorjaus (urakkaid, hoitokauden_alkuvuosi, hv_alun_indkorj_tavoitehinta, tavoitehinnan_muutokset,
                                               hv_lopun_tavoitehinta_ennen_indkorj, hoitokauden_kuukaudet, kuukausien_keskiarvo,
                                               alkuperainen_pisteluku, alkuperaisen_pisteluvun_kuukausi, pistelukujen_muutos,
                                               pistelukujen_muutos_prosentteina, indeksikorotuksen_prosenttiosuus,
                                               hoitokauden_lopun_indeksikorjaus, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :hv_alun_indkorj_tavoitehinta, :tavoitehinnan_muutokset, :hv_lopun_tavoitehinta_ennen_indkorj,
        (SELECT ARRAY[:hoitokauden_kuukaudet]::indeksikorjauskuukausi[]), :kuukausien_keskiarvo, :alkuperainen_pisteluku,
        :alkuperaisen_pisteluvun_kuukausi, :pistelukujen_muutos, :pistelukujen_muutos_prosentteina,
        :indeksikorotuksen_prosenttiosuus, :hoitokauden_lopun_indeksikorjaus, :luoja, NOW());

-- name: poista-hoitokauden-indeksikorjaus-paatos<!
-- Poista hoitokauden indeksikorjaus päätös
UPDATE paatos_hoitokauden_indeksikorjaus
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-hoitokauden-indeksikorjaus-paatos
SELECT 'Hoitovuoden lopun indeksikorjaus' as nimi, *
FROM paatos_hoitokauden_indeksikorjaus
WHERE id = :paatos-id
  AND poistettu = FALSE;

-- name: hae-hoitokauden-indeksikorjaus-paatokset
-- Hae hoitokauden indeksikorjauspäätökset
SELECT 'Hoitovuoden lopun indeksikorjaus' as nimi, *
FROM paatos_hoitokauden_indeksikorjaus
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

--name: tee-hoidonjohtopalkkio-paatos<!
-- Tee hoidonjohtopalkkio päätös
INSERT INTO paatos_hoidonjohtopalkkio (urakkaid, hoitokauden_alkuvuosi, hv_lopun_indkorjaamaton_tavoitehinta, tarjouksen_tavoitehinta, hoidonjohtopalkkio,
                                       muutosprosentti, hoidonjohtopalkkio_muutos, kulu_id, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :hv_lopun_indkorjaamaton_tavoitehinta, :tarjouksen_tavoitehinta, :hoidonjohtopalkkio,
        :muutosprosentti, :hoidonjohtopalkkio_muutos, :kulu_id, :luoja, NOW());

--name: poista-hoidonjohtopalkkio-paatos<!
-- Poista hoidonjohtopalkkio päätös
UPDATE paatos_hoidonjohtopalkkio
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-hoidonjohtopalkkiopaatokset
-- Hae hoidonjohtopalkkiopäätökset
SELECT 'Hoidonjohtopalkkion muutos' as nimi, *
FROM paatos_hoidonjohtopalkkio
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

-- name: hae-hoidonjohtopalkkiopaatos
-- Hae hoidonjohtopalkkiopäätökset
SELECT 'Hoidonjohtopalkkion muutos' as nimi, *
FROM paatos_hoidonjohtopalkkio
WHERE id = :paatos-id
  AND poistettu = FALSE;

--name: tee-poytakirjan-raporttipaatos<!
INSERT INTO paatos_poytakirjan_raportti (urakkaid, hoitokauden_alkuvuosi, tarkistettu, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, NOW(), :luoja, NOW());

--name: poista-poytakirjan-raporttipaatos<!
UPDATE paatos_poytakirjan_raportti
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-poytakirjan-raporttipaatokset
SELECT 'Välikatselmuspöytäkirjaan liitettävät raportit' as nimi, *
FROM paatos_poytakirjan_raportti
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

-- name: hae-poytakirjan-raporttipaatos
SELECT 'Välikatselmuspöytäkirjaan liitettävät raportit' as nimi, *
FROM paatos_poytakirjan_raportti
WHERE id = :paatos-id
  AND poistettu = FALSE;

-- name: hae-hoitokauden-lopun-indeksikorjaus
-- single?: true
-- Jos hoitokauden indeksikorjaus on tehty, niin hae se
SELECT hoitokauden_lopun_indeksikorjaus
FROM paatos_hoitokauden_indeksikorjaus
WHERE hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
 AND urakkaid = :urakkaid
 AND poistettu = FALSE;

-- name: hae-budjetoitu-hoidonjohtopalkkio-hoitokaudelle
SELECT SUM(kt.summa)                                  AS budjetoitu_summa,
       SUM(kt.summa_indeksikorjattu)                  AS budjetoitu_summa_indeksikorjattu
FROM kustannusarvioitu_tyo kt
     JOIN sopimus s ON kt.sopimus = s.id AND s.urakka = :urakkaid
WHERE kt.toimenpideinstanssi = (SELECT tpi.id AS id
                                FROM toimenpideinstanssi tpi
                                         JOIN toimenpide tpk3 ON tpk3.id = tpi.toimenpide
                                         JOIN toimenpide tpk2 ON tpk3.emo = tpk2.id
                                WHERE tpi.urakka = :urakkaid
                                  AND tpk2.koodi = '23150'
                                limit 1)
  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND (kt.tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'G - Hoidonjohtopalkkio')
    OR kt.tehtava = (SELECT id
                     from tehtava
                     WHERE yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744') -- Hoitourakan työnjohto
    OR kt.tehtava = (SELECT id
                     from tehtava
                     WHERE yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8')) -- Hoidonjohtopalkkio;
group by kt.sopimus;

-- name: paivita-kattohinta<!
-- Käytetään, kun 19/20 vuosien urakassa on asetettu uusi kattohinta eli kun tavoitehinta-muutospäätös on tehty
UPDATE urakka_tavoite
SET kattohinta = :kattohinta,
    kattohinta_indeksikorjattu = :kattohinta,
    muokattu = CURRENT_TIMESTAMP,
    muokkaaja = :muokkaaja
WHERE urakka = :urakkaid
  AND hoitokausi = :hoitovuosinro;
