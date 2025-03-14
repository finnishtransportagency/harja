(ns harja.palvelin.palvelut.kustannusten-kirjaus
  "Palvelut kustannusten kirjauksille ja hauille"
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.kustannusten-kirjaus :as q]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

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

(defn tallenna-tiemerkinta-kustannuskirjaukset
  [db user tiedot]
  (let [urakka-id (get-in tiedot [:urakka :id])]
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-tiemerkinnan user)
    (doseq [tieto (:tiedot tiedot)]
      (assert (= 100.0 (float (reduce + (map #(get tieto % 0) [:pk1 :pk2 :pk3]))))) "PK-osuuksien summa on oltava 100")
    (doseq [tieto (:tiedot tiedot)]
      (if (empty? (hae-tiemerkinta-kustannuskirjaus-kustannusvuodella db user {:urakka-id urakka-id :kustannusvuosi (:kustannusvuosi tieto)}))
        (q/lisaa-tiemerkinta-kustannuskirjaus! db
          (assoc tieto :luoja (:id user) :muokkaaja (:id user) :muokattu (pvm/nyt)))
        (q/paivita-tiemerkinta-kustannuskirjaus! db
          (assoc tieto :muokkaaja (:id user) :muokattu (pvm/nyt)))))
    tiedot))


(defn hae-tiemerkinta-muut-kustannukset
  [db user {:keys [urakka-id] :as tiedot}]
  ;; TODO 
  (oikeudet/ei-oikeustarkistusta!)
  (q/hae-tiemerkinta-muut-kustannukset db))


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

    ;; Muut kustannukset 
    (julkaise-palvelu (:http-palvelin this) :hae-tiemerkinta-muut-kustannukset
      (fn [kayttaja tiedot] (hae-tiemerkinta-muut-kustannukset (:db this) kayttaja tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-tiemerkinta-kustannuskirjaus
      :hae-tiemerkinta-kustannuskirjaus-kustannusvuodella
      :tallenna-tiemerkinta-kustannuskirjaus

      ;; Muut kustannukset
      :hae-tiemerkinta-muut-kustannukset)
    this))


