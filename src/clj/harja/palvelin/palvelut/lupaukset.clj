(ns harja.palvelin.palvelut.lupaukset
  "Palvelu välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.id :refer [id-olemassa?]]
            [harja.kyselyt
             [lupaukset :as lupaukset-q]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]))


(defn- sitoutumistiedot [lupausrivit]
  {:pisteet (:sitoutuminen-pisteet (first lupausrivit))
   :id (:sitoutuminen-id (first lupausrivit))})

(defn- numero->kirjain [numero]
  (case numero
    1 "A"
    2 "B"
    3 "C"
    4 "D"
    5 "E"
    nil))

(defn- lupausryhman-tiedot [lupausrivit]
  (let [ryhmat (map first (vals (group-by :lupausryhma-id lupausrivit)))]
    (->> ryhmat
         (map #(select-keys % [:lupausryhma-id :lupausryhma-otsikko
                               :lupausryhma-jarjestys :lupausryhma-alkuvuosi]))
         (map #(set/rename-keys % {:lupausryhma-id :id
                                   :lupausryhma-otsikko :otsikko
                                   :lupausryhma-jarjestys :jarjestys
                                   :lupausryhma-alkuvuosi :alkuvuosi}))
         (map #(assoc % :kirjain (numero->kirjain (:jarjestys %)))))))

(defn- hae-urakan-lupaustiedot [db user tiedot]
  (println "hae-urakan-lupaustiedot " tiedot)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-valitavoitteet user (:urakka-id tiedot))
  (let [vastaus (into []
                      (map #(update % :kirjaus-kkt konv/pgarray->vector))
                      (lupaukset-q/hae-urakan-lupaustiedot db {:urakka (:urakka-id tiedot)
                                                               :alkuvuosi (:urakan-alkuvuosi tiedot)}))]
    {:lupaus-sitoutuminen (sitoutumistiedot vastaus)
     :lupausryhmat (lupausryhman-tiedot vastaus)
     :lupaukset vastaus}))

(defn vaadi-lupaus-kuuluu-urakkaan
  "Tarkistaa, että lupaus kuuluu annettuun urakkaan"
  [db urakka-id lupaus-id]
  (when (id-olemassa? lupaus-id)
    (let [lupauksen-urakka (:urakka-id (first (lupaukset-q/hae-lupauksen-urakkatieto db {:id lupaus-id})))]
      (when-not (= lupauksen-urakka urakka-id)
        (throw (SecurityException. (str "Lupaus " lupaus-id " ei kuulu valittuun urakkaan "
                                        urakka-id " vaan urakkaan " lupauksen-urakka)))))))

(defn- tallenna-urakan-luvatut-pisteet
  [db user tiedot]
  (println "tallenna-urakan-luvatut-pisteet tiedot " tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user (:urakka-id tiedot))
  (vaadi-lupaus-kuuluu-urakkaan db (:urakka-id tiedot) (:id tiedot))
  (jdbc/with-db-transaction [db db]
    (let [params {:id (:id tiedot)
                  :urakkaid (:urakka-id tiedot)
                  :pisteet (:pisteet tiedot)
                  :kayttaja (:id user)}
          vastaus (if (:id tiedot)
                    (lupaukset-q/paivita-urakan-luvatut-pisteet<! db params)
                    (lupaukset-q/lisaa-urakan-luvatut-pisteet<! db params))]
      (hae-urakan-lupaustiedot db user tiedot))))

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