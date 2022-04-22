-- Poistetaan tapahtuma_tiedot tauluun liittyvät triggerit, jotka hallinnoivat taulun sisältöä.
-- tapahtuma_tiedot taulun sisältö siivotaan tunnin välein ajastetulla tehtävällä ihan koodista

DROP TRIGGER tg_poista_vanhat_tapahtumat ON tapahtuman_tiedot;
DROP TRIGGER tg_esta_tapahtumien_muokkaus_ja_poisto ON tapahtuman_tiedot;
