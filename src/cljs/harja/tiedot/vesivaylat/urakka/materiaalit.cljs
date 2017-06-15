(ns harja.tiedot.vesivaylat.urakka.materiaalit
  (:require [reagent.core :as r]
            [tuck.core :as t]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]))
                                        ;
;; Määritellään viestityypit
(defrecord PaivitaUrakka [urakka])
(defrecord ListausHaettu [tulokset])


;; App atom
(defonce app (r/atom {:urakka-id nil
                      :materiaalilistaus nil}))


(defn- hae [app]
  (let [tulos! (t/send-async! ->ListausHaettu)]
    (log "HAETAAN ")
    app))

(extend-protocol t/Event
  PaivitaUrakka
  (process-event [{urakka :urakka} app]
    (hae (assoc app :urakka-id (:id urakka))))

  ListausHaettu
  (process-event [{tulokset :tulokset} app]
    (assoc app
           :materiaalilistaus tulokset)))
