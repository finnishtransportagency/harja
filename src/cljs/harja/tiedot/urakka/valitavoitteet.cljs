(ns harja.tiedot.urakka.valitavoitteet
  "Ylläpidon urakoiden välitavoitteiden tiedot."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn hae-urakan-valitavoitteet [urakka-id]
  (let [ch (chan)]
    (go
      (>! ch (<! (k/post! :hae-urakan-valitavoitteet urakka-id))))
    ch))

(defn merkitse-valmiiksi! [urakka-id valitavoite-id valmis-pvm kommentti]
  (let [ch (chan)]
    (go
      (let [res (<! (k/post! :merkitse-valitavoite-valmiiksi
                             {:urakka-id urakka-id
                              :valitavoite-id valitavoite-id
                              :valmis-pvm valmis-pvm
                              :kommentti kommentti}))]
        (>! ch res)))
    ch))

(defn tallenna! [urakka-id valitavoitteet]
  (let [ch (chan)]
    (go (let [res (<! (k/post! :tallenna-urakan-valitavoitteet
                               {:urakka-id urakka-id
                                :valitavoitteet valitavoitteet}))]
          (>! ch res)))
    ch))
