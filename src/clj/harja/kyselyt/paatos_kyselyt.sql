-- name: tee-lupauspaatos<!
-- Tee lupauspäätös
INSERT INTO paatos_lupaus (urakkaid, hoitokauden_alkuvuosi, tyyppi, tavoitehinta, luvatut_pisteet,
                           toteutuneet_pisteet, lupausbonus, lupaussanktio, erilliskustannus_id, sanktio_id, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :tyyppi, :tavoitehinta, :luvatut_pisteet,
        :toteutuneet_pisteet, :lupausbonus, :lupaussanktio, :erilliskustannus_id, :sanktio_id, :luoja, NOW());

-- name: poista-lupauspaatos<!
-- Poista lupauspäätös
UPDATE paatos_lupaus
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-lupauspaatokset
-- Hae lupauspäätökset
SELECT *
FROM paatos_lupaus
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

-- name: hae-lupauspaatos
SELECT *
FROM paatos_lupaus
WHERE id = :paatos-id
  AND poistettu = FALSE;

-- name: tee-tavoitehinnan-muutos-paatos<!
-- Tee tavoitehinnan muutos päätös
INSERT INTO paatos_tavoitehinnan_muutos (urakkaid, hoitokauden_alkuvuosi, versio, tavoitehinta, kattohinta, luoja,
                                         luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :versio, :tavoitehinta, :kattohinta, :luoja, NOW());

-- name: poista-tavoitehinnan-muutos-paatos<!
-- Poista tavoitehinnan muutos päätös
UPDATE paatos_tavoitehinnan_muutos
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-tavoitehinnan-muutos-paatokset
-- Hae tavoitehinnan muutos päätökset
SELECT *
FROM paatos_tavoitehinnan_muutos
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

-- name: hae-tavoitehinnan-muutospaatos
-- Hae tavoitehinnan muutos
SELECT *
FROM paatos_tavoitehinnan_muutos
WHERE id = :paatos-id
  AND poistettu = FALSE;

-- name: tee-tavoitehinnan-ylitys-paatos<!
-- Tee tavoitehinnan ylitys päätös
INSERT INTO paatos_tavoitehinta_ylitys (urakkaid, hoitokauden_alkuvuosi, versio, tavoitehinta, toteutuneet_kustannukset,
                                        ylityksen_maara, tilaajan_prosentti, urakoitsijan_prosentti, tilaaja_maksaa,
                                        urakoitsija_maksaa, siirto, kulu_id, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :versio, :tavoitehinta, :toteutuneet_kustannukset, :ylityksen_maara,
        :tilaajan_prosentti, :urakoitsijan_prosentti, :tilaaja_maksaa, :urakoitsija_maksaa, :siirto, :kulu_id, :luoja,
        NOW());

-- name: poista-tavoitehinnan-ylitys-paatos<!
-- Poista tavoitehinnan ylitys päätös
UPDATE paatos_tavoitehinta_ylitys
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-tavoitehinnnan-ylitys-paatokset
-- Hae tavoitehinnan ylitys päätökset
SELECT *
FROM paatos_tavoitehinta_ylitys
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

-- name: hae-tavoitehinnan-ylityspaatos
SELECT *
FROM paatos_tavoitehinta_ylitys
WHERE id = :paatos-id
  AND poistettu = FALSE;

-- name: tee-tavoitehinnan-alitus-paatos<!
-- Tee tavoitehinnan alitus päätös
INSERT INTO paatos_tavoitehinta_alitus (urakkaid, hoitokauden_alkuvuosi, versio, tavoitehinta, toteutuneet_kustannukset,
                                        alituksen_maara, siirron_maara, tavoitepalkkio
    , siirto, kulu_id, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :versio, :tavoitehinta, :toteutuneet_kustannukset, :alituksen_maara,
        :siirron_maara, :tavoitepalkkio, :siirto, :kulu_id, :luoja, NOW());

-- name: poista-tavoitehinnan-alitus-paatos<!
-- Poista tavoitehinnan alitus päätös
UPDATE paatos_tavoitehinta_alitus
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-tavoitehinnnan-alitus-paatokset
-- Hae tavoitehinnan alitus päätökset
SELECT *
FROM paatos_tavoitehinta_alitus
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

