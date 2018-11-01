(ns harja.tiedot.hallinta.sonjajonot
  (:require [tuck.core :as t :refer [process-event]]
            [reagent.core :refer [atom]]
            [cljs.core.async :refer [<! >! timeout chan alts!]]
            [harja.tyokalut.tuck :as tuck-tyokalut])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defonce tila (atom {}))

(defrecord HaeSonjanTila [])
(defrecord AloitaSonjaTilanHakeminen [])
(defrecord LopetaSonjaTilanHakeminen [])
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
    (let [lopetus-kanava (chan)]
      (t/action! (fn [e!]
                   (go-loop []
                            (e! (->HaeSonjanTila))
                            ;; Jos ei ole tullut haun lopetus käskyä, haetaan tulokset 5 sek välein
                            (when-not (first (alts! [lopetus-kanava (timeout 5000)]))
                              (recur)))))
      (assoc tila :lopetus-kanava lopetus-kanava)))
  LopetaSonjaTilanHakeminen
  (process-event [_ {lopetus-kanava :lopetus-kanava :as tila}]
    (go (>! lopetus-kanava true))
    tila)
  SonjanTilaHaettu
  (process-event [{vastaus :vastaus} tila]
    (assoc tila :sonjan-tila vastaus))
  SonjanTilahakuEpaonnistui
  (process-event [{vastaus :vastaus} tila]
    (assoc tila :virhe vastaus)))
