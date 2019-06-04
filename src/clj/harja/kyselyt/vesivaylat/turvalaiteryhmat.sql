-- name: vie-turvalaiteryhmatauluun<!
INSERT INTO reimari_turvalaiteryhma
(
  tunnus,
  nimi,
  kuvaus,
  turvalaitteet,
  luoja,
  luotu
)
VALUES (
  :tunnus,
  :nimi,
  :kuvaus,
  :turvalaitteet :: INTEGER [],
  :luoja,
  current_timestamp
)
ON CONFLICT (tunnus)
  DO UPDATE
    SET
      tunnus        = :tunnus,
      nimi          = :nimi,
      kuvaus        = :kuvaus,
      turvalaitteet = :turvalaitteet :: INTEGER [],
      muokkaaja     = :muokkaaja,
      muokattu = current_timestamp;
