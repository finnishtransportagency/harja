(ns harja.tiedot.hallinta.jms-jonot
  (:require [tuck.core :as t :refer [process-event]]
            [reagent.core :refer [atom]]
            [cljs.core.async :refer [<! >! timeout chan alts!]]
            [harja.tyokalut.tuck :as tuck-tyokalut])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defonce tila (atom {}))

(defrecord HaeJMSnTila [jarjestelma])
(defrecord AloitaJMSTilanHakeminen [jarjestelma])
(defrecord LopetaJMSTilanHakeminen [jarjestelma])
(defrecord JMSnTilaHaettu [vastaus])
(defrecord JMSnTilahakuEpaonnistui [vastaus])

(extend-protocol t/Event
  HaeJMSnTila
  (process-event [{:keys [jarjestelma]} tila]
    (tuck-tyokalut/get! tila
                        (case jarjestelma
                          :sonja :hae-sonjan-tila
                          :itmf :hae-itmfn-tila)
                        {:onnistui ->JMSnTilaHaettu
                         :epaonnistui ->JMSnTilahakuEpaonnistui
                         :onnistui-parametrit [jarjestelma]
                         :epaonnistui-parametrit [jarjestelma]}))
  AloitaJMSTilanHakeminen
  (process-event [{:keys [jarjestelma]} tila]
    (let [lopetus-kanava (chan)]
      (t/action! (fn [e!]
                   (go-loop []
                            (e! (->HaeJMSnTila jarjestelma))
                            ;; Jos ei ole tullut haun lopetus käskyä, haetaan tulokset 5 sek välein
                            (when-not (first (alts! [lopetus-kanava (timeout 5000)]))
                              (recur)))))
      (assoc-in tila [:lopetus-kanava jarjestelma] lopetus-kanava)))
  LopetaJMSTilanHakeminen
  (process-event [{:keys [jarjestelma]} {lopetus-kanava :lopetus-kanava :as tila}]
    (go (>! (get lopetus-kanava jarjestelma) true))
    tila)
  JMSnTilaHaettu
  (process-event [{:keys [vastaus jarjestelma]} tila]
    (assoc-in tila [:jarjestelmien-tilat jarjestelma] vastaus))
  JMSnTilahakuEpaonnistui
  (process-event [{:keys [vastaus jarjestelma]} tila]
    (assoc-in tila [:jarjestelmien-virheet jarjestelma] vastaus)))
