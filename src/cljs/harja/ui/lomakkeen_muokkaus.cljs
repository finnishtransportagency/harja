(ns harja.ui.lomakkeen-muokkaus
  "Käyttötarkoitus on sama kuin harja.ui.grid.gridin-muokkaus, mutta lomakkeelle")

(defn ilman-lomaketietoja
  "Palauttaa lomakkeen datan ilman lomakkeen ohjaustietoja"
  [data]
  (dissoc data
          :harja.ui.lomake/muokatut
          :harja.ui.lomake/virheet
          :harja.ui.lomake/varoitukset
          :harja.ui.lomake/huomautukset
          :harja.ui.lomake/puuttuvat-pakolliset-kentat
          :harja.ui.lomake/ensimmainen-muokkaus
          :harja.ui.lomake/viimeisin-muokkaus
          :harja.ui.lomake/skeema))
