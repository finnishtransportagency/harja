(ns harja.palvelin.palvelut.toteumat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.toteumat :as q]
            [harja.palvelin.oikeudet :as oik]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn urakan-toteumat [db user urakka-id]
  (log/debug "Haetaan urakan toteumat: " urakka-id)
  (into []
        (q/listaa-urakan-toteumat db urakka-id)))

(defrecord Toteumat []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)]
      (julkaise-palvelu http :urakan-toteumat
                        (fn [user urakka-id]
                          (urakan-toteumat (:db this) user urakka-id)))
      this))

  (stop [this]
    (poista-palvelu (:http-palvelin this) :urakan-toteumat)
    this))
