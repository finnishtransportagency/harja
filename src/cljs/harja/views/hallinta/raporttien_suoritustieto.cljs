(ns harja.views.hallinta.raporttien-suoritustieto

  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [<! >! chan close!]]
            [harja.tiedot.hallinta.raporttien-suoritustieto :as tiedot]
            [harja.tiedot.hallinta.yhteiset :as yhteiset]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as y]
            [harja.ui.kentat :as kentat]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.komponentti :as komp])
  (:require-macros [cljs.core.async.macros :refer [go]]))



(defn roolivalinta [valittu-atom valinnat]
  [kentat/tee-otsikollinen-kentta
   {:otsikko "Tekijän rooli"
    :kentta-params {:tyyppi :valinta :valinnat valinnat
                    :valinta-arvo first :valinta-nayta second}
    :arvo-atom valittu-atom}])

(defn formaattivalinta [valittu-atom valinnat]
  [kentat/tee-otsikollinen-kentta
   {:otsikko "Formaatti"
    :kentta-params {:tyyppi :valinta :valinnat valinnat
                    :valinta-arvo first :valinta-nayta second}
    :arvo-atom valittu-atom}])

(defn raporttivalinta [valittu-atom valinnat]
  [kentat/tee-otsikollinen-kentta
   {:otsikko "Raportti"
    :kentta-params {:tyyppi :valinta :valinnat valinnat
                    :valinta-arvo first :valinta-nayta second}
    :arvo-atom valittu-atom}])

(defn raporttitieto-grid [rivit]
  [:span.raporttitietojen-grid
   [grid/grid
    {:otsikko (str "Raporttien suoritustiedot")
     :tyhja (if (nil? rivit) [yleiset/ajax-loader "Toetpka haetaan..."] "Ei tietoja")
     :tunniste :raportti
     :piilota-toiminnot? true}
    [{:otsikko       "Raportti" :nimi :raportti :tyyppi :string :leveys 50}
     {:otsikko       "Lukumäärä" :nimi :count :tyyppi :string :leveys 50}]
    rivit]])

(defn raporttien-suoritustieto [_]

  (komp/luo
    (fn [_]
      [:span.indeksit
       [valinnat/aikavali yhteiset/valittu-aikavali]
       [raporttivalinta tiedot/valittu-raportti tiedot/+mahdolliset-raportit+]
       [roolivalinta tiedot/valittu-rooli tiedot/+mahdolliset-roolit+]
       [formaattivalinta tiedot/valittu-formaatti tiedot/+mahdolliset-formaatit+]

       [yleiset/vihje "Tässä on tilastotietoa, mitä Harjan raportteja käytetään eniten."]
       [raporttitieto-grid @tiedot/raporttitiedot]])))