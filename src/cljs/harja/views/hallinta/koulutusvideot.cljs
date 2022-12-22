(ns harja.views.hallinta.koulutusvideot
  "Työkalu koulutusvideoiden muokkaamiseen infosivulla"
  (:require [tuck.core :refer [tuck]]
            [harja.ui.komponentti :as komp]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as lomake]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.info :as tiedot]
            [harja.ui.grid :as grid]))

(defn videolistaus [e! videot]
  [grid/grid {:tyhja "Ei videoita."
              :tunniste :id
              :voi-kumota? false
              :piilota-toiminnot? false
              :jarjesta :id
              :tallenna-vain-muokatut true
              :tallenna (fn [sisalto]
                          (tuck-apurit/e-kanavalla! e! tiedot/->TallennaVideo sisalto))}

   [{:otsikko "Otsikko"
     :tyyppi
     :string
     :validoi [[:ei-tyhja "Anna otsikko"]]
     :nimi :otsikko :leveys 1}
    {:otsikko "Linkki"
     :tyyppi
     :string
     :validoi [[:ei-tyhja "Anna linkki"]]
     :nimi :linkki :leveys 1.5}

    {:otsikko "Pvm"
     ::lomake/col-luokka "col-xs-3"
     :pakollinen? true
     :aseta (fn [rivi arvo]
              (assoc-in rivi [:pvm] arvo))
     :fmt pvm/pvm-opt :tyyppi :pvm
     :validoi [[:ei-tyhja "Valitse päivämäärä"]]
     :nimi :pvm
     :leveys 1.5}]
   videot])

(defn videot* [e! _]
  (komp/luo
   (komp/sisaan
    #(do
       (e! (tiedot/->HaeKoulutusvideot))))
   
   (fn [e! {:keys [videot]}]
     [:div
      [:h3 {:class "header-yhteiset"} "Info-näkymän videomateriaalit"]
      [:p "Tästä voit muokata infosivun linkkejä."]
      [:p "Kun lisäät rivejä syötä linkki muodossa https://www.youtube.com/watch?v=cTTxPCdU9zs"]
      [videolistaus e! videot]])))

(defn nakyma []
  [tuck tiedot/tila videot*]) 