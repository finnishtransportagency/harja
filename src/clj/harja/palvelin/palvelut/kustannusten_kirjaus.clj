(ns harja.palvelin.palvelut.kustannusten-kirjaus
  "Palvelut kustannusten kirjauksille ja hauille"
  (:require
   [taoensso.timbre :as log]
   [clojure.java.jdbc :as jdbc]
   [com.stuartsierra.component :as component]

   [harja.pvm :as pvm]
   [harja.domain.oikeudet :as oikeudet]
   [harja.kyselyt.konversio :as konv]
   [harja.kyselyt.kustannusten-kirjaus :as q]
   [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
   [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta]
   [harja.palvelin.palvelut.yllapito-toteumat :as yllapito-toteumat]
   [harja.palvelin.palvelut.yllapitokohteet.tiemerkinta-apurit :as apurit]))

(defn hae-tiemerkinta-kustannuskirjaukset
  [db user urakka]
  (let [urakka-id (get-in urakka [:urakka :id])
        urakan-alkuvuosi (pvm/vuosi (get-in urakka [:urakka :alkupvm]))
        urakan-loppuvuosi (pvm/vuosi (get-in urakka [:urakka :loppupvm]))
        default-lista (apurit/default-kustannuslista urakka-id urakan-alkuvuosi urakan-loppuvuosi)]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-tiemerkinta-kustannukset user urakka-id)
    (let [vastaus (into []
                    (map #(konv/decimal->double % :kustannus :pk1 :pk2 :pk3)
                      (q/hae-tiemerkinta-kustannuskirjaukset db urakka-id)))]
      (apurit/tee-valmis-kustannuslista vastaus default-lista))))

(defn hae-tiemerkinta-kustannuskirjaus-kustannusvuodella
  [db user {:keys [urakka-id kustannusvuosi] :as _tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-tiemerkinta-kustannukset user urakka-id)

  (let [vastaus (into []
                  (map #(konv/decimal->double % :kustannus :pk1 :pk2 :pk3)
                    (q/hae-tiemerkinta-kustannuskirjaus-kustannusvuodella
                      db {:urakka urakka-id :kustannusvuosi kustannusvuosi})))]
    vastaus))

(defn tallenna-tiemerkinta-kustannuskirjaukset
  [db user tiedot]
  (let [urakka-id (get-in tiedot [:urakka :id])]
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-tiemerkinta-kustannukset user)

    (doseq [tieto (:tiedot tiedot)]
      (when (< 0.0M (bigdec (:kustannus tieto)))
        (apurit/validoi-kustannuskirjaus-rivi tieto)))
    
    (doseq [tieto (:tiedot tiedot)]
      (if (empty? (hae-tiemerkinta-kustannuskirjaus-kustannusvuodella db user {:urakka-id urakka-id :kustannusvuosi (:kustannusvuosi tieto)}))
        (q/lisaa-tiemerkinta-kustannuskirjaus! db
          (assoc tieto :luoja (:id user) :muokkaaja (:id user) :muokattu (pvm/nyt)))
        (q/paivita-tiemerkinta-kustannuskirjaus! db
          (assoc tieto :muokkaaja (:id user) :muokattu (pvm/nyt)))))
    tiedot))

