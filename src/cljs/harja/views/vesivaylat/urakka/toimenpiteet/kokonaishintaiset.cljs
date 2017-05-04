(ns harja.views.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.ui.otsikkopaneeli :refer [otsikot]]
            [harja.domain.vesivaylat.toimenpide :as t]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log]]
            [harja.ui.grid :as grid]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- ryhmittele-toimenpiteet-alueella [toimenpiteet]
  (let [toimenpiteet-ryhmiteltyna (group-by ::t/alue toimenpiteet)
        turvalaitetyypit (keys toimenpiteet-ryhmiteltyna)]
    (vec (mapcat #(-> (cons (grid/otsikko %)
                            (get toimenpiteet-ryhmiteltyna %))) turvalaitetyypit))))

(defn- suodata-toimenpiteet-turvalaitetyypilla [toimenpiteet turvalaitetyyppi]
  (filterv #(= (::t/turvalaitetyyppi %) turvalaitetyyppi) toimenpiteet))

(defn- suodata-ja-ryhmittele-toimenpiteet-gridiin [toimenpiteet turvalaitetyyppi]
  (-> toimenpiteet
      (suodata-toimenpiteet-turvalaitetyypilla turvalaitetyyppi)
      (ryhmittele-toimenpiteet-alueella)))

(defn- paneelin-otsikon-sisalto [sijainnit]
  [grid/grid
   {:tunniste ::t/id
    :tyhja (if (nil? sijainnit)
             [ajax-loader "Haetaan toimenpiteitä"]
             "Ei toimenpiteitä")}
   [{:otsikko "Työluokka" :nimi ::t/tyoluokka}
    {:otsikko "Toimenpide" :nimi ::t/toimenpide}
    {:otsikko "Päivämäärä" :nimi ::t/pvm :fmt pvm/pvm-opt}
    {:otsikko "Turvalaite" :nimi ::t/turvalaite}]
   sijainnit])

(defn- paneelin-otsikko [otsikko maara]
  (str otsikko
       " ("
       maara
       (when (not= maara 0)
         "kpl")
       ")"))

(defn- luo-otsikkorivit [toimenpiteet]
  (let [turvalaitetyypit (keys (group-by ::t/turvalaitetyyppi toimenpiteet))]
    (vec (mapcat
           (fn [turvalaitetyyppi]
             [(paneelin-otsikko turvalaitetyyppi
                                (count (suodata-toimenpiteet-turvalaitetyypilla
                                                   toimenpiteet
                                                   turvalaitetyyppi)))
              (fn [] [paneelin-otsikon-sisalto
                      (suodata-ja-ryhmittele-toimenpiteet-gridiin
                        toimenpiteet
                        turvalaitetyyppi)])])
           turvalaitetyypit))))

(defn kokonaishintaiset-toimenpiteet* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! {:keys [toimenpiteet] :as app}]
      [:div
       [:div {:style {:padding "10px"}}
        [:img {:src "images/harja_favicon.png"}]
        [:div {:style {:color "orange"}} "Työmaa"]]

       [otsikot (luo-otsikkorivit toimenpiteet)]])))

(defn kokonaishintaiset-toimenpiteet []
  [tuck tiedot/tila kokonaishintaiset-toimenpiteet*])