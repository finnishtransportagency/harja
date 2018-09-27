/* Käyttöliittymän suolasakko käytössä-täppä vaikuttaa ainoastaan valittuun hoitokauteen. Käytössätieto koskee kuitenkin
kaikkia hoitokausia. Siksi triggerillä päivitetään kaikki urakkaa koskevat rivit vastaamaan uusinta valintaa. */

CREATE OR REPLACE FUNCTION aseta_suolasakon_kayto_kaikille_hoitokausille()
  RETURNS trigger AS $$
BEGIN
  IF NEW.urakka IS NULL THEN
    RAISE EXCEPTION 'Urakkatieto puuttuu. Suolasakon käyttöä ei voida päivittää.';
  ELSE
    UPDATE suolasakko
    set kaytossa = NEW.kaytossa,
      muokattu = CURRENT_TIMESTAMP,
      muokkaaja = NEW.muokkaaja WHERE
      urakka = NEW.urakka;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_aseta_suoasakon_kaytto_kaikille_hoitokausille
AFTER UPDATE ON suolasakko
FOR EACH ROW
WHEN (OLD.kaytossa IS DISTINCT FROM NEW.kaytossa)
EXECUTE PROCEDURE aseta_suolasakon_kayto_kaikille_hoitokausille();