-- name: yrita-asetaa-lukko
SELECT pg_try_advisory_lock(:lukko-id);

-- name: vapauta-lukkovapauta
SELECT pg_advisory_unlock(:lukko-id);

