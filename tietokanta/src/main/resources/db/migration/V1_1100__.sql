-- Poistetaan kannasta bugin takia aiheutuneet rivit, joissa on NULL arvoja.
DELETE FROM urakanvastuuhenkilo WHERE kayttajatunnus IS NULL;
