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
                                        alituksen_maara, siirron_maara, tavoitepalkkio, kulu_id, tavoitepalkkion_maksuprosentti,
                                        viimeinen_hoitokausi, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :hoitokauden_alun_tavoitehinta, :hoitokauden_lopun_tavoitehinta, :toteutuneet_kustannukset, :alituksen_maara,
        :siirron_maara, :tavoitepalkkio, :kulu_id, :tavoitepalkkion_maksuprosentti, :viimeinen_hoitokausi, :luoja, NOW());

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
INSERT INTO paatos_hoitokauden_indeksikorjaus (urakkaid, hoitokauden_alkuvuosi, tavoitehinta, tavoitehinnan_muutokset,
                                               tavoitehinta_ennen, hoitokauden_kuukaudet, kuukausien_keskiarvo,
                                               alkuperainen_pisteluku, alkuperaisen_pisteluvun_kuukausi, pistelukujen_muutos,
                                               indeksikorotuksen_prosenttiosuus, hoitokauden_lopun_indeksikorjaus, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :tavoitehinta, :tavoitehinnan_muutokset, :tavoitehinta_ennen,
        (SELECT ARRAY[:hoitokauden_kuukaudet]::indeksikorjauskuukausi[]), :kuukausien_keskiarvo, :alkuperainen_pisteluku,
        :alkuperaisen_pisteluvun_kuukausi, :pistelukujen_muutos, :indeksikorotuksen_prosenttiosuus, :hoitokauden_lopun_indeksikorjaus, :luoja, NOW());

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
INSERT INTO paatos_hoidonjohtopalkkio (urakkaid, hoitokauden_alkuvuosi, tavoitehinta, tarjouksen_tavoitehinta, hoidonjohtopalkkio,
                                       muutosprosentti, hoidonjohtopalkkio_muutos, kulu_id, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :tavoitehinta, :tarjouksen_tavoitehinta, :hoidonjohtopalkkio,
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
-- Tee hoidonjohtopalkkio päätös
INSERT INTO paatos_poytakirjan_raportti (urakkaid, hoitokauden_alkuvuosi, tarkistettu, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, NOW(), :luoja, NOW());

--name: poista-poytakirjan-raporttipaatos<!
-- Poista hoidonjohtopalkkio päätös
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
from kustannusarvioitu_tyo kt
     JOIN sopimus s ON kt.sopimus = s.id AND s.urakka = :urakkaid
WHERE kt.toimenpideinstanssi = (SELECT tpi.id AS id
                                FROM toimenpideinstanssi tpi
                                         JOIN toimenpide tpk3 ON tpk3.id = tpi.toimenpide
                                         JOIN toimenpide tpk2 ON tpk3.emo = tpk2.id
                                WHERE tpi.urakka = :urakkaid
                                  AND tpk2.koodi = '23150'
                                limit 1)
  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
GROUP BY kt.toimenpideinstanssi, kt.tehtavaryhma;
