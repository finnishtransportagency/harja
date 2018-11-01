(ns harja.tiedot.hallinta.sonjajonot
  (:require [tuck.core :as t :refer [process-event]]
            [reagent.core :refer [atom]]
            [cljs.core.async :refer [<! timeout]]
            [harja.tyokalut.tuck :as tuck-tyokalut])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(defonce tila (atom {}))

(defrecord HaeSonjanTila [])
(defrecord AloitaSonjaTilanHakeminen [])
(defrecord SonjanTilaHaettu [vastaus])
(defrecord SonjanTilahakuEpaonnistui [vastaus])

(extend-protocol t/Event
  HaeSonjanTila
  (process-event [_ tila]
    (tuck-tyokalut/get! tila :hae-sonjan-tila
                        {:onnistui ->SonjanTilaHaettu
                         :epaonnistui ->SonjanTilahakuEpaonnistui}))
  AloitaSonjaTilanHakeminen
  (process-event [_ tila]
    (t/action! (fn [e!]
                 (go-loop []
                          (e! (->HaeSonjanTila))
                          (<! (timeout 5000))
                          (recur))))
    tila)
  SonjanTilaHaettu
  (process-event [{vastaus :vastaus} tila]
    (assoc tila :sonjan-tila vastaus))
  SonjanTilahakuEpaonnistui
  (process-event [{vastaus :vastaus} tila]
    (assoc tila :virhe vastaus)))
