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
            [harja.ui.komponentti :as komp]
            [harja.visualisointi :as vis])
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
  (let [kaikki-suoritukset-yhteensa (reduce + 0 (map :count rivit))
        rivit (if-not @tiedot/valittu-raportti
                (conj rivit
                      {:count kaikki-suoritukset-yhteensa
                       :raportti "Suorituksia yhteensä"
                       :lihavoi true})
                rivit)]
    [:div.raporttitietojen-grid
     [grid/grid
      {:otsikko (str "Raporttien suoritustiedot")
       :tyhja (if (nil? rivit) [yleiset/ajax-loader "Tietoja haetaan..."] "Ei tietoja")
       :tunniste :raportti
       :piilota-toiminnot? true}
      [{:otsikko "Raportti" :nimi :raportti :tyyppi :string :leveys 6
        :fmt (fn [arvo]
               (if (= arvo "Suorituksia yhteensä")
                 arvo
                 (tiedot/raportin-nimi-fmt tiedot/+mahdolliset-raportit+ arvo)))}
       {:otsikko "Suoritus\u00ADkertoja" :nimi :count :tyyppi :string :leveys 2
        :tasaa :oikea}]
      rivit]]))

(defn raporttitieto-top5-piirakka [avain-arvo-map]
  (when-not (empty? avain-arvo-map)
    [:div.piirakka-wrapper
     [:h5.piirakka-label "TOP 5 raportit"]
     [vis/pie
      {:width 460 :height 300 :radius 120 :show-text :percent :show-legend true}
      avain-arvo-map]]))

(defn raporttien-suoritustieto [_]
  (komp/luo
    (fn [_]
      (let [rivit @tiedot/raporttitiedot
            top-5 (take 5 rivit)
            piirakan-data (into {}
                                (map (fn [{:keys [count raportti]}]
                                       {(tiedot/raportin-nimi-fmt tiedot/+mahdolliset-raportit+
                                                                  raportti) count})
                                     top-5))]
        [:span.raporttitieto
         [valinnat/aikavali yhteiset/valittu-aikavali]
         [raporttivalinta tiedot/valittu-raportti tiedot/+mahdolliset-raportit+]
         [roolivalinta tiedot/valittu-rooli tiedot/+mahdolliset-roolit+]
         [formaattivalinta tiedot/valittu-formaatti tiedot/+mahdolliset-formaatit+]
         [:div.flex
          [raporttitieto-grid rivit]
          (when-not @tiedot/valittu-raportti
            [raporttitieto-top5-piirakka piirakan-data])]]))))