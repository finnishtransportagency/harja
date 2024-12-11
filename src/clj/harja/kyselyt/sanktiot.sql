-- name: luo-sanktio<!
-- Luo uuden sanktion annetulle laatupoikkeamalle
INSERT
INTO sanktio
(perintapvm, sakkoryhma, tyyppi, toimenpideinstanssi, vakiofraasi, maara, indeksi, laatupoikkeama, suorasanktio, luoja,
 luotu)
VALUES (:perintapvm, :ryhma :: sanktiolaji, :tyyppi,
        COALESCE(
            (SELECT t.id -- suoraan annettu tpi
             FROM toimenpideinstanssi t
             WHERE t.id = :tpi_id
               AND t.urakka = :urakka),
            (SELECT t.id
             FROM toimenpideinstanssi t -- sanktiotyyppiin linkattu tpi
                      JOIN sanktiotyyppi s ON s.toimenpidekoodi = t.toimenpide
             WHERE s.id = :tyyppi
               AND t.urakka = :urakka)),
        :vakiofraasi,
        :summa, :indeksi, :laatupoikkeama, :suorasanktio, :luoja, NOW());

-- name: paivita-sanktio!
-- Päivittää olemassaolevan sanktion
UPDATE sanktio
SET perintapvm          = :perintapvm,
    sakkoryhma          = :ryhma :: sanktiolaji,
    tyyppi              = :tyyppi,
    toimenpideinstanssi = COALESCE(
        (SELECT t.id FROM toimenpideinstanssi t WHERE t.id = :tpi_id AND t.urakka = :urakka),
        (SELECT t.id
         FROM toimenpideinstanssi t
                  JOIN sanktiotyyppi s ON s.toimenpidekoodi = t.toimenpide
         WHERE s.id = :tyyppi
           AND t.urakka = :urakka)),
    vakiofraasi         = :vakiofraasi,
    maara               = :summa,
    indeksi             = :indeksi,
    laatupoikkeama      = :laatupoikkeama,
    suorasanktio        = :suorasanktio,
    muokkaaja           = :muokkaaja,
    poistettu           = :poistettu,
    muokattu            = NOW()
WHERE id = :id;

-- name: hae-laatupoikkeaman-sanktiot
-- Palauttaa kaikki annetun laatupoikkeaman sanktiot
SELECT s.id,
       s.perintapvm,
       s.maara           AS summa,
       s.sakkoryhma      AS laji,
       s.toimenpideinstanssi,
       s.indeksi,
       s.vakiofraasi,
       t.id              AS tyyppi_id,
       t.nimi            AS tyyppi_nimi,
       t.toimenpidekoodi AS tyyppi_toimenpidekoodi,
       t.koodi           AS tyyppi_koodi
FROM sanktio s
         LEFT JOIN sanktiotyyppi t ON s.tyyppi = t.id
WHERE laatupoikkeama = :laatupoikkeama
  AND s.poistettu IS NOT TRUE;

-- name: hae-suorasanktion-tiedot
-- Hae yksittäisen suora sanktion tiedot
SELECT s.id,
       s.perintapvm,
       s.maara           AS summa,
       s.sakkoryhma      AS laji,
       s.suorasanktio,
       s.toimenpideinstanssi,
       s.indeksi,
       s.vakiofraasi,
       s.laatupoikkeama  AS laatupoikkeama_id,
       t.id              AS tyyppi_id,
       t.nimi            AS tyyppi_nimi,
       t.toimenpidekoodi AS tyyppi_toimenpidekoodi,
       t.koodi           AS tyyppi_koodi
FROM sanktio s
         LEFT JOIN sanktiotyyppi t ON s.tyyppi = t.id
WHERE s.id = :id;

-- name: poista-sanktio!
UPDATE sanktio
SET poistettu = TRUE,
    muokattu  = NOW(),
    muokkaaja = :muokkaaja
WHERE id = :id;


