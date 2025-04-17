(ns harja.palvelin.palvelut.kustannusten-kirjaus
  "Palvelut kustannusten kirjauksille ja hauille"
  (:require
   [clojure.java.jdbc :as jdbc]
   [com.stuartsierra.component :as component]
   [harja.domain.oikeudet :as oikeudet]
   [harja.kyselyt.konversio :as konv]
   [harja.kyselyt.kustannusten-kirjaus :as q]
   [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
   [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu
                                                     poista-palvelut]]
   [harja.pvm :as pvm]
   [slingshot.slingshot :refer [throw+]]
   [taoensso.timbre :as log]))

(defn default-kustannuslista
  "Palauttaa oletus nolla-arvot vuosille, joille ei ole merkitty kustannuksia
   urakan alkamisvuoden ja loppumisvuoden perusteella."
  [urakka-id alkuvuosi loppuvuosi]
  (for [x (range alkuvuosi (+ 1 loppuvuosi))]
    (assoc {} :urakka urakka-id :kustannusvuosi x :kustannus 0 :pk1 0 :pk2 0 :pk3 0)))

(defn filteroi-arvoilla [data values]
  (filterv #(not (some (set values) (vals %))) data))

(defn tee-valmis-kustannuslista [vastaus default-lista]
  (let [filteroi-vuodet (into [] (map :kustannusvuosi vastaus))
        filteroitu-lista (filteroi-arvoilla default-lista filteroi-vuodet)]
    (sort-by :kustannusvuosi (concat vastaus filteroitu-lista))))

(defn hae-tiemerkinta-kustannuskirjaukset
  [db user urakka]
  (let [urakka-id (get-in urakka [:urakka :id])
        urakan-alkuvuosi (pvm/vuosi (get-in urakka [:urakka :alkupvm]))
        urakan-loppuvuosi (pvm/vuosi (get-in urakka [:urakka :loppupvm]))
        default-lista (default-kustannuslista urakka-id urakan-alkuvuosi urakan-loppuvuosi)]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-tiemerkinnan user urakka-id)
    (let [vastaus (into []
                    (map #(konv/decimal->double % :kustannus :pk1 :pk2 :pk3)
                      (q/hae-tiemerkinta-kustannuskirjaukset db urakka-id)))]
      (tee-valmis-kustannuslista vastaus default-lista))))



(defn hae-tiemerkinta-kustannuskirjaus-kustannusvuodella
  [db user {:keys [urakka-id kustannusvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-tiemerkinnan user urakka-id)
  (let [vastaus (into [] (map #(konv/decimal->double % :kustannus :pk1 :pk2 :pk3)
                           (q/hae-tiemerkinta-kustannuskirjaus-kustannusvuodella
                             db {:urakka urakka-id :kustannusvuosi kustannusvuosi})))]
    vastaus))


(defn validoi-rivi [rivi]
  (let [summa (->> [:pk1 :pk2 :pk3]
                (map #(get rivi % 0))
                (reduce +)
                float)]
    (when-not (= 100.0 summa)
      (log/error "PK-osuuksien summan on oltava 100, saatiin:" summa)
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+
                          :viesti "PK-osuuksien summan on oltava 100"}]}))))


(defn tallenna-tiemerkinta-kustannuskirjaukset
  [db user tiedot]
  (let [urakka-id (get-in tiedot [:urakka :id])]
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-tiemerkinnan user)
    (doseq [tieto (:tiedot tiedot)]
      (when (< 0.0M (bigdec (:kustannus tieto)))
        (validoi-rivi tieto)))
    (doseq [tieto (:tiedot tiedot)]
      (if (empty? (hae-tiemerkinta-kustannuskirjaus-kustannusvuodella db user {:urakka-id urakka-id :kustannusvuosi (:kustannusvuosi tieto)}))
        (q/lisaa-tiemerkinta-kustannuskirjaus! db
          (assoc tieto :luoja (:id user) :muokkaaja (:id user) :muokattu (pvm/nyt)))
        (q/paivita-tiemerkinta-kustannuskirjaus! db
          (assoc tieto :muokkaaja (:id user) :muokattu (pvm/nyt)))))
    tiedot))

(defn hae-tiemerkinta-paallystyskohteiden-kustannukset
  [db kayttaja {:keys [urakka-id urakka-alkupvm yllapitokohdetyotyyppi]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-tiemerkinnan kayttaja urakka-id)
  (let [vuosi (pvm/vuosi urakka-alkupvm)]
    (q/hae-urakan-yllapitokohteiden-kustannukset db {:urakka urakka-id 
                                                     :yllapitokohdetyotyyppi (or yllapitokohdetyotyyppi  "paallystys") 
                                                     :vuosi vuosi})))

(defn hae-tiemerkinta-paikkausten-kustannukset
  [db kayttaja {:keys [urakka-id urakka-alkupvm]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-tiemerkinnan kayttaja urakka-id)
  (let [vuosi (pvm/vuosi urakka-alkupvm)]
    (q/hae-urakan-paikkauskohteiden-kustannukset db {:urakka-id urakka-id :vuosi vuosi})))

(defn tallenna-tiemerkinta-yllapitokohteiden-kustannukset
  [db user {:keys [tiedot]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-tiemerkinnan user)
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
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-tiemerkinnan user)
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


(defn hae-tiemerkinta-muut-kustannukset
  [db kayttaja {:keys [urakka-id]}]
  ;; TODO 
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)
  (q/hae-tiemerkinta-muut-kustannukset db))

(defn hae-tiemerkinta-kustannustyypit
  [db kayttaja {:keys [urakka-id]}]
  ;; TODO 
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)
  (q/hae-tiemerkinta-kustannustyypit db))

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

    ;; Uudet päällysteet
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

    ;; Muut kustannukset 
    (julkaise-palvelu (:http-palvelin this) :hae-tiemerkinta-muut-kustannukset
      (fn [kayttaja tiedot] (hae-tiemerkinta-muut-kustannukset (:db this) kayttaja tiedot)))
    
    (julkaise-palvelu (:http-palvelin this) :hae-tiemerkinta-kustannustyypit
      (fn [kayttaja tiedot] (hae-tiemerkinta-kustannustyypit (:db this) kayttaja tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-tiemerkinta-kustannuskirjaus
      :hae-tiemerkinta-kustannuskirjaus-kustannusvuodella
      :tallenna-tiemerkinta-kustannuskirjaus
      
      ;; Uudet päällysteet
      :hae-tiemerkinta-paallystyskohteiden-kustannukset
      :hae-tiemerkinta-paikkausten-kustannukset
      :tallenna-tiemerkinta-yllapitokohteiden-kustannukset
      :tallenna-tiemerkinta-paikkauskohteiden-kustannukset)
    this))
