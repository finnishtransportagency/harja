-- Silta ja sulkutyyppisillä kanavakohteilla voi olla sama lähdetunnus. Kahdella sillalla ja kahdella sululla ei voi olla.
CREATE UNIQUE INDEX lahdetunnus_tyyppi_unique_index
  ON kan_kohteenosa (lahdetunnus, tyyppi);