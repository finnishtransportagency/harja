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
            [harja.loki :refer [log logt]]
            [harja.atom :refer [paivita!]]
            [cljs.core.async :refer [<! >!]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce suolatoteumissa? (atom false))

(defonce toteumat
  (reaction<! [hae? @suolatoteumissa?
               ur @nav/valittu-urakka
               sopimus @tiedot-urakka/valittu-sopimusnumero
               hk @tiedot-urakka/valittu-hoitokausi
               kk @tiedot-urakka/valittu-hoitokauden-kuukausi]
              (when (and hae? ur)
                (go
                  (into []
                        ;; luodaan kaikille id
                        (map-indexed (fn [i rivi]
                                       (assoc rivi :id i)))
                        (<! (suola/hae-toteumat (:id ur) (first sopimus)
                                                (or kk hk))))))))

(defonce materiaalit
  (reaction<! [hae? @suolatoteumissa?]
              (when hae?
                (suola/hae-materiaalit))))

(defn suolatoteumat []

  (komp/luo
   (komp/lippu suolatoteumissa?)
   (fn []
     (let [ur @nav/valittu-urakka
           [sopimus-id _] @tiedot-urakka/valittu-sopimusnumero
           muokattava? (comp not :koneellinen)]
       [:div.suolatoteumat

        [:span.valinnat
         [urakka-valinnat/urakan-sopimus ur]
         [urakka-valinnat/urakan-hoitokausi-ja-kuukausi ur]]
        
        [grid/grid {:otsikko "Talvisuolan käyttö"
                    :tallenna #(go (if-let [tulos (<! (suola/tallenna-toteumat (:id ur) sopimus-id %))]
                                       (paivita! toteumat)))
                    :tyhja (if (nil? @toteumat)
                             [yleiset/ajax-loader "Suolatoteumia haetaan..."]
                             "Ei suolatoteumia valitulle aikavälille")
                    :voi-poistaa? muokattava?}
         [{:otsikko "Materiaali" :nimi :materiaali :fmt :nimi :leveys "15%" :muokattava? muokattava?
           :tyyppi :valinta
           :valinta-nayta #(or (:nimi %) "- valitse -")
           :valinnat @materiaalit}
          {:otsikko "Pvm" :nimi :alkanut :fmt pvm/pvm-opt :tyyppi :pvm :leveys "15%" :muokattava? muokattava?}
          {:otsikko "Käytetty määrä" :nimi :maara :tyyppi :positiivinen-numero :leveys "15%" :muokattava? muokattava?}
          {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :string :leveys "50%" :muokattava? muokattava?
           :hae #(if (muokattava? %) (:lisatieto %) "Koneellisesti raportoitu")}]

         @toteumat]]))))