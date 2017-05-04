(ns harja.views.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.ui.otsikkokomponentti :refer [otsikot]]
            [harja.domain.vesivaylat.toimenpide :as t]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def testidata [{::t/id 0
                 ::t/alue "Kopio, Iisalmen väylä"
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/turvalaitetyyppi "Viitat"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}
                {::t/id 1
                 ::t/alue "Kopio, Iisalmen väylä"
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/turvalaitetyyppi "Viitat"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}
                {::t/id 2
                 ::t/alue "Kopio, Iisalmen väylä"
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/turvalaitetyyppi "Viitat"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}
                {::t/id 45
                 ::t/alue "Varkaus, Kuopion väylä"
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/turvalaitetyyppi "Viitat"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}
                {::t/id 3
                 ::t/alue "Varkaus, Kuopion väylä"
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/turvalaitetyyppi "Tykityöt"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}
                {::t/id 4
                 ::t/alue "Varkaus, Kuopion väylä"
                 ::t/tyoluokka "Asennus ja huolto"
                 ::t/toimenpide "Huoltotyö"
                 ::t/turvalaitetyyppi "Poljut"
                 ::t/pvm (pvm/nyt)
                 ::t/turvalaite "Siitenluoto (16469)"}])

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

(defn- otsikon-sisalto [sijainnit]
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

(defn- toimenpidepaneelin-otsikko [otsikko maara]
  (str otsikko
       " ("
       maara
       (when (not= maara 0)
         "kpl")
       ")"))

(defn kokonaishintaiset-toimenpiteet* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      [:div
       [:div {:style {:padding "10px"}}
        [:img {:src "images/harja_favicon.png"}]
        [:div {:style {:color "orange"}} "Työmaa"]]

       [otsikot [(toimenpidepaneelin-otsikko "Viitat" (count testidata))
                 (fn [] [otsikon-sisalto (suodata-ja-ryhmittele-toimenpiteet-gridiin testidata "Viitat")])
                 (toimenpidepaneelin-otsikko "Poljut" 0)
                 (fn [] [otsikon-sisalto (suodata-ja-ryhmittele-toimenpiteet-gridiin testidata "Poljut")])
                 (toimenpidepaneelin-otsikko "Tykityöt" 0)
                 (fn [] [otsikon-sisalto (suodata-ja-ryhmittele-toimenpiteet-gridiin testidata "Tykityöt")])]]])))

(defn kokonaishintaiset-toimenpiteet []
  [tuck tiedot/tila kokonaishintaiset-toimenpiteet*])