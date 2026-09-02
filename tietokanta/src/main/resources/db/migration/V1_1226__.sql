-- Lisää mahdollisuus estää Sampo lähetykset
ALTER TABLE urakka_parametrit
  ADD COLUMN maksuera_lahetys_sampo BOOLEAN DEFAULT TRUE,
  ADD COLUMN kustannussuunnitelma_lahetys_sampo BOOLEAN DEFAULT TRUE;

COMMENT ON COLUMN urakka_parametrit.maksuera_lahetys_sampo IS 'Määrittää, lähetetäänkö maksuerätietoja Sampoon';
COMMENT ON COLUMN urakka_parametrit.kustannussuunnitelma_lahetys_sampo IS 'Määrittää, lähetetäänkö kustannussuunnitelmatietoja Sampoon';
