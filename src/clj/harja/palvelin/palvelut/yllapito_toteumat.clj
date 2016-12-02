(ns harja.palvelin.palvelut.yllapito_toteumat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.kyselyt.yllapito-toteumat :as q]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin
             :refer
             [julkaise-palvelu poista-palvelut]]
            [clojure.java.jdbc :as jdbc]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-yllapito-toteumat [db user {:keys [urakka alkupvm loppupvm] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-muutos-ja-lisatyot user urakka)
  ;; TODO HUOMIOI AIKA JA SOPPARI!
  (log/debug "Hae ylläpidon toteumat parametreilla: " (pr-str tiedot))
  (jdbc/with-db-transaction [db db]
    (into [] (q/hae-muut-tyot db {:urakka urakka
                                  :alkupvm alkupvm
                                  :loppupvm loppupvm}))))

(defn hae-yllapito-toteuma [db user {:keys [urakka id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-muutos-ja-lisatyot user urakka)
  (log/debug "Hae ylläpidon toteuma parametreilla: " (pr-str tiedot))
  (jdbc/with-db-transaction [db db]
    (first (q/hae-muu-tyo db {:urakka urakka
                              :id id}))))

(defn hae-laskentakohteet [db user {:keys [urakka] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-muutos-ja-lisatyot user urakka)
  (log/debug "Hae laskentakohteet urakalle: " (pr-str urakka))
  (jdbc/with-db-transaction [db db]
    (into [] (q/hae-urakan-laskentakohteet db {:urakka urakka}))))

(defn tallenna-yllapito-toteuma [db user {:keys [id urakka selite pvm hinta yllapitoluokka
                                                 laskentakohde] :as toteuma}]
  (log/debug "tallenna ylläpito toteuma:" toteuma)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-muutos-ja-lisatyot user urakka)

  ;; TODO VAADI TOTEUMA KUULUU URAKKAAN! Ja laskentakohde kuuluu urakkaan. Molemmille oma vaadi-funktio, muuten heitä.
  (jdbc/with-db-transaction [db db]
                            (let [lask-kohde {:nimi     (second laskentakohde)
                                              :urakka   urakka
                                              :kayttaja (:id user)}
                                  muu-tyo {:id             id
                                           :urakka         urakka
                                           :selite         selite
                                           :pvm            pvm
                                           :hinta          hinta
                                           :yllapitoluokka yllapitoluokka
                                           :laskentakohde  laskentakohde-id
                                           :kayttaja       (:id user)}]
                              (when (and (nil? (first laskentakohde))
                                         (not= "Ei laskentakohdetta" (second laskentakohde)))
                                (q/luo-uusi-urakan_laskentakohde<! db lask-kohde))
                              (if (:id toteuma)
                                (q/paivita-muu-tyo<! db muu-tyo)
                                (q/luo-uusi-muu-tyo<! db muu-tyo)))
    (hae-yllapito-toteumat db user {:urakka urakka})))

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
