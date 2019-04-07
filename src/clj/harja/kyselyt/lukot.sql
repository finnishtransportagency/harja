-- name: aseta-lukko
SELECT aseta_lukko(:tunniste, :lukko, :aikaraja);

-- name: avaa-lukko
SELECT avaa_lukko(:tunniste);
