(ns harja.palvelin.palvelut.yllapitokohteet.maaramuutokset
  "Tämä namespace tarjoaa palvelut päällystysurakan ylläpitokohteen
   määrämuutoksien hallintaan."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.paallystys-kyselyt :as q]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.yha-apurit :as yha-apurit]
            [harja.id :refer [id-olemassa?]]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]))

(defn vaadi-maaramuutos-kuuluu-urakkaan [db urakka-id maaramuutos-id]
  (assert urakka-id "Urakka pitää olla!")
  (when (id-olemassa? maaramuutos-id)
    (let [maaramuutoksen-todellinen-urakka (:urakka (first (q/hae-maaramuutoksen-urakka db {:id maaramuutos-id})))]
      (when (not= maaramuutoksen-todellinen-urakka urakka-id)
        (throw (SecurityException. (str "Määrämuutos " maaramuutos-id " ei kuulu valittuun urakkaan "
                                        urakka-id " vaan urakkaan " maaramuutoksen-todellinen-urakka)))))))

(defn vaadi-maaramuutos-ei-jarjestelman-luoma [db maaramuutos-id]
  (when (id-olemassa? maaramuutos-id)
    (let [maaramuutos-jarjestelman-luoma (:jarjestelman-luoma
                                           (first (q/maaramuutos-jarjestelman-luoma db {:id maaramuutos-id})))]
      (when maaramuutos-jarjestelman-luoma
        (throw (SecurityException. "Määrämuutos on järjestelmän luoma, ei voi muokata!"))))))

(defn- luo-maaramuutos [db user yllapitokohde-id
                        {:keys [tyyppi tyo yksikko tilattu-maara
                                toteutunut-maara yksikkohinta ennustettu-maara] :as maaramuutos}]
  (log/debug "Luo määrämuutos: " (pr-str maaramuutos))
  (q/luo-yllapitokohteen-maaramuutos<! db {:yllapitokohde yllapitokohde-id
                                           :tyon_tyyppi (name tyyppi)
                                           :tyo tyo
                                           :yksikko yksikko
                                           :tilattu_maara tilattu-maara
                                           :ennustettu_maara ennustettu-maara
                                           :toteutunut_maara toteutunut-maara
                                           :yksikkohinta yksikkohinta
                                           :luoja (:id user)
                                           :jarjestelma nil
                                           :ulkoinen_id nil}))


(defn- paivita-maaramuutos [db user
                            {:keys [:urakka-id :yllapitokohde-id]}
                            {:keys [id tyyppi tyo yksikko tilattu-maara poistettu
                                    toteutunut-maara yksikkohinta ennustettu-maara] :as maaramuutos}]
  (log/debug "Päivitä määrämuutos: " (pr-str maaramuutos))
  (q/paivita-yllapitokohteen-maaramuutos<! db {:tyon_tyyppi (name tyyppi)
                                               :tyo tyo
                                               :yksikko yksikko
                                               :tilattu_maara tilattu-maara
                                               :ennustettu_maara ennustettu-maara
                                               :toteutunut_maara toteutunut-maara
                                               :yksikkohinta yksikkohinta
                                               :kayttaja (:id user)
                                               :id id
                                               :urakka urakka-id
                                               :poistettu (true? poistettu)
                                               :jarjestelma nil
                                               :ulkoinen_id nil}))

(defn- luo-tai-paivita-maaramuukset [db user urakka-ja-yllapitokohde maaramuutokset]
  (doseq [maaramuutos maaramuutokset]
    (if-not (id-olemassa? (:id maaramuutos))
      (luo-maaramuutos db user (:yllapitokohde-id urakka-ja-yllapitokohde) maaramuutos)
      (paivita-maaramuutos db user urakka-ja-yllapitokohde maaramuutos))))

(defn tallenna-maaramuutokset
  "Suorittaa annetuille määrämuutoksille lisäys-/päivitysoperaation.
   Palauttaa päivittyneet määrämuutokset sekä ylläpitokohteet."
  [db user {:keys [urakka-id yllapitokohde-id maaramuutokset sopimus-id vuosi]}]
  (log/debug "Aloitetaan määrämuutoksien tallennus")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id yllapitokohde-id)

  (doseq [maaramuutos maaramuutokset]
    (vaadi-maaramuutos-ei-jarjestelman-luoma db (:id maaramuutos))
    (vaadi-maaramuutos-kuuluu-urakkaan db urakka-id (:id maaramuutos)))

  (jdbc/with-db-transaction [db db]
    (let [maaramuutokset (map #(assoc % :tyyppi (yy/maaramuutoksen-tyon-tyyppi->kantaenum (:tyyppi %)))
                              maaramuutokset)]
      (yha-apurit/lukitse-urakan-yha-sidonta db urakka-id)
      (luo-tai-paivita-maaramuukset db user {:yllapitokohde-id yllapitokohde-id
                                             :urakka-id urakka-id} maaramuutokset)

      ;; Rakennetaan vastaus
      {:maaramuutokset (yy/hae-yllapitokohteen-maaramuutokset db {:yllapitokohde-id yllapitokohde-id
                                                                  :urakka-id urakka-id})
       :yllapitokohteet (yy/hae-urakan-yllapitokohteet db {:urakka-id urakka-id
                                                           :sopimus-id sopimus-id
                                                           :vuosi vuosi})})))

(defn hae-ja-summaa-maaramuutokset
  [db {:keys [urakka-id yllapitokohde-id]}]
  (let [maaramuutokset (yy/hae-yllapitokohteen-maaramuutokset
                         db {:yllapitokohde-id yllapitokohde-id
                             :urakka-id urakka-id})]
    (paallystys-ja-paikkaus/summaa-maaramuutokset maaramuutokset)))

(defn hae-yllapitokohteen-maaramuutokset
  [db user {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (yy/hae-yllapitokohteen-maaramuutokset db tiedot))

(defrecord Maaramuutokset []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :hae-maaramuutokset
                        (fn [user tiedot]
                          (hae-yllapitokohteen-maaramuutokset db user tiedot)))
      (julkaise-palvelu http :tallenna-maaramuutokset
                        (fn [user tiedot]
                          (tallenna-maaramuutokset db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-maaramuutokset
      :tallenna-maaramuutokset)
    this))
