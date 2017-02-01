(ns harja.palvelin.palvelut.yllapitokohteet.maaramuutokset
  "Tämä namespace tarjoaa palvelut päällystysurakan ylläpitokohteen
   määrämuutoksien hallintaan."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.paallystys :as q]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.yha :as yha]
            [harja.id :refer [id-olemassa?]]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]))

(defn vaadi-maaramuutos-kuuluu-urakkaan [db urakka-id maaramuutos-id]
  (assert urakka-id "Urakka pitää olla!")
  (when (id-olemassa? maaramuutos-id)
    (let [maaramuutoksen-todellinen-urakka (:urakka (first (q/hae-maaramuutoksen-urakka db {:id maaramuutos-id})))]
      (when (not= maaramuutoksen-todellinen-urakka urakka-id)
        (throw (SecurityException. (str "Määrämuutos " maaramuutos-id " ei kuulu valittuun urakkaan "
                                        urakka-id " vaan urakkaan " maaramuutoksen-todellinen-urakka)))))))

(def maaramuutoksen-tyon-tyyppi->kantaenum
  {:ajoradan-paallyste "ajoradan_paallyste"
   :pienaluetyot "pienaluetyot"
   :tasaukset "tasaukset"
   :jyrsinnat "jyrsinnat"
   :muut "muut"})

(def maaramuutoksen-tyon-tyyppi->keyword
  {"ajoradan_paallyste" :ajoradan-paallyste
   "pienaluetyot" :pienaluetyot
   "tasaukset" :tasaukset
   "jyrsinnat" :jyrsinnat
   "muut" :muut})

(defn hae-maaramuutokset
  [db user {:keys [yllapitokohde-id urakka-id]}]
  (log/debug "Aloitetaan määrämuutoksien haku")
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (let [maaramuutokset (into []
                             (comp
                               (map #(assoc % :tyyppi (maaramuutoksen-tyon-tyyppi->keyword (:tyyppi %))))
                               (map #(konv/string-polusta->keyword % [:tyyppi])))
                             (q/hae-yllapitokohteen-maaramuutokset db {:id yllapitokohde-id
                                                                       :urakka urakka-id}))]
    (log/debug "Määrämuutokset saatu: " (pr-str maaramuutokset))
    maaramuutokset))

(defn- luo-maaramuutos [db user yllapitokohde-id
                        {:keys [tyyppi tyo yksikko tilattu-maara
                                toteutunut-maara yksikkohinta] :as maaramuutos}]
  (log/debug "Luo määrämuutos: " (pr-str maaramuutos))
  (q/luo-yllapitokohteen-maaramuutos<! db {:yllapitokohde yllapitokohde-id
                                           :tyon_tyyppi (name tyyppi)
                                           :tyo tyo
                                           :yksikko yksikko
                                           :tilattu_maara tilattu-maara
                                           :toteutunut_maara toteutunut-maara
                                           :yksikkohinta yksikkohinta
                                           :luoja (:id user)}))

(defn- paivita-maaramuutos [db user
                            {:keys [:urakka-id :yllapitokohde-id]}
                            {:keys [id tyyppi tyo yksikko tilattu-maara poistettu
                                    toteutunut-maara yksikkohinta] :as maaramuutos}]
  (log/debug "Päivitä määrämuutos: " (pr-str maaramuutos))
  (q/paivita-yllapitokohteen-maaramuutos<! db {:tyon_tyyppi (name tyyppi)
                                               :tyo tyo
                                               :yksikko yksikko
                                               :tilattu_maara tilattu-maara
                                               :toteutunut_maara toteutunut-maara
                                               :yksikkohinta yksikkohinta
                                               :kayttaja (:id user)
                                               :id id
                                               :urakka urakka-id
                                               :poistettu poistettu}))

(defn- luo-tai-paivita-maaramuukset [db user urakka-ja-yllapitokohde maaramuutokset]
  (doseq [maaramuutos maaramuutokset]
    (if-not (id-olemassa? (:id maaramuutos))
      (luo-maaramuutos db user (:yllapitokohde-id urakka-ja-yllapitokohde) maaramuutos)
      (paivita-maaramuutos db user urakka-ja-yllapitokohde maaramuutos))))

(defn liita-yllapitokohteisiin-maaramuutokset
  "Laskee ja liittää päällystyskohteisiin määrämuutokset"
  [db user {:keys [yllapitokohteet urakka-id]}]
  (mapv #(if (= (:yllapitokohdetyotyyppi %) :paallystys)
           (let [kohteen-maaramuutokset
                 (hae-maaramuutokset db user {:yllapitokohde-id (:id %)
                                              :urakka-id urakka-id})]
             (assoc % :maaramuutokset
                      (paallystys-ja-paikkaus/summaa-maaramuutokset kohteen-maaramuutokset)))
           %)
        yllapitokohteet))

(defn tallenna-maaramuutokset
  "Suorittaa annetuille määrämuutoksille lisäys-/päivitysoperaation.
   Palauttaa päivittyneet määrämuutokset sekä ylläpitokohteet."
  [db user {:keys [urakka-id yllapitokohde-id maaramuutokset
                   sopimus-id vuosi]}]
  (log/debug "Aloitetaan määrämuutoksien tallennus")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id yllapitokohde-id)
  (doseq [maaramuutos maaramuutokset]
    (vaadi-maaramuutos-kuuluu-urakkaan db urakka-id (:id maaramuutos)))

  (jdbc/with-db-transaction [db db]
    (let [maaramuutokset (map #(assoc % :tyyppi (maaramuutoksen-tyon-tyyppi->kantaenum (:tyyppi %)))
                              maaramuutokset)]
      (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id yllapitokohde-id)
      (yha/lukitse-urakan-yha-sidonta db urakka-id)
      (luo-tai-paivita-maaramuukset db user {:yllapitokohde-id yllapitokohde-id
                                             :urakka-id urakka-id} maaramuutokset)

      ;; Rakennetaan vastaus
      (let [yllapitokohteet (yy/hae-urakan-yllapitokohteet db user {:urakka-id urakka-id
                                                                    :sopimus-id sopimus-id
                                                                    :vuosi vuosi})
            yllapitokohteet (liita-yllapitokohteisiin-maaramuutokset
                              db user {:yllapitokohteet yllapitokohteet
                                       :urakka-id urakka-id})]
        {:maaramuutokset (hae-maaramuutokset db user {:yllapitokohde-id yllapitokohde-id
                                                      :urakka-id urakka-id})
         :yllapitokohteet yllapitokohteet}))))

(defrecord Maaramuutokset []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :hae-maaramuutokset
                        (fn [user tiedot]
                          (hae-maaramuutokset db user tiedot)))
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
