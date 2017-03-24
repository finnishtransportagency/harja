(ns harja.palvelin.palvelut.yllapito-toteumat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.kyselyt.yllapito-muut-toteumat :as q]
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

(defn vaadi-toteuma-kuuluu-urakkaan [db toteuma-id vaitetty-urakka-id]
  (log/debug "Tarkistetaan, että toteuma " toteuma-id " kuuluu väitettyyn urakkaan " vaitetty-urakka-id)
  (assert vaitetty-urakka-id "Urakka id puuttuu!")
  (when toteuma-id
    (let [toteuman-todellinen-urakka-id (:urakka (first
                                                   (q/muun-toteuman-urakka
                                                     db {:toteuma toteuma-id})))]
      (when (and (some? toteuman-todellinen-urakka-id)
                 (not= toteuman-todellinen-urakka-id vaitetty-urakka-id))
        (throw (SecurityException. (str "Toteuma ei kuulu väitettyyn urakkaan " vaitetty-urakka-id
                                        " vaan urakkaan " toteuman-todellinen-urakka-id)))))))

(def muutyo-xf
  (comp
    yllapitokohteet-domain/yllapitoluokka-xf
    (map #(konv/string->keyword % :tyyppi))
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

(defn tallenna-yllapito-toteumat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm toteumat]}]
  (log/debug "Tallenna ylläpidon toteuma:" toteumat)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteutus-muuttyot user urakka-id)

  (jdbc/with-db-transaction [db db]
    (doseq [{:keys [id] :as toteuma} toteumat]
      (vaadi-toteuma-kuuluu-urakkaan db id urakka-id))

    (doseq [toteuma toteumat]
      (let [{:keys [id selite pvm hinta tyyppi yllapitoluokka laskentakohde uusi-laskentakohde poistettu]} toteuma
            uusi-tallennettava-laskentakohde {:nimi uusi-laskentakohde
                                              :urakka urakka-id
                                              :kayttaja (:id user)}

            laskentakohde-id (if (first laskentakohde)
                               (first laskentakohde)
                               (when uusi-laskentakohde
                                 (:id (q/luo-uusi-urakan_laskentakohde<!
                                        db
                                        uusi-tallennettava-laskentakohde))))
            muu-tyo {:id id
                     :urakka urakka-id
                     :sopimus sopimus-id
                     :selite selite
                     :pvm pvm
                     :hinta hinta
                     :tyyppi (if tyyppi
                               (name tyyppi)
                               "muu")
                     :yllapitoluokka (:numero yllapitoluokka)
                     :laskentakohde laskentakohde-id
                     :poistettu (boolean poistettu)
                     :kayttaja (:id user)}]

        (if (id/id-olemassa? (:id toteuma))
          (q/paivita-muu-tyo<! db (assoc muu-tyo :poistettu (boolean poistettu)))
          (q/luo-uusi-muu-tyo<! db muu-tyo))))
    (let [vastaus {:toteumat (hae-yllapito-toteumat db user {:urakka urakka-id :sopimus sopimus-id
                                                             :alkupvm alkupvm :loppupvm loppupvm})
                   :laskentakohteet (hae-laskentakohteet db user {:urakka urakka-id})}]
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
    (doseq [{:keys [id yllapitokohde-id] :as kohde} toteumat]
      (vaadi-toteuma-kuuluu-urakkaan db id urakka-id)
      (when yllapitokohde-id (yy/vaadi-yllapitokohde-osoitettu-tiemerkintaurakkaan db urakka-id yllapitokohde-id)))

    (log/debug "Tallennetaan yksikköhintaiset työt tiemerkintäurakalle: " urakka-id)

    (doseq [{:keys [hinta hintatyyppi paivamaara id yllapitokohde-id poistettu
                    selite tr-numero yllapitoluokka pituus hinta-kohteelle] :as kohde} toteumat]
      (let [sql-parametrit {:yllapitokohde yllapitokohde-id
                            :hinta hinta
                            :hintatyyppi (when hintatyyppi (name hintatyyppi))
                            :paivamaara paivamaara
                            :hinta_kohteelle (when yllapitokohde-id hinta-kohteelle)
                            :selite selite
                            :tr_numero (when-not yllapitokohde-id tr-numero)
                            :yllapitoluokka (when-not yllapitokohde-id yllapitoluokka)
                            :pituus (when-not yllapitokohde-id pituus)
                            :ulkoinen_id nil
                            :luoja nil}]
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
      (julkaise-palvelu http :tallenna-yllapito-toteumat
                        (fn [user tiedot]
                          (tallenna-yllapito-toteumat db user tiedot)))
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
      :tallenna-yllapito-toteumat
      :tallenna-tiemerkinnan-yksikkohintaiset-tyot
      :hae-tiemerkinnan-yksikkohintaiset-tyot)
    this))