-- name: hae-urakan-sanktiot
-- row-fn: muunna-urakan-sanktio
-- Palauttaa kaikki urakalle kirjatut sanktiot perintäpäivämäärällä ja toimenpideinstanssilla rajattuna
-- Käytetään siis mm. Laadunseuranta/sanktiot välilehdellä
SELECT s.id,
       s.perintapvm,
       -- Haetaan kasittelyaika sanktioiden ja bonusten listausta varten.
       -- Huomaa, että sama käsittelyaika haetaan myös erikseen hierarkiana laatupoikkeamaa varten ja sitä käytetään lomakkeella
       -- sanktion laatupoikkeamassa.
       lp.kasittelyaika                             AS kasittelyaika,
       s.maara                                      AS summa,
       s.sakkoryhma::text                           AS laji,
       s.indeksi,
       s.suorasanktio,
       s.toimenpideinstanssi,
       s.vakiofraasi,
       (SELECT korotus
        FROM sanktion_indeksikorotus(s.perintapvm, s.indeksi, s.maara, u.id,
                                     s.sakkoryhma)) AS indeksikorjaus,
       lp.id                                        AS laatupoikkeama_id,
       lp.kohde                                     AS laatupoikkeama_kohde,
       lp.aika                                      AS laatupoikkeama_aika,
       lp.tekija                                    AS laatupoikkeama_tekija,
       lp.urakka                                    AS laatupoikkeama_urakka,
       CONCAT(k.etunimi, ' ', k.sukunimi)           AS laatupoikkeama_tekijanimi,
       lp.kasittelyaika                             AS laatupoikkeama_paatos_kasittelyaika,
       lp.paatos                                    AS laatupoikkeama_paatos_paatos,
       lp.kasittelytapa                             AS laatupoikkeama_paatos_kasittelytapa,
       lp.muu_kasittelytapa                         AS laatupoikkeama_paatos_muukasittelytapa,
       lp.kuvaus                                    AS laatupoikkeama_kuvaus,
       lp.perustelu                                 AS laatupoikkeama_paatos_perustelu,
       lp.tr_numero                                 AS laatupoikkeama_tr_numero,
       lp.tr_alkuosa                                AS laatupoikkeama_tr_alkuosa,
       lp.tr_loppuosa                               AS laatupoikkeama_tr_loppuosa,
       lp.tr_alkuetaisyys                           AS laatupoikkeama_tr_alkuetaisyys,
       lp.tr_loppuetaisyys                          AS laatupoikkeama_tr_loppuetaisyys,
       lp.sijainti                                  AS laatupoikkeama_sijainti,
       lp.tarkastuspiste                            AS laatupoikkeama_tarkastuspiste,
       lp.selvitys_pyydetty                         AS laatupoikkeama_selvityspyydetty,
       lp.selvitys_annettu                          AS laatupoikkeama_selvitysannettu,

       ypk.tr_numero                                AS yllapitokohde_tr_numero,
       ypk.tr_alkuosa                               AS yllapitokohde_tr_alkuosa,
       ypk.tr_alkuetaisyys                          AS yllapitokohde_tr_alkuetaisyys,
       ypk.tr_loppuosa                              AS yllapitokohde_tr_loppuosa,
       ypk.tr_loppuetaisyys                         AS yllapitokohde_tr_loppuetaisyys,
       ypk.kohdenumero                              AS yllapitokohde_numero,
       ypk.nimi                                     AS yllapitokohde_nimi,
       ypk.id                                       AS yllapitokohde_id,
       ypk.yhaid                                    AS yllapitokohde_yhaid,

       t.nimi                                       AS tyyppi_nimi,
       t.id                                         AS tyyppi_id,
       t.toimenpidekoodi                            AS tyyppi_toimenpidekoodi,
       t.koodi                                      AS tyyppi_koodi

FROM sanktio s
         JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id
         JOIN urakka u ON lp.urakka = u.id
         JOIN kayttaja k ON lp.luoja = k.id
         LEFT JOIN sanktiotyyppi t ON s.tyyppi = t.id
         LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id