-- name: hae-tavoitehinnan-alituspaatos
SELECT *
FROM paatos_tavoitehinta_alitus
WHERE id = :paatos-id
  AND poistettu = FALSE;

-- name: tee-kattohointa-paatos<!
-- Tee kattohinta päätös
INSERT INTO paatos_kattohinta (urakkaid, hoitokauden_alkuvuosi, kattohinta, toteutuneet_kustannukset, ylityksen_maara,
                               urakoitsija_maksaa, siirrettava_maara, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :kattohinta, :toteutuneet_kustannukset, :ylityksen_maara,
        :urakoitsija_maksaa, :siirrettava_maara, :luoja, NOW());

-- name: poista-kattohinta-paatos<!
-- Poista kattohinta päätös
UPDATE paatos_kattohinta
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-kattohinta-paatokset
-- Hae kattohinta päätökset
SELECT *
FROM paatos_kattohinta
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

-- name: hae-kattohinta-paatos
-- Hae kattohinta päätös
SELECT *
FROM paatos_kattohinta
WHERE id = :paatos-id
  AND poistettu = FALSE;

-- name: tee-hoitokauden-lopun-hinta-paatos<!
-- Tee hoitokauden lopun hinta päätös
INSERT INTO paatos_hoitokauden_lopun_hinta (urakkaid, hoitokauden_alkuvuosi, tavoitehinta, kattohinta, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :tavoitehinta, :kattohinta, :luoja, NOW());

--name: poista-hoitokauden-lopun-hinta-paatos<!
-- Poista hoitokauden lopun hinta päätös
UPDATE paatos_hoitokauden_lopun_hinta
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-hoitokauden-lopun-hinta-paatokset
-- Hae hoitokauden lopun hintapäätökset
SELECT *
FROM paatos_hoitokauden_lopun_hinta
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

-- name: tee-hoitokauden-indeksikorjaus-paatos<!
-- Tee hoitokauden indeksikorjaus päätös
INSERT INTO paatos_hoitokauden_indeksikorjaus (urakkaid, hoitokauden_alkuvuosi, tavoitehinta, tavoitehinnan_muutokset,
                                               tavoitehinta_ennen, pistelukujen_muutos, indeksikorotuksen_prosentit,
                                               hoitokauden_lopun_indeksikorjaus, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :tavoitehinta, :tavoitehinnan_muutokset, :tavoitehinta_ennen,
        :pistelukujen_muutos, :indeksikorotuksen_prosentit, :hoitokauden_lopun_indeksikorjaus, :luoja, NOW());

-- name: poista-hoitokauden-indeksikorjaus-paatos<!
-- Poista hoitokauden indeksikorjaus päätös
UPDATE paatos_hoitokauden_indeksikorjaus
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-hoitokauden-indeksikorjaus-paatokset
-- Hae hoitokauden indeksikorjauspäätökset
SELECT *
FROM paatos_hoitokauden_indeksikorjaus
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;

--name: tee-hoidonjohtopalkkio-paatos<!
-- Tee hoidonjohtopalkkio päätös
INSERT INTO paatos_hoidonjohtopalkkio (urakkaid, hoitokauden_alkuvuosi, tavoitehinta_ennen, hoidonjohtopalkkio,
                                       tavoitehinta_jalkeen, luoja, luotu)
VALUES (:urakkaid, :hoitokauden_alkuvuosi, :tavoitehinta_ennen, :hoidonjohtopalkkio, :tavoitehinta_jalkeen,
        :luoja, NOW());

--name: poista-hoidonjohtopalkkio-paatos<!
-- Poista hoidonjohtopalkkio päätös
UPDATE paatos_hoidonjohtopalkkio
SET poistettu = TRUE,
    poistaja  = :poistaja
WHERE id = :id;

-- name: hae-hoidonjohtopalkkiopaatokset
-- Hae hoidonjohtopalkkiopäätökset
SELECT *
FROM paatos_hoidonjohtopalkkio
WHERE urakkaid = :urakkaid
  AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
  AND poistettu = FALSE;
