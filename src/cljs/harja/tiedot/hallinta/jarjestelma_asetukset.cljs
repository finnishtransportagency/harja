(ns harja.tiedot.hallinta.jarjestelma-asetukset
  (:require [tuck.core :as tuck]
            [reagent.core :refer [atom] :as r]
            [harja.domain.geometriaaineistot :as geometria-ainestot]
            [harja.pvm :as pvm]
            [reagent.core :refer [atom] :as r])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tila
         (atom {:nakymassa? false
                :geometria-aineistot nil
                :haku-kaynnissa? false}))

(defrecord Nakymassa? [nakymassa?])
(defrecord HaeGeometria-aineistot [])

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  HaeGeometria-aineistot
  (process-event [_ app]
    ;; todo: tee palvelinkutsu ja hae geometria-aineistot
    (assoc app
      :haku-kaynnissa? false
      :geometria-aineistot
      (atom {1 {::geometria-ainestot/id 666
                ::geometria-ainestot/nimi "hitutinteri"
                ::geometria-ainestot/tiedostonimi "hitutinteri.shp"
                ::geometria-ainestot/voimassaolo-alkaa (pvm/nyt)
                ::geometria-ainestot/voimassaolo-paattyy (pvm/nyt)}}))))