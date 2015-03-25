(ns harja.palvelin.palvelut.yksikkohintaiset-tyot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [clojure.tools.logging :as log]
            [clojure.set :refer [intersection difference]]
            [clojure.string :as str]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            
            [harja.palvelin.oikeudet :as oikeudet]
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
                     :maara (if (:maara %) (double (:maara %))) 
                     :yksikkohinta (if (:yksikkohinta %) (double (:yksikkohinta %)))))
        (q/listaa-urakan-yksikkohintaiset-tyot db urakka-id)))

(defn tallenna-urakan-yksikkohintaiset-tyot 
  "Palvelu joka tallentaa urakan yksikkohintaiset tyot"
  [db user {:keys [urakka-id sopimusnumero hoitokausi-alkupvm hoitokausi-loppupvm tyot] :as tiedot}]
  (oikeudet/vaadi-rooli-urakassa user oikeudet/rooli-urakanvalvoja urakka-id)
  (assert (vector? tyot) "tyot tulee olla vektori")
  (jdbc/with-db-transaction [c db]
        (let [nykyiset-arvot (hae-urakan-yksikkohintaiset-tyot c user urakka-id)
              valitun-hoitokauden-ja-sopimusnumeron-tyot (filter #(and
                                                                    (= (:sopimus %) sopimusnumero) 
                                                                    ;; olettaa hoidon au:n hoitokausirakenteen
                                                                    ;; --> ei välttämättä toimi ylläpidon urakoissa
                                                                    (or (= (:alkupvm %) hoitokausi-alkupvm)
                                                                        (= (:loppupvm %) hoitokausi-loppupvm))) 
                                                                 nykyiset-arvot)
              tyot-kannassa (into #{}  (map :tehtava valitun-hoitokauden-ja-sopimusnumeron-tyot))]
          (doseq [tyo tyot]
            (if (not (tyot-kannassa (:tehtava tyo)))
              ;; insert
              (q/lisaa-urakan-yksikkohintainen-tyo<! c (:maara tyo) (:yksikko tyo) (:yksikkohinta tyo)
                                                     urakka-id sopimusnumero (:tehtava tyo)
                                                     (java.sql.Date. (.getTime (:alkupvm tyo)))
                                                     (java.sql.Date. (.getTime (:loppupvm tyo))))
              ;;update
              (q/paivita-urakan-yksikkohintainen-tyo! c (:maara tyo) (:yksikko tyo) (:yksikkohinta tyo)
                                                      urakka-id sopimusnumero (:tehtava tyo)
                                                      (java.sql.Date. (.getTime (:alkupvm tyo)))
                                                      (java.sql.Date. (.getTime (:loppupvm tyo)))))))
      (hae-urakan-yksikkohintaiset-tyot c user urakka-id)))
