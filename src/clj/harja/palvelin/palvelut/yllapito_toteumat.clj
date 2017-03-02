(ns harja.palvelin.palvelut.yllapito-toteumat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.kyselyt.yllapito-toteumat :as q]
            [harja.domain.tiemerkinta-toteumat :as tt]
            [taoensso.timbre :as log]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [harja.id :as id]
            [harja.palvelin.komponentit.http-palvelin
             :refer
             [julkaise-palvelu poista-palvelut]]
            [clojure.java.jdbc :as jdbc]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
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
                                    :id id})))))

(defn hae-laskentakohteet [db user {:keys [urakka] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteutus-muuttyot user urakka)
  (log/debug "Hae laskentakohteet urakalle: " (pr-str urakka))
  (jdbc/with-db-transaction [db db]
    (into [] (q/hae-urakan-laskentakohteet db {:urakka urakka}))))

(defn tallenna-yllapito-toteuma [db user {:keys [id urakka sopimus selite pvm hinta yllapitoluokka
                                                 laskentakohde alkupvm loppupvm uusi-laskentakohde poistettu] :as toteuma}]
  (log/debug "tallenna ylläpito_toteuma:" toteuma)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteutus-muuttyot user urakka)
  (jdbc/with-db-transaction [db db]
    (let [uusi-tallennettava-laskentakohde {:nimi uusi-laskentakohde
                                            :urakka urakka
                                            :kayttaja (:id user)}

          laskentakohde-id (if (first laskentakohde)
                             (first laskentakohde)
                             (when uusi-laskentakohde
                               (:id (q/luo-uusi-urakan_laskentakohde<!
                                      db
                                      uusi-tallennettava-laskentakohde))))
          muu-tyo {:id id
                   :urakka urakka
                   :sopimus sopimus
                   :selite selite
                   :pvm pvm
                   :hinta hinta
                   :yllapitoluokka (:numero yllapitoluokka)
                   :laskentakohde laskentakohde-id
                   :kayttaja (:id user)}]

      (if (:id toteuma)
        (q/paivita-muu-tyo<! db (assoc muu-tyo :poistettu (boolean poistettu)))
        (q/luo-uusi-muu-tyo<! db muu-tyo)))
    (let [vastaus {:toteumat (hae-yllapito-toteumat db user {:urakka urakka :sopimus sopimus
                                                             :alkupvm alkupvm :loppupvm loppupvm})
                   :laskentakohteet (hae-laskentakohteet db user {:urakka urakka})}]
      vastaus)))

(defn hae-tiemerkinnan-yksikkohintaiset-tyot [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteutus-yksikkohintaisettyot user urakka-id)
  (log/debug "Haetaan yksikköhintaiset työt tiemerkintäurakalle: " urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [toteumat (into []
                         (comp
                           (map spec-apurit/poista-nil-avaimet)
                           (map #(konv/string->keyword % :hintatyyppi))
                           (map #(assoc % :hinta (when-let [hinta (:hinta %)] (double hinta)))))
                         (q/hae-tiemerkintaurakan-yksikkohintaiset-tyot
                           db
                           {:urakka urakka-id}))]
      toteumat)))

(defn tallenna-tiemerkinnan-yksikkohintaiset-tyot
  [db user {:keys [urakka-id toteumat]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteutus-yksikkohintaisettyot user urakka-id)
  (jdbc/with-db-transaction [db db]
    (doseq [{:keys [yllapitokohde-id ] :as kohde} toteumat]
      (when yllapitokohde-id (yy/vaadi-yllapitokohde-osoitettu-tiemerkintaurakkaan db urakka-id yllapitokohde-id)))

    (log/debug "Tallennetaan yksikköhintaiset työt tiemerkintäurakalle: " urakka-id)

    (doseq [{:keys [hinta hintatyyppi muutospvm id yllapitokohde-id poistettu
                    selite tr-numero yllapitoluokka pituus hinta-kohteelle] :as kohde} toteumat]
      (let [sql-parametrit {:yllapitokohde yllapitokohde-id
                            :hinta hinta
                            :hintatyyppi (when hintatyyppi (name hintatyyppi))
                            :muutospvm muutospvm
                            :hinta_kohteelle (when yllapitokohde-id hinta-kohteelle)
                            :selite selite
                            :tr_numero (when-not yllapitokohde-id tr-numero)
                            :yllapitoluokka (when-not yllapitokohde-id yllapitoluokka)
                            :pituus (when-not yllapitokohde-id pituus)}]
        (if (id/id-olemassa? id)
          (q/paivita-tiemerkintaurakan-yksikkohintainen-tyo<!
            db (merge sql-parametrit {:id id :urakka urakka-id
                                      :poistettu (or poistettu false)}))
          (q/luo-tiemerkintaurakan-yksikkohintainen-tyo<!
            db (merge sql-parametrit {:urakka urakka-id})))))
    (hae-tiemerkinnan-yksikkohintaiset-tyot db user {:urakka-id urakka-id})))

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
      (julkaise-palvelu http :hae-tiemerkinnan-yksikkohintaiset-tyot
                        (fn [user tiedot]
                          (hae-tiemerkinnan-yksikkohintaiset-tyot db user tiedot))
                        {:kysely-spec ::tt/hae-tiemerkinnan-yksikkohintaiset-tyot-kysely
                         :vastaus-spec ::tt/hae-tiemerkinnan-yksikkohintaiset-tyot-vastaus})
      (julkaise-palvelu http :tallenna-tiemerkinnan-yksikkohintaiset-tyot
                        (fn [user tiedot]
                          (tallenna-tiemerkinnan-yksikkohintaiset-tyot db user tiedot))
                        {:kysely-spec ::tt/tallenna-tiemerkinnan-yksikkohintaiset-tyot-kysely
                         :vastaus-spec ::tt/tallenna-tiemerkinnan-yksikkohintaiset-tyot-vastaus})
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-yllapito-toteumat
      :hae-yllapito-toteuma
      :hae-laskentakohteet
      :tallenna-yllapito-toteuma
      :tallenna-tiemerkinnan-yksikkohintaiset-tyot
      :hae-tiemerkinnan-yksikkohintaiset-tyot)
    this))
