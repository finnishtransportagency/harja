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
       CASE WHEN l.lupaustyyppi = 'kysely'::lupaustyyppi THEN l.pisteet
           ELSE 0
           END                  AS "kyselypisteet",
       CASE WHEN l.lupaustyyppi != 'kysely'::lupaustyyppi THEN l.pisteet
           ELSE 0
           END                  AS "pisteet",
       l."kirjaus-kkt",
       l."paatos-kk",
       l."joustovara-kkta",
       l.sisalto,
       jsonb_agg(row_to_json(row(vas.kuukausi, vas.vuosi, vas.vastaus, vas."lupaus-vaihtoehto-id",
                                 lv.pisteet, vas."veto-oikeutta-kaytetty", vas."veto-oikeus-aika"))) AS vastaukset
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
