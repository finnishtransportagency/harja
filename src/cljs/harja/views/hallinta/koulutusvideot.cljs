(ns harja.views.hallinta.koulutusvideot
  "Työkalu koulutusvideoiden muokkaamiseen infosivulla"
  (:require [tuck.core :refer [tuck]]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as lomake]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.info :as data]
            [harja.ui.grid :as grid]))

(defn listaa-videot [e! app]
  (let [videot (:videot app)]

    [grid/grid {:tyhja (if (nil? videot)
                         [yleiset/ajax-loader "Videoita haetaan..."]
                         "Ei videoita")
                :tunniste :id
                :voi-lisata? (constantly true)
                :voi-muokata? (constantly true)
                :voi-poistaa? (constantly true)
                :uusi-rivi (fn [rivi] (assoc rivi :otsikko "< uusi otsikko >" :linkki "< uusi linkki >")) 
                :voi-kumota? false
                :piilota-toiminnot? false
                :jarjesta :id
                :tallenna-vain-muokatut true
                :tallenna (fn [sisalto]
                            (tuck-apurit/e-kanavalla! e! data/->TallennaVideo sisalto))}

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
     videot]))

(defn videot*
  "Rajapintakutsun callback"
  [e! _]
  (komp/luo
   (komp/sisaan
    #(do
       (e! (data/->HaeKoulutusvideot)))) 
   (fn [e! app]
     [:div "test"]
     (when (:videot app)
       [:div
        [:p {:class "info-info"} "Info-näkymän videomateriaalit"]
        [:p "Tästä voit muokata infosivun linkkejä."]
        [:p "Kun lisäät rivejä syötä linkki muodossa https://www.youtube.com/watch?v=cTTxPCdU9zs"]
        [listaa-videot e! app]]))))

(defn nakyma []
  [tuck data/data videot*]) 
