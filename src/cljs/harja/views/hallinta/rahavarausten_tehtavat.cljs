(ns harja.views.hallinta.rahavarausten-tehtavat
  (:require [clojure.string :as str]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.yleiset :as yleiset]
            [reagent.core :as r]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.grid :as grid]
            [harja.tiedot.hallinta.rahavaraukset :as tiedot])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))


(defn tehtavat-vetolaatikko
  "Anna parametrina valittu tehtävä sekä kaikki mahdolliset tehtävät, joista voidaan valita."
  [tehtava tehtavat]
  (do
    (js/console.log "tehtavat-vetolaatikko :: tehtava: " (pr-str tehtava) " tehtavat: " (pr-str tehtavat))
    [:div.rahavaraus-tehtava
     [grid/grid
      {:otsikko "Tehtävät"
       ; :tunniste :id
       :piilota-toiminnot? true
       }
      [{:otsikko "Tehtävä" :nimi :nimi :tyyppi :string :leveys 12}]
      tehtavat]
     #_ [:div.rahavaraus-tehtava-nimi (:nimi tehtava)]]))

    #_ [yleiset/livi-pudotusvalikko {:class "alasveto-tehtava"
                                     :valinta (:id tehtava)
                                     ;:format-fn :nimi
                                     :valitse-fn #(js/console.log "Valittiin urakka" (pr-str %))}
        tehtavat]


(defn rahavarausten-tehtavat* [e! _app]
  (komp/luo
    (komp/sisaan #(do
                    ;; Haetaan ennen sivun renderöintiä kaikki rahavaraukset ja niihin liitetyt tehtävät
                    (e! (tiedot/->HaeRahavarauksetTehtavineen))
                    ;; Haetaan kaikki tehtävät, joita voidaan liittää rahavarauksiin alasvetovalikon avulla
                    (e! (tiedot/->HaeTehtavat))))
    (fn [e! {:keys [rahavaraukset-tehtavineen] :as app}]
      (let [_ (js/console.log "rahavaraukset-tehtavineen" (pr-str rahavaraukset-tehtavineen))]
        [:div.rahavaraukset-hallinta
         [harja.ui.debug/debug app]
         [:h1 "Rahavarauksen tehtävät"]


         [:div.urakan-rahavaraukset
          [grid/grid
           {:otsikko "Rahavaraukset"
            :tunniste :id
            :piilota-toiminnot? true
            :vetolaatikot (into {}
                            (map (juxt :id (fn [rivi] [tehtavat-vetolaatikko (:nimi rivi) (:tehtavat rivi)]))
                              rahavaraukset-tehtavineen))}
           [{:tyyppi :vetolaatikon-tila :leveys 1}
            {:otsikko "Rahavaraus" :nimi :nimi :tyyppi :string :leveys 12}]
           rahavaraukset-tehtavineen]
          ]]))))


(defn rahavarausten-tehtavat []
  [tuck tiedot/tila rahavarausten-tehtavat*])
