-- Lisätään geometriapaivitys-tauluun sarake viimeisimmän lähteen tallentamiseksi
ALTER TABLE geometriapaivitys
  ADD COLUMN viimeisin_lahde TEXT;

COMMENT ON COLUMN geometriapaivitys.viimeisin_lahde
                IS 'Viimeisin lähde, josta geometriapäivitysajo on haettu. Voi olla esim. tiedostopolku tai URL.';
