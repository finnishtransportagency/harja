-- Ylläpidon muun toteuman hinnan etumerkin tarkistus
ALTER TABLE yllapito_muu_toteuma ADD CONSTRAINT ennuste_tai_maara CHECK
(toteutunut_maara IS NOT NULL OR ennustettu_maara IS NOT NULL);