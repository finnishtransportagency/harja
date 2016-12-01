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
  ;; TODO OIKEUSTARKISTUS, hoidon mallilla vai oma rivi?
  (log/debug "Hae ylläpidon toteumat parametreilla: " (pr-str tiedot))
  (jdbc/with-db-transaction [db db]
    (into [] (q/hae-muut-tyot db {:urakka urakka}))))

(defn hae-yllapito-toteuma [db user {:keys [urakka id] :as tiedot}]
  ;; TODO OIKEUSTARKISTUS, hoidon mallilla vai oma rivi?
  (log/debug "Hae ylläpidon toteuma parametreilla: " (pr-str tiedot))
  (jdbc/with-db-transaction [db db]
    (first (q/hae-muu-tyo db {:urakka urakka
                              :id id}))))

(defn hae-laskentakohteet [db user tiedot]
  ;; TODO OIKEUSTARKISTUS
  ;; TODO
  )

(defn tallenna-yllapito-toteuma [db user {:keys [id urakka selite
                                                 pvm hinta yllapitoluokka] :as toteuma}]
  ;; TODO OIKEUSTARKISTUS, hoidon mallilla vai oma rivi?

  ;; TODO VAADI TOTEUMA KUULUU URAKKAAN!

  (jdbc/with-db-transaction [db db]
    (if (:id toteuma)
      (q/paivita-muu-tyo<! db {:id id
                               :urakka urakka
                               :selite selite
                               :pvm pvm
                               :hinta hinta
                               :yllapitoluokka yllapitoluokka})
      (q/luo-uusi-muu-tyo<! db {:urakka urakka
                                :selite selite
                                :pvm pvm
                                :hinta hinta
                                :yllapitoluokka yllapitoluokka})))

  (hae-yllapito-toteumat db user {:urakka urakka}))

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
                          (tallenna-yllapito-toteuma db user toteuma)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-yllapito-toteumat
      :hae-yllapito-toteuma
      :hae-laskentakohteet
      :tallenna-yllapito-toteuma)
    this))
