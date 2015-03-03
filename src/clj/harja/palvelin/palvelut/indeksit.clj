(ns harja.palvelin.palvelut.indeksit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            
            [harja.kyselyt.indeksit :as q]))

(declare hae-indeksit tallenna-indeksi)
                        
(defrecord Indeksit []
  component/Lifecycle
  (start [this]
   (doto (:http-palvelin this)
     (julkaise-palvelu
       :indeksit (fn [user]
         (hae-indeksit (:db this) user)))
     (julkaise-palvelu :tallenna-indeksi
       (fn [user tiedot]
         (tallenna-indeksi (:db this) user tiedot))))
   this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :indeksit)
    (poista-palvelu (:http-palvelin this) :tallenna-indeksi)
    this))


(defn hae-indeksit
  "Palvelu, joka palauttaa indeksit."
  [db user]
        (let 
          [indeksit-vuosittain  (seq (group-by
                            (fn [rivi]
                              [(:nimi rivi) (:vuosi rivi)]
                              ) (q/listaa-indeksit db)))]
          
          (zipmap (map first indeksit-vuosittain)
                  (map (fn [[_ kuukaudet]]
                         (assoc (zipmap (map :kuukausi kuukaudet) (map #(float (:arvo %)) kuukaudet))
                                :vuosi (:vuosi (first kuukaudet))))
                       indeksit-vuosittain))))

(defn tallenna-indeksi [db user {:keys [nimi indeksit poistettu]}]
    (log/info "tallenna-indeksit " nimi + " indeksit " + indeksit " poistettu " poistettu)
      ((jdbc/with-db-transaction [c db]
                                 (hae-indeksit c user))))
