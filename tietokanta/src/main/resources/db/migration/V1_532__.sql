-- Ylläpidon muun toteuman hinnan etumerkin tarkistus
ALTER TABLE yllapito_muu_toteuma ADD CONSTRAINT ei_negatiivinen_hinta CHECK ((tyyppi = 'muu' AND hinta >= 0) OR tyyppi != 'muu');
