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
INSERT INTO tyomaapaivakirja_saa (urakka_id, tyomaapaivakirja_id, versio, havaintoaika, aseman_tunniste,
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
