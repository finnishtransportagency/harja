-- name: aseta-lukko
SELECT aseta_lukko(:tunniste, :lukko, :aikaraja);

-- name: avaa-lukko
SELECT avaa_lukko(:tunniste);

-- name: aseta-tiedoituslukko
SELECT pg_advisory_lock(:lukkoid);

-- name: avaa-tiedoituslukko
SELECT pg_advisory_unlock(:lukkoid);