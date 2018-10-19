(ns harja.tiedot.hallinta.sonjajonot
  (:require [tuck.core :as t :refer [process-event]]
            [reagent.core :refer [atom]]
            [harja.tyokalut.tuck :as tuck-tyokalut]))

(defonce tila (atom {}))

(defrecord HaeSonjanTila [])
(defrecord SonjanTilaHaettu [vastaus])
(defrecord SonjanTilahakuEpaonnistui [vastaus])

(extend-protocol t/Event
  HaeSonjanTila
  (process-event [_ tila]
    (-> tila
        (tuck-tyokalut/get! :hae-sonjan-tila
                             {:onnistui ->SonjanTilaHaettu
                              :epaonnistui ->SonjanTilahakuEpaonnistui})))
  SonjanTilaHaettu
  (process-event [{vastaus :vastaus} tila]
    (assoc tila :sonjan-tila vastaus))
  SonjanTilahakuEpaonnistui
  (process-event [{vastaus :vastaus} tila]
    (assoc tila :virhe vastaus)))
