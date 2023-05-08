(ns harja.views.urakka.tyomaapaivakirja
  "Työmaapäiväkirja urakka välilehti"
  (:require [tuck.core :refer [tuck]]
            [harja.ui.bootstrap :as bs]

            [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.lomake :as lomake]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.istunto :as istunto]
            [harja.pvm :as pvm]))


(defn nakyma [_ tiedot]
  (println "\n Tiedot: " tiedot)

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
   tiedot])

(defn tyomaapiavakirja* [e! _]
  (komp/luo
   (komp/sisaan
    #(do
       (e! (tiedot/->HaeTiedot))))
   
   (fn [e! {:keys [tiedot]}]
     [:div
      [:h3 {:class "header-yhteiset"} "Test"]
      [nakyma e! tiedot]])))

(defn tyomaapiavakirja [ur]
  [tuck tiedot/tila tyomaapiavakirja*])
