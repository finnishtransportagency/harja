(ns harja.views.hallinta.pohjavesialueidensiirto_nakyma
  "Työkalu pohjavesialueiden siirtämiseksi rajoitusalueiksi"
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [<! >! chan close! timeout]]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.tiedot.hallinta.yhteiset :as yhteiset]
            [harja.tiedot.hallinta.pohjavesialueidensiirto-tiedot :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.kentat :as kentat]
            [harja.ui.listings :refer [suodatettu-lista]]
            [harja.loki :refer [log]]
            [harja.ui.grid :as grid]
            [harja.fmt :as fmt]
            [cljs-time.core :as t]
            [harja.pvm :as pvm]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.hallintayksikot :as hal]
            [clojure.string :as str]
            [harja.ui.yleiset :as y])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn pohjavesialueet
  "Rajoitusalueen toteumien summatiedot / yhteenveto per päivämäärä ja käytetty materiaali."
  [e! app urakka]
  (komp/luo
    (komp/sisaan
      (fn []
        (e! (tiedot/->HaeUrakanPohjavesialueet (:id urakka)))))
    (fn [e! app urakka]
      (let [urakkaid (:id urakka)
            valittu-urakka (some #(when (= urakkaid (:id %)) %) (:urakat app))
            urakan-pohjavesialueet (:pohjavesialueet valittu-urakka)]
        [grid/grid {:tunniste (juxt :hoitokauden-alkuvuosi :talvisuolaraja :nimi :tie :tunnus)
                    :piilota-muokkaus? true
                    ;:esta-tiivis-grid? true
                    :reunaviiva? true
                    :tyhja (if (nil? urakan-pohjavesialueet)
                             [yleiset/ajax-loader "Urakan pohjavesialueita haetaan..."]
                             "Ei pohjavesialueita")}
         [{:otsikko "Pohjavesialue" :nimi :nimi :leveys 1}
          {:otsikko "Tunnus" :nimi :tunnus :leveys 1}
          {:otsikko "Tie" :nimi :tie :leveys 1}
          {:otsikko "Aosa" :nimi :aosa :leveys 1}
          {:otsikko "Aet" :nimi :aet :leveys 1}
          {:otsikko "Losa" :nimi :losa :leveys 1}
          {:otsikko "Let" :nimi :let :leveys 1}
          {:otsikko "Pituus" :nimi :pituus :leveys 1}
          {:otsikko "Ajoratojen pituus" :nimi :ajoratojen_pituus :leveys 1}
          {:otsikko "Urakkaid" :nimi :urakkaid :leveys 1}
          {:otsikko "Suolaraja" :nimi :talvisuolaraja :leveys 1}
          {:otsikko "Formiaatti" :nimi :formiaatti :leveys 1 :fmt #(if % "Formiaatti" "-")}
          {:otsikko "Hoitokauden alkuvuosi" :nimi :hoitokauden-alkuvuosi :leveys 1}
          ]
         urakan-pohjavesialueet]))))

(defn listaa-urakat [e! app]
  (let [urakat (:urakat app)
        urakan-pohjavesialueet (into {}
                                 (map (juxt :id (fn [urakka] [pohjavesialueet e! app urakka])))
                                 urakat)]
    [grid/grid {:tunniste :id
                :piilota-muokkaus? true
                ;; Estetään dynaamisesti muuttuva "tiivis gridin" tyyli, jotta siniset viivat eivät mene vääriin kohtiin,
                ;; taulukon sarakemääriä muutettaessa. Tyylejä säädetty toteumat.less tiedostossa.
                ;:esta-tiivis-grid? true
                :vetolaatikot urakan-pohjavesialueet
                :tyhja (if (or (nil? urakat))
                         [yleiset/ajax-loader "Rajoitusalueita haetaan..."]
                         "Ei Rajoitusalueita")}
     [{:tyyppi :vetolaatikon-tila :leveys 0.5}
      {:otsikko "ID" :nimi :id :leveys 1}
      {:otsikko "Urakka" :nimi :nimi :leveys 1.5}
      {:otsikko "Toimenpiteet"
       :tyyppi :komponentti
       :leveys 1
       :komponentti (fn [urakka]
                      [:div [napit/tallenna "Tee siirto"
                             #(e! (tiedot/->TeeSiirto urakka))
                             {:paksu? true}]])}]
     urakat]))

(defn urakat* [e! app]
  (komp/luo
    (komp/sisaan
      #(do
         (e! (tiedot/->HaePohjavesialueurakat))))
    (fn [e! app]
      (when (:urakat app)
        [:div
         [debug/debug app]
         (listaa-urakat e! app)]))))

;; TODO: Keksi parempi nimi
(defn view []
  [tuck tiedot/data urakat*])
