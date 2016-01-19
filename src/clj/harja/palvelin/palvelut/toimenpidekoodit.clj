(ns harja.palvelin.palvelut.toimenpidekoodit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.toimenpidekoodit :refer [hae-kaikki-toimenpidekoodit] :as q]
            [harja.kyselyt.urakat :as urakat-q]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.kyselyt.konversio :as konv]))







(defn tallenna-tehtavat [db user {:keys [lisattavat muokattavat poistettavat]}]
  (roolit/vaadi-rooli user roolit/jarjestelmavastuuhenkilo)
  (jdbc/with-db-transaction [c db]
    (doseq [rivi lisattavat]
      ;; tee tässä :hinnoittelu vektorista jdbcarray
      (let [rivi rivi]
        (lisaa-toimenpidekoodi c user rivi)))
    (doseq [rivi muokattavat]
      (muokkaa-toimenpidekoodi c user rivi))
    (doseq [id poistettavat]
      (poista-toimenpidekoodi c user id))
    (hae-kaikki-toimenpidekoodit c)))

(defn hae-toimenpidekoodit
  "Palauttaa toimenpidekoodit listana"
  [db kayttaja]
  (into []
        (map #(konv/array->vec % :hinnoittelu))
        (hae-kaikki-toimenpidekoodit db)))

(defn lisaa-toimenpidekoodi
  "Lisää toimenpidekoodin, sisään tulevassa koodissa on oltava :nimi, :emo ja :yksikko. Emon on oltava 3. tason koodi."
  ;;[db {kayttaja :id} {nimi :nimi emo :emo}]
  [db user {:keys [nimi emo yksikko kokonaishintainen] :as rivi}]
  (let [luotu (q/lisaa-toimenpidekoodi<! db nimi emo yksikko kokonaishintainen (:id user))]
    {:taso              4
     :emo               emo
     :nimi              nimi
     :yksikko           yksikko
     :kokonaishintainen kokonaishintainen
     :id                (:id luotu)}))

(defn poista-toimenpidekoodi
  "Merkitsee toimenpidekoodin poistetuksi. Palauttaa true jos koodi merkittiin poistetuksi, false muuten."
  [db user id]
  (= 1 (q/poista-toimenpidekoodi! db (:id user) id)))

(defn muokkaa-toimenpidekoodi
  "Muokkaa toimenpidekoodin nimeä ja yksikköä. Palauttaa true jos muokkaus tehtiin, false muuten."
  [db user {:keys [nimi emo yksikko id kokonaishintainen] :as rivi}]
  (= 1 (q/muokkaa-toimenpidekoodi! db (:id user) nimi yksikko kokonaishintainen id)))

(defrecord Toimenpidekoodit []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu :hae-toimenpidekoodit
                        (fn [kayttaja]
                          (hae-toimenpidekoodit (:db this) kayttaja))
                        {:last-modified (fn [user]
                                          (:muokattu (first (q/viimeisin-muokkauspvm (:db this)))))})
      (julkaise-palvelu
        :tallenna-tehtavat (fn [user tiedot]
                             (tallenna-tehtavat (:db this) user tiedot))))
    this)

  (stop [this]
    (doseq [p [:hae-toimenpidekoodit :tallenna-tehtavat]]
      (poista-palvelu (:http-palvelin this) p))
    this))