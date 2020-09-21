-- name: hae-pot2-massat
SELECT m.id, m.nimi, array_agg(ROW(r.id, r.kiviaine_esiintyma))
    FROM pot2_massa m
             LEFT JOIN pot2_massa_runkoaine r ON r.pot2_massa_id = m.id
    WHERE urakka_id = :urakka-id
    GROUP By m.id;
