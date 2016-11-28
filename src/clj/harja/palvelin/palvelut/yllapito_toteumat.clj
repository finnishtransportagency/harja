(ns harja.palvelin.palvelut.yllapito_toteumat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.kyselyt.yllapito-toteumat :as q]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin
             :refer
             [julkaise-palvelu poista-palvelut]]
            [clojure.java.jdbc :as jdbc]))

(defn hae-yllapito-toteumat [db user {:keys [urakka] :as tiedot}]
  (log/debug "Hae ylläpidon toteumat parametreilla: " (pr-str tiedot))
  (jdbc/with-db-transaction [db db]
    (into [] (q/hae-muut-tyot db {:urakka urakka}))))

(defn hae-yllapito-toteuma [db user tiedot]
  ;; TODO
  )

(defn hae-laskentakohteet [db user tiedot]
  ;; TODO
  )

(defn tallenna-yllapito-toteuma [db user tiedot]
  ;; TODO
  )

(defrecord YllapitoToteumat []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :hae-yllapito-toteumat
                        (fn [user tiedot]
                          (hae-yllapito-toteumat db user tiedot)))
      (julkaise-palvelu http :hae-yllapito-toteuma
                        (fn [user tiedot]
                          (hae-yllapito-toteuma db user tiedot)))
      (julkaise-palvelu http :hae-laskentakohteet
                        (fn [user tiedot]
                          (hae-laskentakohteet db user tiedot)))
      (julkaise-palvelu http :tallenna-yllapito-toteuma
                        (fn [user tiedot]
                          (tallenna-yllapito-toteuma db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-yllapito-toteumat
      :hae-yllapito-toteuma
      :hae-laskentakohteet
      :tallenna-yllapito-toteuma)
    this))
