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
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]))

(defn- maarittele-hinnan-kohde
  "Palauttaa stringin, jossa on ylläpitokohteen tieosoitteen tiedot. Käytetään tunnistamaan tilanne,
   jossa hinta on annettu ylläpitokohteen vanhalle tieosoitteelle."
  [{:keys [tr-numero tr-alkuosa tr-alkuetaisyys
           tr-loppuosa tr-loppuetaisyys] :as kohde}]
  ;; Tod.näk. et halua muuttaa tätä ainakaan migratoimatta kannassa olevaa dataa.
  (str tr-numero " / " tr-alkuosa " / " tr-alkuetaisyys " / "
       tr-loppuosa " / " tr-loppuetaisyys))

(defn- lisaa-yllapitokohteelle-tieto-hinnan-muuttumisesta [kohde]
  (let [hinnan-kohde-eri-kuin-nykyinen-osoite?
        (and (:hinta-kohteelle kohde)
             (not= (maarittele-hinnan-kohde kohde)
                   (:hinta-kohteelle kohde)))]
    (assoc kohde :hinnan-kohde-muuttunut? hinnan-kohde-eri-kuin-nykyinen-osoite?)))

(def muutyo-xf
  (comp
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
                                                 laskentakohde alkupvm loppupvm uusi-laskentakohde] :as toteuma}]
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
                   :yllapitoluokka yllapitoluokka
                   :laskentakohde laskentakohde-id
                   :kayttaja (:id user)}]

      (if (:id toteuma)
        (q/paivita-muu-tyo<! db muu-tyo)
        (q/luo-uusi-muu-tyo<! db muu-tyo)))
    (let [vastaus {:toteumat (hae-yllapito-toteumat db user {:urakka urakka :sopimus sopimus
                                                             :alkupvm alkupvm :loppupvm loppupvm})
                   :laskentakohteet (hae-laskentakohteet db user {:urakka urakka})}]
      vastaus)))

(defn hae-tiemerkinnan-yksikkohintaiset-tyot [db user {:keys [urakka-id]}]
  (assert urakka-id "anna urakka-id")
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteutus-yksikkohintaisettyot user urakka-id)
  (log/debug "Haetaan yksikköhintaiset työt tiemerkintäurakalle: " urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [kohteet (into []
                        (map #(konv/string->keyword % :hintatyyppi))
                        (q/hae-tiemerkintaurakan-yksikkohintaiset-tyot
                          db
                          {:suorittava_tiemerkintaurakka urakka-id}))
          kohteet (mapv (partial yy/lisaa-yllapitokohteelle-kohteen-pituus db) kohteet)
          kohteet (mapv lisaa-yllapitokohteelle-tieto-hinnan-muuttumisesta kohteet)]
      kohteet)))

(defn tallenna-tiemerkinnan-yksikkohintaiset-tyot [db user {:keys [urakka-id kohteet]}]
  (assert urakka-id "anna urakka-id")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-toteutus-yksikkohintaisettyot user urakka-id)
  (log/debug "Tallennetaan yksikköhintaiset työt " kohteet " tiemerkintäurakalle: " urakka-id)
  (jdbc/with-db-transaction [db db]
    (doseq [{:keys [hinta hintatyyppi muutospvm id] :as kohde} kohteet]
      (let [hinta-osoitteelle (maarittele-hinnan-kohde kohde)
            tiedot (first (q/hae-yllapitokohteen-tiemerkintaurakan-yksikkohintaiset-tyot
                            db
                            {:yllapitokohde id}))]
        (if tiedot
          (q/paivita-tiemerkintaurakan-yksikkohintainen-tyo<! db {:hinta hinta
                                                                  :hintatyyppi (when hintatyyppi (name hintatyyppi))
                                                                  :muutospvm muutospvm
                                                                  :hinta_kohteelle (when hinta
                                                                                     hinta-osoitteelle)
                                                                  :yllapitokohde id})
          (q/luo-tiemerkintaurakan-yksikkohintainen-tyo<! db {:hinta hinta
                                                              :hintatyyppi (when hintatyyppi (name hintatyyppi))
                                                              :muutospvm muutospvm
                                                              :hinta_kohteelle (when hinta
                                                                                 hinta-osoitteelle)
                                                              :yllapitokohde id}))))
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
                          (hae-tiemerkinnan-yksikkohintaiset-tyot db user tiedot)))
      (julkaise-palvelu http :tallenna-tiemerkinnan-yksikkohintaiset-tyot
                        (fn [user tiedot]
                          (tallenna-tiemerkinnan-yksikkohintaiset-tyot db user tiedot)))
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
