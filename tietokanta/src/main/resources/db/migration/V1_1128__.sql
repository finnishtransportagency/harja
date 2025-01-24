CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2021_q1 BEFORE UPDATE ON tarkastus_2021_q1 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2021_q2 BEFORE UPDATE ON tarkastus_2021_q2 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2021_q3 BEFORE UPDATE ON tarkastus_2021_q3 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2021_q4 BEFORE UPDATE ON tarkastus_2021_q4 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2022_q1 BEFORE UPDATE ON tarkastus_2022_q1 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2022_q2 BEFORE UPDATE ON tarkastus_2022_q2 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2022_q3 BEFORE UPDATE ON tarkastus_2022_q3 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2022_q4 BEFORE UPDATE ON tarkastus_2022_q4 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2023_q1 BEFORE UPDATE ON tarkastus_2023_q1 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2023_q2 BEFORE UPDATE ON tarkastus_2023_q2 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2023_q3 BEFORE UPDATE ON tarkastus_2023_q3 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2023_q4 BEFORE UPDATE ON tarkastus_2023_q4 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2024_q1 BEFORE UPDATE ON tarkastus_2024_q1 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2024_q2 BEFORE UPDATE ON tarkastus_2024_q2 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2024_q3 BEFORE UPDATE ON tarkastus_2024_q3 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2024_q4 BEFORE UPDATE ON tarkastus_2024_q4 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2025_q1 BEFORE UPDATE ON tarkastus_2025_q1 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2025_q2 BEFORE UPDATE ON tarkastus_2025_q2 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2025_q3 BEFORE UPDATE ON tarkastus_2025_q3 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2025_q4 BEFORE UPDATE ON tarkastus_2025_q4 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2026_q1 BEFORE UPDATE ON tarkastus_2026_q1 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2026_q2 BEFORE UPDATE ON tarkastus_2026_q2 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2026_q3 BEFORE UPDATE ON tarkastus_2026_q3 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();
CREATE TRIGGER tg_muodosta_tarkastuksen_envelope_2026_q4 BEFORE UPDATE ON tarkastus_2026_q4 FOR EACH ROW EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();

-- Tämän ajamalla saadaan päivitettyä vanhojen reittien envelope, ja täten niiden näkyvyys tilannekuvassa.
-- Ei kuitenkaan ajeta migraatiossa sen hitauden takia.
-- UPDATE tarkastus SET envelope = st_envelope(sijainti);
