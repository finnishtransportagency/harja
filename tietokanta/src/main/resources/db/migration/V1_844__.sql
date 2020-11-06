-- Tee indeksi toteuma_materiaali taululle uuden urakka_id kentän perusteella
CREATE INDEX CONCURRENTLY toteuma_materiaali_urakka_poistettu ON toteuma_materiaali (urakka_id, poistettu);
