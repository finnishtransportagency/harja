(ns harja.palvelin.palvelut.materiaalit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.materiaalit :as q]
            [harja.palvelin.oikeudet :as oik]
            [harja.kyselyt.konversio :as konv]))

(defn hae-materiaalikoodit [db]
  (into [] (q/hae-materiaalikoodit db)))

(defn hae-urakan-materiaalit [db user urakka-id]
  (oik/vaadi-rooli-urakassa user oik/rooli-urakanvalvoja urakka-id)
  (into []
        (comp (map konv/alaviiva->rakenne)
              (map #(if (:id (:pohjavesialue %))
                      %
                      (dissoc % :pohjavesialue)))
              (map #(assoc % :maara (double (:maara %)))))
        (q/hae-urakan-materiaalit db urakka-id)))

(defrecord Materiaalit []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-materiaalikoodit
                      (fn [user]
                        (hae-materiaalikoodit (:db this))))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakan-materiaalit
                      (fn [user urakka-id]
                        (hae-urakan-materiaalit (:db this) user urakka-id)))
                           
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-materiaalikoodit
                     :hae-urakan-materiaalit)
                    
    this))
