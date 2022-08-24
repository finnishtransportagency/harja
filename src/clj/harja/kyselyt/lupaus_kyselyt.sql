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
       LEFT JOIN lupaus_sitoutuminen sit ON sit."urakka-id" = :urakka
       JOIN lupaus l ON r.id = l."lupausryhma-id"
       LEFT JOIN lupaus_vastaus vas ON (l.id = vas."lupaus-id" AND vas."urakka-id" = :urakka
                                    AND (concat(vas.vuosi, '-', vas.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE))
       LEFT JOIN lupaus_vaihtoehto lv ON lv.id = vas."lupaus-vaihtoehto-id"
 WHERE r."urakan-alkuvuosi" = :alkuvuosi
GROUP BY l.id, sit.id, r.id;

-- name: hae-lupaus-vaihtoehdot
SELECT id, "lupaus-id", vaihtoehto, pisteet
  FROM lupaus_vaihtoehto
 WHERE "lupaus-id" = :lupaus-id;

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
 :kayttaja-id);

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
