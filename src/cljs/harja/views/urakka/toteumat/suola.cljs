(ns harja.views.urakka.toteumat.suola
  "Suolankäytön toteumat hoidon alueurakoissa"
  (:require [reagent.core :refer [atom wrap]]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.suola :as suola]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.pvm :as pvm]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.loki :refer [log logt]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]))

(defonce suolatoteumissa? (atom false))

(defonce toteumat
  (reaction<! [hae? @suolatoteumissa?
               ur @nav/valittu-urakka
               hk @tiedot-urakka/valittu-hoitokausi
               kk @tiedot-urakka/valittu-hoitokauden-kuukausi]
              (when (and hae? ur)
                (suola/hae-toteumat (:id ur) (or kk hk)))))

(defn suolatoteumat []

  (komp/luo
   (komp/lippu suolatoteumissa?)
   (fn []
     (let [ur @nav/valittu-urakka]
       [:div.suolatoteumat

        [:span.valinnat
         [urakka-valinnat/urakan-sopimus ur]
         [urakka-valinnat/urakan-hoitokausi-ja-kuukausi ur]]
        
        [grid/grid {:tunniste #(or (:tmid %)
                                   (str (pvm/pvm (:alkanut %))))
                    :otsikko "Talvisuolan käyttö"
                    :tallenna #(log "SUOLA: " (pr-str %))
                    :tyhja (if (nil? @toteumat)
                             [yleiset/ajax-loader "Suolatoteumia haetaan..."]
                             "Ei suolatoteumia valitulle aikavälille")}
         [{:otsikko "Materiaali" :nimi :materiaali :fmt :nimi :leveys "15%"}
          {:otsikko "Pvm" :nimi :alkanut :fmt pvm/pvm :leveys "15%"}
          {:otsikko "Käytetty määrä" :nimi :maara :leveys "15%"}
          {:otsikko "Lisätieto" :nimi :lisatieto :leveys "50%"}]

         @toteumat]])
     )))



