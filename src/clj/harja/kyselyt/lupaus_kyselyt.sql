-- name: hae-lupaus
-- row-fn: muunna-lupaus
SELECT id,
       jarjestys,
       "lupausryhma-id",
       "urakka-id",
       lupaustyyppi,
       pisteet,
       "kirjaus-kkt",
       "paatos-kk",
       "joustovara-kkta",
       sisalto,
       "urakan-alkuvuosi",
       luotu
  FROM lupaus
 WHERE id = :id;

-- name: hae-urakan-lupaustiedot
-- row-fn: muunna-lupaus
SELECT l.id                     AS "lupaus-id",
       sit.id                   AS "sitoutuminen-id",
       sit.pisteet              AS "sitoutuminen-pisteet",
       r.id                     AS "lupausryhma-id",
       r.otsikko                AS "lupausryhma-otsikko",
       r.jarjestys              AS "lupausryhma-jarjestys",
       r."urakan-alkuvuosi"     AS "lupausryhma-alkuvuosi",

       -- lupaus
       l.lupaustyyppi,
       l.jarjestys              AS "lupaus-jarjestys",
       CASE WHEN (l.lupaustyyppi = 'kysely'::lupaustyyppi OR l.lupaustyyppi = 'monivalinta'::lupaustyyppi) THEN l.pisteet
           ELSE 0
           END                  AS "kyselypisteet",
       CASE WHEN (l.lupaustyyppi != 'kysely'::lupaustyyppi AND l.lupaustyyppi != 'monivalinta'::lupaustyyppi) THEN l.pisteet
           ELSE 0
           END                  AS "pisteet",
       l."kirjaus-kkt",
       l."paatos-kk",
       l."joustovara-kkta",
       l.kuvaus,
       l.sisalto,
       jsonb_agg(row_to_json(row(vas.id, vas.kuukausi, vas.vuosi, vas.vastaus, vas."lupaus-vaihtoehto-id",
                                 lv.pisteet, vas."veto-oikeutta-kaytetty", vas."veto-oikeus-aika", vas.paatos))) AS vastaukset
  FROM lupausryhma r
       LEFT JOIN lupaus_sitoutuminen sit ON sit."urakka-id" = :urakka AND sit.poistettu IS FALSE
       JOIN lupaus l ON r.id = l."lupausryhma-id"
       LEFT JOIN lupaus_vastaus vas ON (l.id = vas."lupaus-id" AND vas."urakka-id" = :urakka
                                    AND (concat(vas.vuosi, '-', vas.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE))
       LEFT JOIN lupaus_vaihtoehto lv ON lv.id = vas."lupaus-vaihtoehto-id"
       JOIN lupausryhma_urakka lu ON r.id = lu."lupausryhma_id"
 WHERE lu."urakka_id" = :urakka
GROUP BY l.id, sit.id, r.id
ORDER BY l.jarjestys, r.jarjestys;

-- name: hae-lupaus-vaihtoehdot
 SELECT
	lv.id, 
	lv."lupaus-id", 
	lv.vaihtoehto, 
	lv.pisteet, 
	lv."vaihtoehto-askel", 
	lv."vaihtoehto-seuraava-ryhma-id",
	lvr."ryhma-otsikko"
FROM
	lupaus_vaihtoehto lv
left JOIN lupaus_vaihtoehto_ryhma lvr on
	lv."vaihtoehto-ryhma-otsikko-id" = lvr.id
WHERE
	lv."lupaus-id" = :lupaus-id
  ORDER BY lv.id;

-- name: hae-lupaus-vaihtoehto
SELECT id, "lupaus-id", vaihtoehto, pisteet
  FROM lupaus_vaihtoehto
 WHERE id = :id;

-- name: lisaa-urakan-luvatut-pisteet<!
INSERT INTO lupaus_sitoutuminen ("urakka-id", pisteet, luoja)
 VALUES (:urakka-id, :pisteet, :kayttaja);

-- name: paivita-urakan-luvatut-pisteet<!
UPDATE lupaus_sitoutuminen
   SET pisteet = :pisteet, muokattu = NOW(), muokkaaja = :kayttaja
 WHERE id = :id;

-- name: hae-lupauksen-urakkatieto
SELECT "urakka-id"
  FROM lupaus_sitoutuminen
 WHERE id = :id;

-- name: hae-lupaus-vastaus
SELECT id,
       "lupaus-id",
       "urakka-id",
       kuukausi,
       vuosi,
       paatos,
       vastaus,
       "lupaus-vaihtoehto-id",
       "veto-oikeutta-kaytetty",
       "veto-oikeus-aika",
       poistettu,
       muokkaaja,
       muokattu,
       luoja,
       luotu
  FROM lupaus_vastaus
 WHERE id = :id;

-- name: paivita-lupaus-vastaus!
UPDATE lupaus_vastaus
   SET vastaus                = :vastaus,
       "lupaus-vaihtoehto-id" = :lupaus-vaihtoehto-id,
       muokkaaja              = :muokkaaja,
       muokattu               = NOW()
 WHERE id = :id;

-- name: lisaa-lupaus-vastaus<!
INSERT INTO lupaus_vastaus
("lupaus-id",
 "urakka-id",
 kuukausi,
 vuosi,
 paatos,
 vastaus,
 "lupaus-vaihtoehto-id",
 luoja)
VALUES
(:lupaus-id,
 :urakka-id,
 :kuukausi,
 :vuosi,
 :paatos,
 :vastaus,
 :lupaus-vaihtoehto-id,
 :luoja);

-- name: kommentit
SELECT lk."lupaus-id",
       lk.vuosi,
       lk.kuukausi,
       k.id,
       k.tekija,
       CASE WHEN k.poistettu THEN null ELSE k.kommentti END as kommentti,
       k.liite,
       k.luoja,
       k.luotu,
       k.muokkaaja,
       k.muokattu,
       k.poistettu,
       l.etunimi,
       l.sukunimi
  FROM lupaus_kommentti lk
           JOIN kommentti k on lk."kommentti-id" = k.id
           JOIN kayttaja l ON k.luoja = l.id
 WHERE lk."lupaus-id" = :lupaus-id
   AND lk."urakka-id" = :urakka-id
   AND (lk.vuosi, lk.kuukausi) BETWEEN (:vuosi-alku, :kuukausi-alku) AND (:vuosi-loppu, :kuukausi-loppu)
 ORDER BY lk.vuosi, lk.kuukausi, k.luotu;

-- name: lisaa-lupaus-kommentti<!
INSERT
  INTO lupaus_kommentti ("lupaus-id", "urakka-id", kuukausi, vuosi, "kommentti-id")
  VALUES (:lupaus-id, :urakka-id, :kuukausi, :vuosi, :kommentti-id);

-- name: poista-kayttajan-oma-kommentti!
UPDATE kommentti
   SET poistettu = true,
       muokkaaja = :kayttaja,
       muokattu  = current_timestamp
 WHERE id = :id
   AND luoja = :kayttaja;

-- name: hae-kaynnissa-olevat-lupaus-urakat
-- Hae ei-poistetut teiden-hoito -tyyppiset urakat, joiden alkuvuosi on annettu alkuvuosi.
-- Urakan täytyy olla käynnissä annettuna hetkenä, tai päättynyt korkeintaan 2 kk sitten.
SELECT id, nimi, hallintayksikko, sampoid FROM urakka
WHERE alkupvm = :alkupvm
  AND tyyppi = 'teiden-hoito'::urakkatyyppi
  AND poistettu = FALSE
-- Onko käynnissä
AND alkupvm <= :nykyhetki::TIMESTAMP
AND loppupvm > (date_trunc('month',:nykyhetki::TIMESTAMP) - '2 months'::interval);

-- name: tallenna-kuukausittaiset-pisteet<!
-- vuonna 2019/2020 alkaneille urakoille ei tallenneta lupauksia, vaan ennuste/toteuma pisteet kuukausittain
INSERT INTO lupaus_pisteet ("urakka-id", kuukausi, vuosi, pisteet, tyyppi, luoja, luotu)
VALUES (:urakka-id, :kuukausi, :vuosi, :pisteet, :tyyppi::lupaus_pisteet_tyyppi, :kayttaja, NOW());

-- name: paivita-kuukausittaiset-pisteet<!
UPDATE lupaus_pisteet
   SET pisteet = :pisteet,
       muokkaaja = :kayttaja,
       muokattu = NOW()
WHERE id = :id;

-- name: poista-kuukausittaiset-pisteet<!
DELETE FROM lupaus_pisteet lp
 WHERE id = :id
   AND "urakka-id" = :urakka-id;

-- name: hae-kuukausittaiset-pisteet
-- Haetaan urakalle pisteet lokakuu -> seuraavan vuoden syyskuu.
SELECT lp.id, lp."urakka-id", lp.kuukausi, lp.vuosi, lp.pisteet, lp.tyyppi
  FROM lupaus_pisteet lp
 WHERE lp."urakka-id" = :urakka-id
   AND (concat(lp.vuosi, '-', lp.kuukausi, '-01')::DATE
        BETWEEN concat(:hk-alkuvuosi,'-10-01')::DATE
        AND (concat(:hk-alkuvuosi,'-09-30')::DATE + ' 1 years'::interval)::DATE);

-- name: hae-sitoutumistiedot
SELECT lsit.id, lsit.pisteet, lsit."urakka-id", lsit.luotu
  FROM lupaus_sitoutuminen lsit
 WHERE lsit."urakka-id" = :urakka-id
   AND lsit.poistettu IS FALSE;

-- name: hae-kuukausivastaus
-- single?: true
SELECT lp.id, lp.pisteet, lp.kuukausi, lp.vuosi, lp."urakka-id", lp.luoja, lp.luotu, lp.muokkaaja, lp.muokattu
  FROM lupaus_pisteet lp
 WHERE lp.id = :id;

-- name: hae-indeksikorotus-summalle
SELECT korotus FROM sanktion_indeksikorotus(:pvm::DATE, :indeksi,:maara::NUMERIC, :urakka-id::INTEGER, :sanktiolaji::sanktiolaji);


-- name: hae-puuttuvat-urakka-linkitykset
SELECT u.id,
  u.nimi
FROM urakka u
WHERE u.tyyppi = 'teiden-hoito'
  AND EXTRACT(
    YEAR
    FROM u.alkupvm
  ) >= 2021
EXCEPT
SELECT DISTINCT 
  urakka_id,
  u.nimi
FROM lupausryhma_urakka lu
  JOIN urakka u ON lu.urakka_id = u.id
WHERE u.tyyppi = 'teiden-hoito';

-- name: hae-rivin-tunnistin-selitteet
SELECT DISTINCT 
  lr."rivin-tunnistin-selite",
  lr."urakan-alkuvuosi"
FROM lupausryhma lr
ORDER BY lr."urakan-alkuvuosi";

-- name: hae-kategorian-urakat
SELECT
	DISTINCT
	lu.urakka_id,
	u.nimi
FROM
	lupausryhma lr
LEFT JOIN lupausryhma_urakka lu ON
	lr.id = lu.lupausryhma_id
LEFT JOIN urakka u ON
	lu.urakka_id = u.id
WHERE (lr."rivin-tunnistin-selite" = :rivin-tunnistin-selite OR :rivin-tunnistin-selite::VARCHAR IS NULL)
  AND lr."urakan-alkuvuosi" = :urakan-alkuvuosi;

-- name: hae-urakan-lupaukset
-- row-fn: muunna-lupaus
SELECT
  r.otsikko,
  r.jarjestys  AS "lupausryhma-jarjestys",
  l.id  AS "lupaus-id",
  l.jarjestys  AS "lupaus-jarjestys",
	l.kuvaus,
  l.pisteet,
  l.sisalto,
  l.lupaustyyppi,
  l."kirjaus-kkt",
  l."joustovara-kkta",
  l."paatos-kk"
FROM
	lupausryhma r
JOIN lupaus l ON
	r.id = l."lupausryhma-id"
JOIN lupausryhma_urakka lu ON
	r.id = lu."lupausryhma_id"
WHERE
	lu."urakka_id" = :urakka-id
ORDER BY r.jarjestys, l.jarjestys;

-- name: hae-lupauksen-hoitovuoden-kirjauskuukaudet
-- row-fn: muunna-lupaus
SELECT id,
       "lupaus-id",
       "hoitovuosi-nro",
       "kirjaus-kkt",
       "paatos-kk",
       "joustovara-kkta"
  FROM lupaus_hoitovuoden_kirjauskuukaudet
 WHERE "lupaus-id" = :lupaus-id
   AND "hoitovuosi-nro" = :hoitovuosi-nro;

-- name: hae-kustannusennuste-id
-- single?: true
SELECT id 
FROM lupaus_kustannusennuste ke
WHERE ke."lupaus-id" = :lupaus-id
  AND ke."urakka-id" = :urakka-id
  AND ke.maarapaiva = :maarapaiva;

-- name: hae-kustannusennuste
-- single?: true  
SELECT ke.id,
       ke."lupaus-id",
       ke."urakka-id", 
       ke.hoitovuosi_alkuvuosi,
       ke.maarapaiva,
       ke.ennustettu_tavoitehinta AS tavoitehinta,
       ke.ennustetut_kustannukset AS "toteutuneet-kustannukset",
       ke.syotetty_pvm,
       ke.lasketut_pisteet as pisteet,
       ke.luoja,
       ke.muokkaaja,
       ke.luotu,
       ke.muokattu
FROM lupaus_kustannusennuste ke
WHERE ke."lupaus-id" = :lupaus-id
  AND ke."urakka-id" = :urakka-id  
  AND ke.maarapaiva = :maarapaiva;

-- name: lisaa-kustannusennuste<!
INSERT INTO lupaus_kustannusennuste 
  ("lupaus-id", "urakka-id", hoitovuosi_alkuvuosi, maarapaiva, 
   ennustettu_tavoitehinta, ennustetut_kustannukset, syotetty_pvm, 
   lasketut_pisteet, luoja)
VALUES (:lupaus-id, :urakka-id, :hoitovuosi-alkuvuosi, :maarapaiva,
        :tavoitehinta, :toteutuneet-kustannukset, :syotetty-pvm,
        :pisteet, :kayttaja);

-- name: paivita-kustannusennuste<!
UPDATE lupaus_kustannusennuste
SET ennustettu_tavoitehinta = :tavoitehinta,
    ennustetut_kustannukset = :toteutuneet-kustannukset,
    syotetty_pvm = :syotetty-pvm,
    lasketut_pisteet = :pisteet,
    muokkaaja = :kayttaja,
    muokattu = NOW()
WHERE id = :id;

-- name: hae-lupauksen-kaikki-kustannusennusteet
SELECT ke.id,
       ke."lupaus-id",
       ke."urakka-id", 
       ke.hoitovuosi_alkuvuosi,
       ke.maarapaiva,
       ke.ennustettu_tavoitehinta AS tavoitehinta,
       ke.ennustetut_kustannukset AS "toteutuneet-kustannukset",
       ke.syotetty_pvm,
       ke.lasketut_pisteet as pisteet,
       ke.luoja,
       ke.muokkaaja,
       ke.luotu,
       ke.muokattu,
       EXTRACT(MONTH FROM ke.maarapaiva) as maarapaiva_kk
FROM lupaus_kustannusennuste ke
WHERE ke."lupaus-id" = :lupaus-id
  AND ke."urakka-id" = :urakka-id
  AND ke.hoitovuosi_alkuvuosi = :hoitokauden-alkuvuosi
ORDER BY ke.maarapaiva;

-- name: hae-kustannusennuste-kuukausi-pisterajat  
-- single?: true
-- Hakee kuukauden pisterajat JSON-muodossa uudesta taulusta
SELECT kp.pisterajat
FROM lupaus_kustannusennuste_kuukausi_pisteet kp
WHERE kp."urakan-alkuvuosi" = :urakan-alkuvuosi
  AND kp.kuukausi = :kuukausi  
ORDER BY kp.paiva;

-- name: hae-valikatselmuksen-vahvistetut-kustannusennusteet
SELECT pty.tavoitehinta AS "vahvistettu-tavoitehinta",
       pty.toteutuneet_kustannukset AS "vahvistetut-toteutuneet-kustannukset",
       pty.hoitokauden_alkuvuosi,
       pta.hoitokauden_lopun_tavoitehinta AS "alitus-tavoitehinta", 
       pta.toteutuneet_kustannukset AS "alitus-toteutuneet-kustannukset",
       pta.hoitokauden_alkuvuosi AS "alitus-hoitokauden-alkuvuosi"
FROM paatos_tavoitehinta_ylitys pty
FULL OUTER JOIN paatos_tavoitehinta_alitus pta ON (
    pty.urakkaid = pta.urakkaid 
    AND pty.hoitokauden_alkuvuosi = pta.hoitokauden_alkuvuosi
)
WHERE (pty.urakkaid = :urakka-id OR pta.urakkaid = :urakka-id)
  AND (pty.hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi OR pta.hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi)
  AND (pty.poistettu = FALSE OR pty.poistettu IS NULL)
  AND (pta.poistettu = FALSE OR pta.poistettu IS NULL);

-- name: hae-urakan-kustannusennuste-lupaukset
-- Hakee urakan kustannusennuste-lupaukset hoitokaudelle
SELECT l.id AS "lupaus-id",
       l.kuvaus,
       l.sisalto,
       l.jarjestys,
       ke.id AS "kustannusennuste-id",
       ke.ennustettu_tavoitehinta,
       ke.ennustetut_kustannukset,
       ke.maarapaiva,
       ke.lasketut_pisteet
FROM lupaus l
JOIN lupaus_kustannusennuste ke ON l.id = ke."lupaus-id"
WHERE l.lupaustyyppi = 'kustannusennuste'::lupaustyyppi
  AND ke."urakka-id" = :urakka-id
  AND ke.hoitovuosi_alkuvuosi = :hoitokauden-alkuvuosi;

-- name: tallenna-lopputilanne!
-- Tallentaa hoitovuoden lopputilanteen
INSERT INTO lupaus_hoitovuosi_lopputilanne
       ("urakka-id", hoitovuosi_alkuvuosi, lopullinen_tavoitehinta, 
        lopulliset_kustannukset, valikatselmus_pvm, vahvistaja, vahvistettu)
VALUES (:urakka-id, :hoitovuosi-alkuvuosi, :lopullinen-tavoitehinta,
        :lopulliset-kustannukset, :valikatselmus-pvm, :vahvistaja, NOW())
    ON CONFLICT ("urakka-id", hoitovuosi_alkuvuosi)
    DO UPDATE SET
        lopullinen_tavoitehinta = EXCLUDED.lopullinen_tavoitehinta,
        lopulliset_kustannukset = EXCLUDED.lopulliset_kustannukset,
        valikatselmus_pvm = EXCLUDED.valikatselmus_pvm,
        vahvistaja = EXCLUDED.vahvistaja,
        vahvistettu = NOW();

-- name: hae-hoitovuoden-lopputilanne
-- Hakee hoitovuoden lopputilanteen
SELECT lopullinen_tavoitehinta,
       lopulliset_kustannukset,
       valikatselmus_pvm,
       vahvistaja,
       vahvistettu
FROM lupaus_hoitovuosi_lopputilanne
WHERE "urakka-id" = :urakka-id
  AND hoitovuosi_alkuvuosi = :hoitovuosi-alkuvuosi;

-- name: paivita-kustannusennuste-lopulliset-pisteet!
-- Päivittää kustannusennusteen lopulliset pisteet
UPDATE lupaus_kustannusennuste
SET ennustettu_tavoitehinta = :ennustettu-tavoitehinta,
    ennustetut_kustannukset = :ennustetut-kustannukset,
    lasketut_pisteet = :lasketut-pisteet,
    tarkkuus_prosentti = :tarkkuus-prosentti,
    laskentakaava_versio = :laskentakaava-versio,
    laskentakaava_teksti = :laskentakaava-teksti,
    laskentakaava_parametrit = :laskentakaava-parametrit::jsonb,
    laskentakaava_vaiheet = :laskentakaava-vaiheet::jsonb,
    muokkaaja = :muokkaaja,
    muokattu = NOW()
WHERE id = :kustannusennuste-id;

-- name: onko-kustannusennuste-pisteet-laskettu
SELECT 
  COUNT(*) as yhteensa,
  COUNT(ke.lasketut_pisteet) as laskettu_pisteet,
  CASE 
    WHEN COUNT(*) = 0 THEN false
    WHEN COUNT(ke.lasketut_pisteet) = COUNT(*) THEN true 
    ELSE false 
  END as kaikki_laskettu
FROM lupaus_kustannusennuste ke
INNER JOIN lupaus l ON l.id = ke."lupaus-id"  
WHERE ke."urakka-id" = :urakka-id
  AND ke.hoitovuosi_alkuvuosi = :hoitokauden-alkuvuosi
  AND l.lupaustyyppi = 'kustannusennuste';

-- name: hae-kustannusennuste-maarapaivat
-- Hakee kustannusennusteen määräpäivätiedot urakan alkuvuoden perusteella
SELECT kuukausi, 
       paiva, 
       kuvaus,
       MAKE_DATE(:hoitokauden-alkuvuosi + CASE WHEN kuukausi >= 10 THEN 0 ELSE 1 END, kuukausi, paiva) AS maarapaiva_pvm
FROM lupaus_kustannusennuste_kuukausi_pisteet 
WHERE "urakan-alkuvuosi" = :urakan-alkuvuosi
ORDER BY kuukausi;