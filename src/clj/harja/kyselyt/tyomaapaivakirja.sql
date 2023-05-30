-- name: hae-tiedot
-- Mock data
SELECT
   id, 
   sampoid, 
   nimi, 
   alkupvm, 
   loppupvm, 
   sopimustyyppi, 
   floor(random() * 3 + 0)::int AS tila 
FROM urakka ORDER BY alkupvm DESC LIMIT 50;


-- name: hae-paivakirjalistaus
SELECT t.id as tyomaapaivakirja_id, t.urakka_id, u.nimi as "urakka-nimi",
       --t.paivamaara::DATE,
       d::DATE as paivamaara,
       CASE
           WHEN t.luotu IS NULL THEN 'puuttuu'
           WHEN t.luotu BETWEEN d::DATE and d::DATE + interval '14 day' THEN 'ok'
           WHEN t.luotu > d::DATE + interval '14 day' THEN 'myohassa'
       END as tila,
       count(tk.id) as "kommenttien-maara",
       t_kalusto.versio,
       t.luotu, t.luoja, t.muokattu, t.muokkaaja
  FROM generate_series(:alkuaika::DATE, :loppuaika::DATE, '1 day'::interval) d
       LEFT JOIN tyomaapaivakirja t ON t.paivamaara = d::DATE AND t.urakka_id = :urakka-id
       LEFT JOIN urakka u ON t.urakka_id = u.id
       LEFT JOIN tyomaapaivakirja_kommentti tk on t.id = tk.tyomaapaivakirja_id
       LEFT JOIN (SELECT versio, tyomaapaivakirja_id FROM tyomaapaivakirja_kalusto ORDER BY versio desc) t_kalusto on t.id = t_kalusto.tyomaapaivakirja_id
 GROUP BY t.id, u.nimi, d, t_kalusto.versio
 ORDER BY paivamaara ASC;


-- name: hae-paivakirja
SELECT t.id as tyomaapaivakirja_id, t.urakka_id, u.nimi as "urakka-nimi",
       t.paivamaara::DATE,
       (SELECT array_agg(row(aloitus, lopetus, nimi))
        FROM tyomaapaivakirja_paivystaja
        WHERE versio = :versio AND tyomaapaivakirja_id = :tyomaapaivakirja_id) as paivystajat,
       (SELECT array_agg(row(aloitus, lopetus, nimi))
        FROM tyomaapaivakirja_tyonjohtaja
        WHERE versio = :versio AND tyomaapaivakirja_id = :tyomaapaivakirja_id) as tyonjohtajat,
       (SELECT array_agg(row(havaintoaika, aseman_tunniste, aseman_tietojen_paivityshetki, ilman_lampotila, tien_lampotila, keskituuli, sateen_olomuoto, sadesumma))
        FROM tyomaapaivakirja_saaasema
        WHERE versio = :versio AND tyomaapaivakirja_id = :tyomaapaivakirja_id)  as "saa-asemat",
       (SELECT array_agg(row(havaintoaika, paikka, kuvaus))
        FROM tyomaapaivakirja_poikkeussaa
        WHERE versio = :versio AND tyomaapaivakirja_id = :tyomaapaivakirja_id) as poikkeussaat,
       (SELECT array_agg(row(aloitus, lopetus, tyokoneiden_lkm, lisakaluston_lkm))
        FROM tyomaapaivakirja_kalusto
        WHERE versio = :versio AND tyomaapaivakirja_id = :tyomaapaivakirja_id) as kalustot,
       (SELECT array_agg(row(tyyppi::TEXT, kuvaus))
        FROM tyomaapaivakirja_tapahtuma
        WHERE versio = :versio AND tyomaapaivakirja_id = :tyomaapaivakirja_id) as tapahtumat,
       (SELECT array_agg(row(kuvaus, aika))
        FROM tyomaapaivakirja_toimeksianto
        WHERE versio = :versio AND tyomaapaivakirja_id = :tyomaapaivakirja_id) as toimeksiannot,
       t.luotu, t.luoja, t.muokattu, t.muokkaaja
  FROM tyomaapaivakirja t
       JOIN urakka u ON t.urakka_id = u.id
 WHERE t.id = :tyomaapaivakirja_id
 GROUP BY t.id, u.nimi;

