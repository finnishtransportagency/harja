-- Lisää indeksi tarkastuksen ajalle
CREATE INDEX CONCURRENTLY tarkastus_aika_idx ON tarkastus (aika);