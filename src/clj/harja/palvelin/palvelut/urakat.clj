(ns harja.palvelin.palvelut.urakat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.urakat :as q]
            [harja.geo :refer [muunna-pg-tulokset]]
            [clojure.tools.logging :as log]))

(declare hallintayksikon-urakat
         urakan-tiedot
         hae-urakoita)


(defrecord Urakat []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)]
      (julkaise-palvelu http :hallintayksikon-urakat
                        (fn [user hallintayksikko]
                          (hallintayksikon-urakat (:db this) user hallintayksikko)))
      (julkaise-palvelu http :hae-urakoita
                        (fn [user teksti]
                          (hae-urakoita (:db this) user teksti)))
      this))

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hallintayksikon-urakat)
    (poista-palvelu (:http-palvelin this) :hae-urakoita)
    
    this))

(def urakka-xf
  (comp (muunna-pg-tulokset :alue :alueurakan_alue)

        ;; Jos alueurakan alue on olemassa, käytetään sitä alueena
        (map #(if-let [alueurakka (:alueurakan_alue %)]
                (-> %
                    (dissoc :alueurakan_alue)
                    (assoc  :alue alueurakka))
                (dissoc % :alueurakan_alue)))
              
        (map #(assoc % :urakoitsija {:id (:urakoitsija_id %)
                                     :nimi (:urakoitsija_nimi %)
                                     :ytunnus (:urakoitsija_ytunnus %)}))
        (map #(assoc % :hallintayksikko {:id (:hallintayksikko_id %)
                                         :nimi (:hallintayksikko_nimi %)
                                         :lyhenne (:hallintayksikko_lyhenne %)}))
        (map #(assoc % :tyyppi (keyword (:tyyppi %))))
              
        (map #(dissoc % :urakoitsija_id :urakoitsija_nimi :urakoitsija_ytunnus
                      :hallintayksikko_id :hallintayksikko_nimi :hallintayksikko_lyhenne))))

(defn hallintayksikon-urakat [db user hallintayksikko-id]
  ;; PENDING: Mistä tiedetään kuka saa katso vai saako perustiedot nähdä kuka vaan (julkista tietoa)?
  (log/debug "Haetaan hallintayksikön urakat: " hallintayksikko-id)
  ;;(Thread/sleep 2000) ;;; FIXME: this is to try out "ajax loading" ui
  (into []
        urakka-xf
        (q/listaa-urakat-hallintayksikolle db hallintayksikko-id)))

(defn hae-urakoita [db user teksti]
  (log/debug "Haetaan urakoita tekstihaulla: " teksti)
  (into []
        urakka-xf
        (q/hae-urakoita db (str "%" teksti "%"))))
