-- name: tallenna-tarjous<!
INSERT INTO tarjous (hoitokauden_alkuvuosi, urakka_id, tarjous_tavoitehinta, tarjous_kattohinta, luoja, luotu)
VALUES (:hoitokauden_alkuvuosi, :urakka_id, :tarjous_tavoitehinta, :tarjous_kattohinta, :luoja, NOW());

-- name: tallenna-tarjouskustannus<!
INSERT INTO tarjous_kustannukset (tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id, tehtavaryhma_id, rahavaraus_id, luoja, luotu)
VALUES (:tarjous_id, :urakka_id, :hoitokauden_alkuvuosi, :summa, :osio::suunnittelu_osio, :tehtava_id, :tehtavaryhma_id, :rahavaraus_id, :luoja, NOW());

-- name: tallenna-tarjouksen-johto-ja-hallintokorvaus<!
INSERT INTO tarjous_johto_ja_hallintokorvaus (tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, johto_ja_hallintokorvaus_toimenkuva_id, tehtavaryhma_id, tehtava_id, luoja, luotu)
VALUES (:tarjous_id, :urakka_id, :hoitokauden_alkuvuosi, :summa, :osio::suunnittelu_osio, :johto_ja_hallintokorvaus_toimenkuva_id, :tehtavaryhma_id, :tehtava_id, :luoja, NOW());

-- name: hae-tarjouksen-tiedot
select t.id as tarjous_id, t.hoitokauden_alkuvuosi, t.urakka_id, t.tarjous_tavoitehinta, t.tarjous_kattohinta,
       (SELECT array_agg(row(tk.id,
           CASE WHEN tk.osio::suunnittelu_osio = 'tavoitehintaiset-rahavaraukset'::suunnittelu_osio THEN r.nimi
                WHEN tk.osio::suunnittelu_osio = 'hankintakustannukset'::suunnittelu_osio THEN 'Kilpailutettavat hankinnat'
                WHEN tk.osio::suunnittelu_osio = 'erillishankinnat'::suunnittelu_osio THEN 'Erillishankinnat'
                WHEN tk.osio::suunnittelu_osio = 'hoidonjohtopalkkio'::suunnittelu_osio THEN 'Hoidonjohtopalkkio'
                ELSE tk.osio::text END,
            tk.summa, tk.osio, tk.tehtava_id, tk.tehtavaryhma_id, tk.rahavaraus_id))
        FROM tarjous_kustannukset tk
                 LEFT JOIN rahavaraus r ON r.id = tk.rahavaraus_id
        WHERE tk.tarjous_id = t.id) as kustannukset,
       (SELECT array_agg(row(tj.id, tj.summa, tj.osio, tj.johto_ja_hallintokorvaus_toimenkuva_id))
        FROM tarjous_johto_ja_hallintokorvaus tj
        WHERE tj.tarjous_id = t.id) as toimenkuvat
from tarjous t
where t.urakka_id = :urakka_id;
