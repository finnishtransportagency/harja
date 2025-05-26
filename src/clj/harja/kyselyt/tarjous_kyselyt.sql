-- name: tallenna-tarjous<!
INSERT INTO tarjous (hoitokauden_alkuvuosi, urakka_id, tarjous_tavoitehinta, tarjous_kattohinta, luoja, luotu)
VALUES (:hoitokauden_alkuvuosi, :urakka_id, :tarjous_tavoitehinta, :tarjous_kattohinta, :luoja, NOW());

-- name: tallenna-tarjouskustannus<!
INSERT INTO tarjous_kustannukset (tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id, tehtavaryhma_id, rahavaraus_id, luoja, luotu)
VALUES (:tarjous_id, :urakka_id, :hoitokauden_alkuvuosi, :summa, :osio::suunnittelu_osio, :tehtava_id, :tehtavaryhma_id, :rahavaraus_id, :luoja, NOW());

-- name: tallenna-tarjouksen-johto-ja-hallintokorvaus<!
INSERT INTO tarjous_johto_ja_hallintokorvaus (tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, johto_ja_hallintokorvaus_toimenkuva_id, tehtavaryhma_id, tehtava_id, luoja, luotu)
VALUES (:tarjous_id, :urakka_id, :hoitokauden_alkuvuosi, :summa, :osio::suunnittelu_osio, :johto_ja_hallintokorvaus_toimenkuva_id, :tehtavaryhma_id, :tehtava_id, :luoja, NOW());

