(ns harja.palvelin.palvelut.yllapito-toteumat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.kyselyt.yllapito-toteumat :as q]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin
             :refer
             [julkaise-palvelu poista-palvelut]]
            [clojure.java.jdbc :as jdbc]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.yllapitokohteet :as yllapitokohteet-domain]
            [harja.kyselyt.konversio :as konv]))

(def muutyo-xf
  (comp
    yllapitokohteet-domain/yllapitoluokka-xf
    (map #(assoc % :laskentakohde [(get-in % [:laskentakohde-id])
                                  (get-in % [:laskentakohde-nimi])]))
    (map #(dissoc % :laskentakohde-id :laskentakohde-nimi))))

(defn hae-yllapito-toteumat [db user {:keys [urakka sopimus alkupvm loppupvm] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteutus-muuttyot user urakka)
  (log/debug "Hae ylläpidon toteumat parametreilla: " (pr-str tiedot))
  (jdbc/with-db-transaction [db db]
    (into []
          muutyo-xf
          (q/hae-muut-tyot db {:urakka urakka
                               :sopimus sopimus
                               :alkupvm alkupvm
                               :loppupvm loppupvm}))))




(defn hae-yllapito-toteuma [db user {:keys [urakka id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteutus-muuttyot user urakka)
  (log/debug "Hae ylläpidon toteuma parametreilla: " (pr-str tiedot))
  (jdbc/with-db-transaction [db db]
    (first (into []
                 muutyo-xf
                 (q/hae-muu-tyo db {:urakka urakka
                                    :id     id})))))

(defn hae-laskentakohteet [db user {:keys [urakka] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteutus-muuttyot user urakka)
  (log/debug "Hae laskentakohteet urakalle: " (pr-str urakka))
  (jdbc/with-db-transaction [db db]
    (into [] (q/hae-urakan-laskentakohteet db {:urakka urakka}))))

(defn tallenna-yllapito-toteuma [db user {:keys [id urakka sopimus selite pvm hinta yllapitoluokka
                                                 laskentakohde alkupvm loppupvm uusi-laskentakohde] :as toteuma }]
  (log/debug "tallenna ylläpito_toteuma:" toteuma)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteutus-muuttyot user urakka)
  (jdbc/with-db-transaction [db db]
                            (let [uusi-tallennettava-laskentakohde {:nimi     uusi-laskentakohde
                                                                    :urakka   urakka
                                                                    :kayttaja (:id user)}
                                  laskentakohde-id (if (first laskentakohde)
                                                     (first laskentakohde)
                                                     (when uusi-laskentakohde
                                                       (:id (q/luo-uusi-urakan_laskentakohde<!
                                                              db
                                                              uusi-tallennettava-laskentakohde))))
                                  muu-tyo {:id             id
                                           :urakka         urakka
                                           :sopimus        sopimus
                                           :selite         selite
                                           :pvm            pvm
                                           :hinta          hinta
                                           :yllapitoluokka (:numero yllapitoluokka)
                                           :laskentakohde  laskentakohde-id
                                           :kayttaja       (:id user)}]

                              (if (:id toteuma)
                                (q/paivita-muu-tyo<! db muu-tyo)
                                (q/luo-uusi-muu-tyo<! db muu-tyo)))
    (let [vastaus {:toteumat        (hae-yllapito-toteumat db user {:urakka  urakka :sopimus sopimus
                                                                    :alkupvm alkupvm :loppupvm loppupvm})
                   :laskentakohteet (hae-laskentakohteet db user {:urakka urakka})}]
      vastaus)))

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
