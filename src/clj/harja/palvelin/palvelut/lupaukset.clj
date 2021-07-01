(ns harja.palvelin.palvelut.lupaukset
  "Palvelu välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.id :refer [id-olemassa?]]
            [harja.kyselyt
             [lupaukset :as lupaukset-q]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]))

(defn- hae-urakan-lupaustiedot [db user tiedot]
  (println "hae-urakan-lupaustiedot " tiedot)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-valitavoitteet user (:urakka-id tiedot))
  (first
    (lupaukset-q/hae-urakan-lupaustiedot db {:urakkaid (:urakka-id tiedot)})))

(defn vaadi-lupaus-kuuluu-urakkaan
  "Tarkistaa, että lupaus kuuluu annettuun urakkaan"
  [db urakka-id lupaus-id]
  (when (id-olemassa? lupaus-id)
    (let [lupauksen-urakka (:urakka (first (lupaukset-q/hae-lupauksen-urakkatieto db {:id lupaus-id})))]
      (when-not (= lupauksen-urakka urakka-id)
        (throw (SecurityException. (str "Lupaus " lupaus-id " ei kuulu valittuun urakkaan "
                                        urakka-id " vaan urakkaan " lupauksen-urakka)))))))

(defn- tallenna-urakan-luvatut-pisteet
  [db user tiedot]
  (println "tallenna-urakan-luvatut-pisteet tiedot " tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user (:urakka-id tiedot))
  (vaadi-lupaus-kuuluu-urakkaan db (:urakka-id tiedot) (:id tiedot))
  (let [params {:id (:id tiedot)
                :urakkaid (:urakka-id tiedot)
                :pisteet (:pisteet tiedot)
                :kayttaja (:id user)}
        vastaus (if (:id tiedot)
                  (lupaukset-q/paivita-urakan-luvatut-pisteet<! db params)
                  (lupaukset-q/lisaa-urakan-luvatut-pisteet<! db params))]
    vastaus))

(defrecord Lupaukset []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakan-lupaustiedot
                      (fn [user tiedot]
                        (hae-urakan-lupaustiedot (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
                      :tallenna-luvatut-pisteet
                      (fn [user tiedot]
                        (tallenna-urakan-luvatut-pisteet (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-urakan-lupaustiedot
                     :tallenna-luvatut-pisteet)
    this))