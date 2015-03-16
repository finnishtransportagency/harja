(ns harja.palvelin.palvelut.yksikkohintaiset-tyot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [clojure.tools.logging :as log]
            [clojure.set :refer [intersection difference]]
            [clojure.string :as str]
            [clojure.java.jdbc :as jdbc]
            
            [harja.kyselyt.yksikkohintaiset-tyot :as q]))

(declare hae-urakan-yksikkohintaiset-tyot tallenna-urakan-yksikkohintaiset-tyot)
                        
(defrecord Yksikkohintaiset-tyot []
  component/Lifecycle
  (start [this]
   (doto (:http-palvelin this)
     (julkaise-palvelu
       :yksikkohintaiset-tyot (fn [user urakka-id]
         (hae-urakan-yksikkohintaiset-tyot (:db this) user urakka-id)))
     (julkaise-palvelu
       :tallenna-urakan-yksikkohintaiset-tyot (fn [user tiedot]
         (tallenna-urakan-yksikkohintaiset-tyot (:db this) user tiedot))))
   this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :yksikkohintaiset-tyot)
    (poista-palvelu (:http-palvelin this) :tallenna-urakan-yksikkohintaiset-tyot)
    this))


(defn hae-urakan-yksikkohintaiset-tyot 
  "Palvelu, joka palauttaa urakan yksikkohintaiset työt."
  [db user urakka-id]
  (log/info "hae-urakan-yksikkohintaiset-tyot" urakka-id)
  ;; FIXME: oikeus checki tähän tai queryyn urakkaroolin kautta
  (into []
        (map #(assoc % 
                     :maara (float (:maara %)) 
                     :yksikkohinta (float (:yksikkohinta %))))
        (q/listaa-urakan-yksikkohintaiset-tyot db urakka-id)))

(defn tallenna-urakan-yksikkohintaiset-tyot 
  "Palvelu joka tallentaa urakan yksikkohintaiset tyot"
  [db user {:keys [urakka-id sopimusnumero hoitokausi-alkupvm hoitokausi-loppupvm tyot] :as tiedot}] ;; tyot
  (assert (vector? tyot) "tyot tulee olla vektori")
  (log/info "tallenna-urakan-yksikkohintaiset-tyot palvelu" urakka-id sopimusnumero hoitokausi-alkupvm hoitokausi-loppupvm)
  (log/info "palvelu: työt" tyot)
  (let [nykyiset-arvot []]
    (jdbc/with-db-transaction [c db]
                              (doseq [tyo tyot]
                                (let [maara-kkt-10-12 (:maara-kkt-10-12 tyo)
                                      alkupvm-kkt-10-12 (:alkupvm tyo)
                                      loppupvm-kkt-10-12 (:alkupvm tyo) ;;TODO: tähän alkupvm:n vuosi ja 31.12.yyyy
                                      
                                      maara-kkt-1-9 (:maara-kkt-10-12 tyo)
                                      alkupvm-kkt-1-9 (:alkupvm tyo) ;;TODO: tähän loppuupvm:n vuosi ja 1.1.yyyy
                                      loppupvm-kkt-1-9 (:loppupvm tyo)]
                                  (log/info "palvelu: tyo" tyo)
                                                                ;; hanskaa nämä kahteen eri riviin 
                                                                ;: :maara-kkt-10-12  :maara-kkt-10-12
                                                                ;; :maara-kkt-1-19 :maara-kkt-1-19
                                                              ;;(q/paivita-urakan-yksikkohintainen-tyo! maara-kkt-10-12, yksikko, yksikkohinta,
                                                                ;;                                                   urakka-id, sopimus, tehtava, hoitokausi-alkupvm, hoitokausi-loppupvm)
                                                                  (hae-urakan-yksikkohintaiset-tyot c user urakka-id)))
                                  )))
