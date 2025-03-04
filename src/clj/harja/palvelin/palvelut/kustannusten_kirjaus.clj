(ns harja.palvelin.palvelut.kustannusten-kirjaus
  "Palvelut kustannusten kirjauksille ja hauille"
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.kustannusten-kirjaus :as q]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(defn default-kustannuslista [urakka-id alkuvuosi loppuvuosi]
  "Palauttaa oletus nolla-arvot vuosille, joille ei ole merkitty kustannuksia
  urakan alkamisvuoden ja loppumisvuoden perusteella."
  (for [x (range alkuvuosi (+ 1 loppuvuosi))]
    (assoc {} :urakka urakka-id :kustannusvuosi x :kustannus 0 :pk1 0 :pk2 0 :pk3 0)))

(defn filter-by-values [data values]
  (filterv #(not (some (set values) (vals %))) data))

(defn tee-valmis-kustannuslista [vastaus default-lista]
  (let [filteroi-vuodet (into [] (map :kustannusvuosi vastaus))
        filteroitu-lista (filter-by-values default-lista filteroi-vuodet)]
    (sort-by :kustannusvuosi (concat vastaus filteroitu-lista))))

(defn hae-tiemerkinta-kustannuskirjaukset
  [db user urakka]
  (let [urakka-id (get-in urakka [:urakka :id])
        urakan-alkuvuosi (pvm/vuosi (get-in urakka [:urakka :alkupvm]))
        urakan-loppuvuosi (pvm/vuosi (get-in urakka [:urakka :loppupvm]))
        default-lista (default-kustannuslista urakka-id urakan-alkuvuosi urakan-loppuvuosi)]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-tiemerkinnan user urakka-id)
    (let [vastaus (into []
                    (map konv/alaviiva->rakenne)
                    (q/hae-tiemerkinta-kustannuskirjaus db urakka-id))]
      (tee-valmis-kustannuslista vastaus default-lista))))

(defn tallenna-tiemerkinta-kustannuskirjaukset
  [db user tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-tiemerkinnan user)
  (println "tiedot: " (:tiedot tiedot))
  (doseq [tieto (:tiedot tiedot)]
    (assert (= 100.0 (float (reduce + (map #(get tieto % 0) [:pk1 :pk2 :pk3]))))) "PK-osuuksien summa on oltava 100")
  (doseq [tieto (:tiedot tiedot)]
    (q/tallenna-tiemerkinta-kustannuskirjaus db
      (assoc tieto :luoja (:id user) :muokkaaja (:id user) :muokattu (pvm/nyt))))
  tiedot)


(defrecord TiemerkinnanKustannusKirjaukset []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-tiemerkinta-kustannuskirjaus
      (fn [kayttaja urakka]
        (hae-tiemerkinta-kustannuskirjaukset (:db this) kayttaja urakka)))

    (julkaise-palvelu (:http-palvelin this)
      :tallenna-tiemerkinta-kustannuskirjaus
      (fn [kayttaja tiedot]
        (tallenna-tiemerkinta-kustannuskirjaukset (:db this) kayttaja tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-tiemerkinta-kustannuskirjaus
      :tallenna-tiemerkinta-kustannuskirjaus)
    this))


