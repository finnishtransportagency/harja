(ns harja.tiedot.urakka.toteumat.mhu-akilliset-hoitotyot
  (:require [tuck.core :as tuck]))

(defrecord PaivitaLomake [lomake])

(extend-protocol
  tuck/Event
  PaivitaLomake
  (process-event [{lomake :lomake} app]
    (assoc app :lomake lomake)))