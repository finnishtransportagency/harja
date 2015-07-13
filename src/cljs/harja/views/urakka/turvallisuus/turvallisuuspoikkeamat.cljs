(ns harja.views.urakka.turvallisuus.turvallisuuspoikkeamat
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.turvallisuus.turvallisuuspoikkeamat :as tiedot]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.pvm :as pvm]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defn turvallisuuspoikkeaman-tiedot
  []
  [:div
   [:button.nappi-ensisijainen
    {:on-click #(reset! tiedot/valittu-turvallisuuspoikkeama nil)}
    "Palaa"]

   [:div "Tänne yhden turvallisuuspoikkeaman tiedot"]])

(defn turvallisuuspoikkeamalistaus
  []
  [:div.sanktiot
   [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]
   [:button.nappi-ensisijainen
    {:on-click #(reset! tiedot/valittu-turvallisuuspoikkeama tiedot/+uusi-turvallisuuspoikkeama+)}
    (ikonit/plus-sign) " Lisää turvallisuuspoikkeama"]
   [grid/grid
    {:otsikko       "Turvallisuuspoikkeamat"
     :tyhja         (if @tiedot/haetut-turvallisuuspoikkeamat "Ei löytyneitä tietoja" [ajax-loader "Haetaan sanktioita."])
     :rivi-klikattu #(reset! tiedot/valittu-turvallisuuspoikkeama %)}
    [{:otsikko "Päivämäärä" :nimi :aika :fmt pvm/pvm-aika :leveys 1}]
    @tiedot/haetut-turvallisuuspoikkeamat
    ]])

(defn turvallisuuspoikkeamat []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)

    (fn []
      (if @tiedot/valittu-turvallisuuspoikkeama
        [turvallisuuspoikkeaman-tiedot]
        [turvallisuuspoikkeamalistaus]))))