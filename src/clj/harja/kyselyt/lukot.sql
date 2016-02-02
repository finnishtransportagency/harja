-- name: aseta-lukko
SELECT aseta_lukko(:tunniste, :lukko, :aikaraja);

-- name: avaa-lukko
SELECT avaa_lukko(:tunniste);

-- name: aseta-tietokantalukko
SELECT pg_advisory_lock(:lukkoid);

-- name: avaa-tietokantalukko
SELECT pg_advisory_unlock(:lukkoid);