-- Laskutusyhteenvedon toteumaindeksi
CREATE INDEX CONCURRENTLY toteuma_ty_ur_alk_idx ON toteuma (tyyppi, urakka, alkanut);
