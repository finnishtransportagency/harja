(ns harja.views.urakka.tyomaapaivakirja
  "Työmaapäiväkirja urakka välilehti"
  (:require [tuck.core :refer [tuck]]
            [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]))


(defn nakyma [_ tiedot]
  (let [aikavali-atom (atom ())]
    [:div
     [:div.row.filtterit {:style {:padding "16px"}}
      [valinnat/aikavali aikavali-atom {:otsikko "Aikaväli"
                                        :for-teksti "filtteri-aikavali"
                                        :luokka #{"label-ja-aikavali " "ei-tiukkaa-leveytta "}
                                        :ikoni-sisaan? true
                                        :vayla-tyyli? true}]]

     [grid/grid {:tyhja "Ei Tietoja."
                 :tunniste :id
                 :voi-kumota? false
                 :piilota-toiminnot? true
                 :jarjesta :id}

      [{:otsikko "Työpäivä"
        :tyyppi
        :string
        :nimi :alkupvm
        :leveys 1}
       {:otsikko "Saapunut"
        :tyyppi :string
        :nimi :loppupvm
        :leveys 1.5}
       {:otsikko "Viim. muutos"
        :tyyppi :string
        :nimi "test"
        :leveys 1}
       {:otsikko "Urakka"
        :tyyppi :string
        :nimi :nimi
        :leveys 1}]
      tiedot]]))

(defn tyomaapiavakirja* [e! _]
  (komp/luo
   (komp/sisaan
    #(do
       (e! (tiedot/->HaeTiedot))))
   
   (fn [e! {:keys [tiedot]}]
     [:div
      [:h3 {:class "header-yhteiset"} "Työmaapäiväkirja"]
      [nakyma e! tiedot]])))

(defn tyomaapiavakirja [ur]
  [tuck tiedot/tila tyomaapiavakirja*])
