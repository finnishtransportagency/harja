(ns harja.palvelin.palvelut.urakat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.skeema :as skeema]
            [harja.kyselyt.urakat :as q]
            [harja.geo :refer [muunna-pg-tulokset]]
            [clojure.tools.logging :as log]))

(declare hallintayksikon-urakat
         urakan-tiedot)


(defrecord Urakat []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)]
      (julkaise-palvelu http :hallintayksikon-urakat
                        (fn [user hallintayksikko]
                          (hallintayksikon-urakat (:db this) user hallintayksikko)))
      this))

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hallintayksikon-urakat)
    this))

(defn hallintayksikon-urakat [db user hallintayksikko-id]
  ;; PENDING: Mistä tiedetään kuka saa katso vai saako perustiedot nähdä kuka vaan (julkista tietoa)?
  (log/debug "Haetaan hallintayksikön urakat: " hallintayksikko-id)
  ;;(Thread/sleep 2000) ;;; FIXME: this is to try out "ajax loading" ui
  (into []
        (comp (muunna-pg-tulokset :alue)
              (map #(assoc % :urakoitsija {:id (:urakoitsija_id %)
                                           :nimi (:urakoitsija_nimi %)
                                           :ytunnus (:urakoitsija_ytunnus %)}))
              (map #(dissoc % :urakoitsija_id :urakoitsija_nimi :urakoitsija_ytunnus)))
        (q/listaa-urakat-hallintayksikolle db hallintayksikko-id)))

