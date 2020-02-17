-- Päivittää sopimuksien materiaalimäärät välimuistitaulussa.
-- Hakee päivitettävät sopimukset annettujen parametrien perusteella. Vain voimassaolevat urakat.
create or replace function paivita_sopimuksien_materiaalin_kaytto(urakkatyyppi urakkatyyppi, urakkaorganisaatio integer) returns void
  language plpgsql
as
$$
DECLARE
  sop RECORD;
BEGIN

  -- Käytä funktiota apuna, kun täytyy päivittää monen urakan materiaalitiedot. Hakuehtoja ja käsittelyä saa tarvittaessa monimutkaistaa.

  RAISE NOTICE 'Aloitetaan urakoitsijan % urakoiden käsittely.', urakkaorganisaatio;

  FOR sop IN SELECT * FROM sopimus WHERE urakka IN
                                          (SELECT id FROM urakka WHERE tyyppi = urakkatyyppi AND urakoitsija = urakkaorganisaatio AND loppupvm > current_timestamp)
    LOOP
      PERFORM paivita_koko_sopimuksen_materiaalin_kaytto(sop.id);
      RAISE NOTICE 'Päivitetty sopimus % urakassa %.', sop.id, sop.urakka;
    END LOOP;

END;
$$;