-- name: hae-paivakirjan-tehtavat
SELECT ttt.tyyppi, ttt.aloitus, ttt.lopetus, array_agg(tehtava.nimi) as tehtavat
  FROM tyomaapaivakirja_tieston_toimenpide ttt
       LEFT JOIN lateral unnest(ttt.tehtavat) t on true
       LEFT JOIN tehtava ON tehtava.id = t
 WHERE ttt.versio = :versio
   AND ttt.tyomaapaivakirja_id = :tyomaapaivakirja_id
   AND ttt.tyyppi = 'yleinen'::tyomaapaivakirja_toimenpide_tyyppi
 GROUP BY ttt.id;

-- name: hae-paivakirjan-toimenpiteet
SELECT ttt.tyyppi, ttt.aloitus, ttt.lopetus, toimenpiteet as toimenpiteet
FROM tyomaapaivakirja_tieston_toimenpide ttt
WHERE ttt.versio = :versio
  AND ttt.tyomaapaivakirja_id = :tyomaapaivakirja_id
  AND ttt.tyyppi = 'muu'::tyomaapaivakirja_toimenpide_tyyppi
GROUP BY ttt.id;

-- name: lisaa-tyomaapaivakirja<!
INSERT INTO tyomaapaivakirja (urakka_id, paivamaara, ulkoinen_id, luotu, luoja)
values (:urakka_id, :paivamaara, :ulkoinen-id, now(), :kayttaja);

-- name: paivita-tyomaapaivakirja<!
UPDATE tyomaapaivakirja SET paivamaara = :paivamaara, ulkoinen_id = :ulkoinen-id, muokattu = now(), muokkaaja = :kayttaja
 WHERE id = :id;

-- name: lisaa-kalusto<!
INSERT INTO tyomaapaivakirja_kalusto (urakka_id, tyomaapaivakirja_id, versio, aloitus, lopetus, tyokoneiden_lkm, lisakaluston_lkm)
VALUES (:urakka_id, :tyomaapaivakirja_id, :versio, :aloitus, :lopetus, :tyokoneiden-lkm, :lisakaluston-lkm);

-- name: lisaa-paivystaja<!
INSERT INTO tyomaapaivakirja_paivystaja (urakka_id, tyomaapaivakirja_id, versio, aloitus, lopetus, nimi)
VALUES (:urakka_id, :tyomaapaivakirja_id, :versio, :aloitus, :lopetus, :nimi);

-- name: lisaa-tyonjohtaja<!
INSERT INTO tyomaapaivakirja_tyonjohtaja (urakka_id, tyomaapaivakirja_id, versio, aloitus, lopetus, nimi)
VALUES (:urakka_id, :tyomaapaivakirja_id, :versio, :aloitus, :lopetus, :nimi);

-- name: lisaa-saatiedot<!
INSERT INTO tyomaapaivakirja_saaasema (urakka_id, tyomaapaivakirja_id, versio, havaintoaika, aseman_tunniste,
                                  aseman_tietojen_paivityshetki, ilman_lampotila, tien_lampotila,
                                  keskituuli, sateen_olomuoto, sadesumma)
VALUES (:urakka_id, :tyomaapaivakirja_id, :versio, :havaintoaika, :aseman-tunniste, :aseman-tietojen-paivityshetki,
        :ilman-lampotila, :tien-lampotila, :keskituuli, :sateen-olomuoto, :sadesumma);

-- name: lisaa-poikkeussaa<!
INSERT INTO tyomaapaivakirja_poikkeussaa (urakka_id, tyomaapaivakirja_id, versio, havaintoaika, paikka, kuvaus)
VALUES (:urakka_id, :tyomaapaivakirja_id, :versio, :havaintoaika, :paikka, :kuvaus);

--name: lisaa-tie-toimenpide<!
INSERT INTO tyomaapaivakirja_tieston_toimenpide (urakka_id, tyomaapaivakirja_id, versio, tyyppi, aloitus, lopetus, tehtavat,
                                                 toimenpiteet)
VALUES (:urakka_id, :tyomaapaivakirja_id, :versio, :tyyppi::tyomaapaivakirja_toimenpide_tyyppi, :aloitus, :lopetus,
        :tehtavat::integer[], :toimenpiteet::text[]);

--name: lisaa-tapahtuma<!
INSERT INTO tyomaapaivakirja_tapahtuma (urakka_id, tyomaapaivakirja_id, versio, tyyppi, kuvaus)
VALUES (:urakka_id, :tyomaapaivakirja_id, :versio, :tyyppi::tyomaapaivakirja_tapahtumatyyppi, :kuvaus);

-- name: lisaa-toimeksianto<!
INSERT INTO tyomaapaivakirja_toimeksianto (urakka_id, tyomaapaivakirja_id, versio, kuvaus, aika)
VALUES (:urakka_id, :tyomaapaivakirja_id, :versio, :kuvaus, :aika);