(defn hae-tiemerkinta-paallystyskohteiden-kustannukset
  [db kayttaja {:keys [urakka-id urakka-alkupvm yllapitokohdetyotyyppi]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-tiemerkinta-kustannukset kayttaja urakka-id)
  (let [vuosi (if urakka-alkupvm (pvm/vuosi urakka-alkupvm) 0)]
    (q/hae-urakan-yllapitokohteiden-kustannukset db {:urakka urakka-id 
                                                     :yllapitokohdetyotyyppi (or yllapitokohdetyotyyppi  "paallystys") 
                                                     :vuosi vuosi})))

(defn hae-tiemerkinta-paikkausten-kustannukset
  [db kayttaja {:keys [urakka-id urakka-alkupvm]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-tiemerkinta-kustannukset kayttaja urakka-id)
  (let [vuosi (if urakka-alkupvm (pvm/vuosi urakka-alkupvm) 0)]
    (q/hae-urakan-paikkauskohteiden-kustannukset db {:urakka-id urakka-id :vuosi vuosi})))

(defn tallenna-tiemerkinta-yllapitokohteiden-kustannukset
  [db user {:keys [tiedot]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-tiemerkinta-kustannukset user)
  (try
    (jdbc/with-db-transaction [db db]
      (let [tulokset (mapv (fn [tieto]
                             (let [tieto-muokattu (assoc tieto
                                                    :muokkaaja (:id user)
                                                    :muokattu (pvm/nyt))]
                               (if (empty? (q/hae-yllapitokustannus db {:yllapitokohde (:id tieto)}))
                                 (q/lisaa-tiemerkinta-yllapitokohde-kustannuskirjaus! db
                                   (assoc tieto-muokattu :luoja (:id user)))
                                 (q/paivita-tiemerkinta-yllapitokohde-kustannuskirjaus! db
                                   tieto-muokattu))))
                       tiedot)]
        {:onnistui true
         :paivitetyt tulokset}))
    (catch Exception e
      (log/error e "Virhe tallennettaessa yllapitokohteiden kustannuksia")
      {:onnistui false
       :virhe (.getMessage e)})))

(defn tallenna-tiemerkinta-paikkauskohteiden-kustannukset
  [db user {:keys [tiedot]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-tiemerkinta-kustannukset user)
  (try
    (jdbc/with-db-transaction [db db]
      (let [tulokset (mapv (fn [tieto]
                             (let [tieto-muokattu (assoc tieto
                                                    :muokkaaja (:id user)
                                                    :muokattu (pvm/nyt))]
                               (if (empty? (q/hae-paikkauskustannus db {:paikkauskohde (:id tieto)}))
                                 (q/lisaa-tiemerkinta-paikkauskohde-kustannuskirjaus! db
                                   (assoc tieto-muokattu :luoja (:id user)))
                                 (q/paivita-tiemerkinta-paikkauskohde-kustannuskirjaus! db
                                   tieto-muokattu))))
                       tiedot)]
        {:onnistui true
         :paivitetyt tulokset}))
    (catch Exception e
      (log/error e "Virhe tallennettaessa paikkausten kustannuksia")
      {:onnistui false
       :virhe (.getMessage e)})))

(defn hae-tiemerkinta-kustannustyypit
  [db kayttaja {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-tiemerkinta-kustannukset kayttaja urakka-id)
  (q/hae-tiemerkinta-kustannustyypit db))

(defn hae-tiemerkinta-yhteenveto
  "Haetaan ja lasketaan tiemerkinnän yhteenvetoon kaikki siihen kuuluvat kustannukset"
  [db kayttaja {:keys [urakan-tiedot valittu-aikavali kaikki? sopimus] :as _tiedot}]
  (let [urakka-id (:id urakan-tiedot)
        _ (oikeudet/vaadi-lukuoikeus oikeudet/urakat-tiemerkinta-kustannukset kayttaja urakka-id)

        ;; Tiemerkintöjen korjaus 
        korjaus-kustannukset (hae-tiemerkinta-kustannuskirjaukset db kayttaja {:urakka urakan-tiedot})
        korjaus-kustannukset (apurit/laske-korjaukset korjaus-kustannukset valittu-aikavali)

        ;; Uusien päällysteiden tiemerkinnät (paikkaus)
        paikkaus-kustannukset (hae-tiemerkinta-paikkausten-kustannukset db kayttaja {:urakka-id urakka-id
                                                                                     :urakka-alkupvm (if kaikki? nil (-> valittu-aikavali first))})

        paikkaus-kustannukset (apurit/laske-tiemerkintakustannukset paikkaus-kustannukset :paikkausten-merkinnat)

        ;; Uusien päällysteiden tiemerkinnät (päällystys)
        paallystys-kustannukset (hae-tiemerkinta-paallystyskohteiden-kustannukset db kayttaja {:urakka-id urakka-id
                                                                                               :urakka-alkupvm (if kaikki? nil (-> valittu-aikavali first))})

        paallystys-kustannukset (apurit/laske-tiemerkintakustannukset paallystys-kustannukset :paallysteiden-merkinnat)

        ;; Sanktiot ja bonukset
        sanktiot-ja-bonukset (laadunseuranta/hae-urakan-sanktiot-ja-bonukset db kayttaja {:hae-sanktiot? true
                                                                                          :hae-bonukset? true
                                                                                          :urakka-id urakka-id
                                                                                          :alku      (-> valittu-aikavali first)
                                                                                          :loppu     (-> valittu-aikavali second)})
        sanktiot-ja-bonukset (apurit/laske-sakot sanktiot-ja-bonukset)

        ;; Muut kustannukset 
        muut-kustannukset (yllapito-toteumat/hae-yllapito-toteumat db kayttaja {:urakka  urakka-id
                                                                                :sopimus sopimus
                                                                                :alkupvm  (-> valittu-aikavali first)
                                                                                :loppupvm (-> valittu-aikavali second)})
        muut-kustannukset (apurit/laske-muut muut-kustannukset)


        yhteenveto (into [] (concat
                              korjaus-kustannukset
                              paikkaus-kustannukset
                              paallystys-kustannukset
                              sanktiot-ja-bonukset
                              muut-kustannukset))

        yhteenveto (conj yhteenveto (apurit/laske-yhteensa yhteenveto))]
    yhteenveto))

(defrecord TiemerkinnanKustannusKirjaukset []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-tiemerkinta-kustannuskirjaus
      (fn [kayttaja urakka]
        (hae-tiemerkinta-kustannuskirjaukset (:db this) kayttaja urakka)))

    (julkaise-palvelu (:http-palvelin this)
      :hae-tiemerkinta-kustannuskirjaus-kustannusvuodella
      (fn [kayttaja tiedot]
        (hae-tiemerkinta-kustannuskirjaus-kustannusvuodella (:db this) kayttaja tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :tallenna-tiemerkinta-kustannuskirjaus
      (fn [kayttaja tiedot]
        (tallenna-tiemerkinta-kustannuskirjaukset (:db this) kayttaja tiedot)))

    (julkaise-palvelu (:http-palvelin this) :hae-tiemerkinta-paallystyskohteiden-kustannukset
      (fn [kayttaja tiedot] 
        (hae-tiemerkinta-paallystyskohteiden-kustannukset (:db this) kayttaja tiedot)))
    
    (julkaise-palvelu (:http-palvelin this) :hae-tiemerkinta-paikkausten-kustannukset
      (fn [kayttaja tiedot]
        (hae-tiemerkinta-paikkausten-kustannukset (:db this) kayttaja tiedot)))
    
    (julkaise-palvelu (:http-palvelin this) :tallenna-tiemerkinta-yllapitokohteiden-kustannukset
      (fn [kayttaja tiedot]
        (tallenna-tiemerkinta-yllapitokohteiden-kustannukset (:db this) kayttaja tiedot)))
    
    (julkaise-palvelu (:http-palvelin this) :tallenna-tiemerkinta-paikkauskohteiden-kustannukset
      (fn [kayttaja tiedot]
        (tallenna-tiemerkinta-paikkauskohteiden-kustannukset (:db this) kayttaja tiedot)))
    
    (julkaise-palvelu (:http-palvelin this) :hae-tiemerkinta-kustannustyypit
      (fn [kayttaja tiedot] (hae-tiemerkinta-kustannustyypit (:db this) kayttaja tiedot)))
    
    (julkaise-palvelu (:http-palvelin this) :hae-tiemerkinta-yhteenveto
      (fn [kayttaja tiedot] (hae-tiemerkinta-yhteenveto (:db this) kayttaja tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-tiemerkinta-kustannuskirjaus
      :hae-tiemerkinta-kustannuskirjaus-kustannusvuodella
      :tallenna-tiemerkinta-kustannuskirjaus
      :hae-tiemerkinta-kustannustyypit
      :hae-tiemerkinta-paallystyskohteiden-kustannukset
      :hae-tiemerkinta-paikkausten-kustannukset
      :tallenna-tiemerkinta-yllapitokohteiden-kustannukset
      :tallenna-tiemerkinta-paikkauskohteiden-kustannukset
      :hae-tiemerkinta-yhteenveto)
    this))