WHERE lp.urakka = :urakka
  -- Ei haeta tässä ylläpidon bonus 'sanktioita', vaan haetaan ne bonuksina eri kyselyssä.
  --   Tämä edistää ylläpidon bonusten käsittelyn refaktorointia myöhemmin siten, että niitäkin käsiteltäisiin samalla
  --   logiikalla kuin muidenkin urakkatyyppien bonuksia.
  AND s.sakkoryhma != 'yllapidon_bonus'::SANKTIOLAJI
  AND lp.poistettu IS NOT TRUE
  AND s.poistettu IS NOT TRUE
  AND (s.perintapvm >= :alku AND s.perintapvm <= :loppu
    -- Halutaan että urakan päättymisen jälkeiset sanktiot näkyvät viimeisen hoitokauden listauksessa
    OR
       (CASE
                   date_part('year', :loppu::date)::integer = date_part('year', u.loppupvm)::integer
               AND date_part('month', :loppu::date)::integer = date_part('month', u.loppupvm)::integer
            WHEN TRUE THEN s.perintapvm > u.loppupvm
            ELSE FALSE
           END))
  -- Ei kuulu poistettuun ylläpitokohteeseen
  AND (lp.yllapitokohde IS NULL
    OR
       lp.yllapitokohde IS NOT NULL AND
       (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS FALSE);

-- name: hae-urakan-bonukset
-- row-fn: muunna-urakan-bonus
-- Palauttaa kaikki urakalle kirjatut bonukset laskutuskuukaudella ja toimenpideinstanssilla rajattuna
-- Käytetään siis mm. Laadunseuranta/sanktiot välilehdellä
-- Filtteröi muu tyyppiset erilliskustannukset pois, koska niitä ei voi tämän renderöivässä näkymässä muokata eikä lisätä

-- Bonukset erilliskustannuksista
SELECT ek.id,
       ek.laskutuskuukausi                                 as perintapvm,
       ek.pvm                                              AS kasittelyaika,
       ek.rahasumma                                        AS summa,
       ek.tyyppi::TEXT                                     AS laji,
       ek.indeksin_nimi                                    AS indeksi,
       TRUE                                                AS suorasanktio,
       TRUE                                                as bonus,
       ek.kasittelytapa                                    as kasittelytapa,
       ek.toimenpideinstanssi                              AS toimenpideinstanssi,
       (SELECT korotus
        from erilliskustannuksen_indeksilaskenta(ek.pvm, ek.indeksin_nimi, ek.rahasumma,
                                                 ek.urakka, ek.tyyppi,
                                                 CASE
                                                     WHEN u.tyyppi = 'teiden-hoito'::urakkatyyppi THEN TRUE
                                                     ELSE FALSE
                                                     END)) AS indeksikorjaus,
       ek.lisatieto                                        AS lisatieto,
       --  Muilla urakkatyypeillä kuin ylläpidon urakoilla ei voi olla bonukseen liitettyä ylläpitokohdetta
       NULL                                                AS yllapitokohde_tr_numero,
       NULL                                                AS yllapitokohde_tr_alkuosa,
       NULL                                                AS yllapitokohde_tr_alkuetaisyys,
       NULL                                                AS yllapitokohde_tr_loppuosa,
       NULL                                                AS yllapitokohde_tr_loppuetaisyys,
       NULL                                                AS yllapitokohde_numero,
       NULL                                                AS yllapitokohde_nimi,
       NULL                                                AS yllapitokohde_id,
       NULL                                                AS yllapitokohde_yhaid
FROM erilliskustannus ek
         JOIN urakka u ON ek.urakka = u.id
WHERE ek.urakka = :urakka
  AND ek.laskutuskuukausi BETWEEN :alku AND :loppu
  AND ek.poistettu IS NOT TRUE
  AND ek.tyyppi != 'muu'::erilliskustannustyyppi

UNION

-- Hae ylläpidon urakoille poikkeuksellisesti bonus sanktio-taulusta
-- TODO refaktoroidaan myöhemmin ylläpidon bonusten käsittely sellaiseksi, että poikkeuksellista käsittelyä ei
--      tarvitsisi tehdä.
SELECT s.id,
       -- perintapvm sanktiolla vastaa erilliskustannuksen laskutuskuukautta
       s.perintapvm          AS perintapvm,
       -- Kasittelyaika haetaan sanktion suhteen laatupoikkeaman puolelta, erilliskustannuksissa se on 'pvm'-sarake.
       lp.kasittelyaika      AS kasittelyaika,
       -- Muunna ylläpidon bonuksen summa positiiviseksi (se on käytännössä negatiivinen sanktio nykytoteuksella)
       s.maara * -1          AS summa,
       'yllapidon_bonus'     AS laji,
       s.indeksi             AS indeksi,
       TRUE                  AS suorasanktio,
       TRUE                  AS bonus,
       lp.kasittelytapa      AS kasittelytapa,
       s.toimenpideinstanssi AS toimenpideinstanssi,
       0                     AS indeksikorjaus,
       lp.perustelu          AS lisatieto,
       -- Ylläpitourakoilla voi olla bonukseen liitetty ylläpitokohde
       ypk.tr_numero         AS yllapitokohde_tr_numero,
       ypk.tr_alkuosa        AS yllapitokohde_tr_alkuosa,
       ypk.tr_alkuetaisyys   AS yllapitokohde_tr_alkuetaisyys,
       ypk.tr_loppuosa       AS yllapitokohde_tr_loppuosa,
       ypk.tr_loppuetaisyys  AS yllapitokohde_tr_loppuetaisyys,
       ypk.kohdenumero       AS yllapitokohde_numero,
       ypk.nimi              AS yllapitokohde_nimi,
       ypk.id                AS yllapitokohde_id,
       ypk.yhaid             AS yllapitokohde_yhaid
FROM sanktio s
         JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id
         LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id
WHERE lp.urakka = :urakka
  AND s.sakkoryhma = 'yllapidon_bonus'::SANKTIOLAJI
  AND s.perintapvm BETWEEN :alku AND :loppu
  AND s.poistettu IS NOT TRUE;

-- name: merkitse-maksuera-likaiseksi!
-- Merkitsee sanktiota vastaavan maksuerän likaiseksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
-- Merkitään vain jos toimenpideinstanssi on voimassa tai sen vanhenemisesta on 3 kk.
UPDATE maksuera
SET likainen = TRUE,
    muokattu = current_timestamp
WHERE tyyppi = 'sakko'
  AND toimenpideinstanssi IN (SELECT toimenpideinstanssi
                              FROM sanktio
                              WHERE id = :sanktio)
  AND toimenpideinstanssi IN
      (select id from toimenpideinstanssi where loppupvm > current_timestamp - INTERVAL '3 months');

-- name: hae-sanktiotyypit
-- Hakee kaikki sanktiotyypit
SELECT id,
       koodi,
       nimi,
       toimenpidekoodi
FROM sanktiotyyppi;

--name: hae-sanktiotyyppi-koodilla
SELECT id
FROM sanktiotyyppi
WHERE koodi IN (:koodit);

--name: hae-sanktiotyypin-tiedot-koodilla
SELECT id, nimi, toimenpidekoodi, koodi
FROM sanktiotyyppi
WHERE koodi = :koodit
  AND poistettu = false;


--name: hae-sanktion-urakka-id
SELECT urakka
FROM laatupoikkeama lp
         JOIN sanktio s ON lp.id = s.laatupoikkeama
WHERE s.id = :sanktioid;

-- name: hae-sanktio
SELECT s.id             AS id,
       s.perintapvm,
       s.indeksi,
       s.maara,
       s.laatupoikkeama as "laatupoikkeama-id",
       s.toimenpideinstanssi,
       s.tyyppi,
       s.suorasanktio,
       s.ulkoinen_id,
       s.vakiofraasi,
       s.sakkoryhma,
       s.muokattu,
       s.muokkaaja,
       s.luoja,
       s.luotu,
       s.poistettu
FROM sanktio s
WHERE s.poistettu IS NOT TRUE
  AND s.id = :id;

-- name: hae-urakan-sanktiot-analytiikalle
-- Hakee kaikki urakan sanktiot palautettavaksi analytiikalle toteutuneiden kustannusten rajapinnan kautta.
-- Palauttaa myös poistetut sanktiot.
-- Käytetään MH-urakoissa, soveltuu myös vanhojen alueurakoiden tietojen palauttamiseen.
SELECT s.id               AS "sanktio-id",
       s.perintapvm       AS "sanktion-ajankohta",
       s.indeksi          AS "indeksi",
       s.maara            AS "sanktion-maara",
       (SELECT korotus
        FROM sanktion_indeksikorotus(s.perintapvm, s.indeksi, s.maara, u.id,
                                     s.sakkoryhma)) AS "sanktion-maara-indeksikorjattuna",
       s.laatupoikkeama   AS "laatupoikkeama-id",
       tp.id              AS "toimenpide-id",
       s.suorasanktio     AS "suorasanktio",
       s.sakkoryhma       AS "sanktiolaji",
       st.koodi           AS "sanktiotyyppi_koodi",
       st.nimi            AS "sanktiotyyppi_nimi",
       st.toimenpidekoodi AS "sanktiotyyppi_toimenpide-id",
       s.poistettu        AS "poistettu"
FROM sanktio s
         JOIN sanktiotyyppi st on s.tyyppi = st.id
         JOIN toimenpideinstanssi tpi ON s.toimenpideinstanssi = tpi.id
         JOIN toimenpide tp ON tpi.toimenpide = tp.id
         JOIN urakka u ON tpi.urakka = u.id
WHERE u.id = :urakka-id;

-- name: hae-urakan-bonukset-analytiikalle
-- Hakee kaikki urakan bonukset palautettavaksi analytiikalle toteutuneiden kustannusten rajapinnan kautta.
-- Palauttaa myös poistetut bonukset.
-- Käytetään MH-urakoissa, soveltuu myös vanhojen alueurakoiden bonusten palauttamiseen.
SELECT ek.id               AS "bonus-id",
       ek.laskutuskuukausi AS "bonuksen-ajankohta",
       ek.indeksin_nimi    AS "indeksi",
       ek.rahasumma        AS "bonuksen-maara",
       tp.id               AS "toimenpide-id",
       ek.tyyppi           AS "bonustyyppi",
       ek.pvm              AS "bonuksen-kasittelyajankohta",
       ek.kasittelytapa    AS "bonuksen-kasittelytapa",
       ek.poistettu        AS "poistettu"
FROM erilliskustannus ek
         JOIN toimenpideinstanssi tpi ON ek.toimenpideinstanssi = tpi.id
         JOIN toimenpide tp ON tpi.toimenpide = tp.id
         JOIN urakka u ON tpi.urakka = u.id
WHERE u.id = :urakka-id
  AND ek.tyyppi != 'muu';

-- name: hae-urakan-paatokset-analytiikalle
-- Hakee urakan välikatselmukseen liittyvät päätökset palautettavaksi analytiikalle toteutuneiden kustannusten rajapinnan kautta.
-- Käytetään MH-urakoissa.
SELECT id                           AS "paatos-id",
       "hoitokauden-alkuvuosi"      AS "paatoksen-hoitovuosi",
       tyyppi                       AS "paatostyyppi", -- 'tavoitehinnan-ylitys', 'kattohinnan-ylitys', 'tavoitehinnan-alitus', 'lupausbonus', 'lupaussanktio'
       "hinnan-erotus"              AS "paatoksen-tulos_kokonaismaara",
       "urakoitsijan-maksu"         AS "paatoksen-tulos_urakoitsija-maksaa",
       "tilaajan-maksu"             AS "paatoksen-tulos_tilaaja-maksaa",
       siirto                       AS "paatoksen-tulos_siirretaan-seuraavalle-hoitovuodelle",
       "lupaus-tavoitehinta"        AS "paatoksen-tulos_tavoitehinta",
       "lupaus-luvatut-pisteet"     AS "lupausten-tulos_luvatut-pisteet",
       "lupaus-toteutuneet-pisteet" AS "lupausten-tulos_toteutuneet-pisteet",
       kulu_id                      AS "viittaukset-toteutuneisiin-kustannuksiin_kulu-id",
       sanktio_id                   AS "viittaukset-toteutuneisiin-kustannuksiin_sanktio-id",
       erilliskustannus_id          AS "viittaukset-toteutuneisiin-kustannuksiin_bonus-id",
       poistettu                    AS "poistettu"
FROM urakka_paatos up
WHERE "urakka-id" = :urakka-id
ORDER BY "hoitokauden-alkuvuosi", tyyppi;

-- name: hae-urakan-tavoitehinnan-oikaisut-analytiikalle
-- Hakee kaikki välikatselmukseen liittyvät tavoitehinnan oikaisut palautettavaksi analytiikalle toteutuneiden kustannusten rajapinnan kautta.
-- Palauttaa myös poistetuksi merkityt tavoitehinnan oikaisut.
-- Käytetään MH-urakoissa.
SELECT id                      AS "tavoitehinnan-oikaisu_oikaisu-id",
       "hoitokauden-alkuvuosi" AS "tavoitehinnan-oikaisun_hoitovuosi",
       summa                   AS "tavoitehinnan-oikaisun_maara",
       otsikko                 AS "tavoitehinnan-oikaisu_oikaisukategoria",
       selite                  AS "tavoitehinnan-oikaisu_oikaisun-selite",
       poistettu               AS "tavoitehinnan-poistettu"
FROM tavoitehinnan_oikaisu toi
WHERE "urakka-id" = :urakka-id
ORDER BY "hoitokauden-alkuvuosi", otsikko, id;


