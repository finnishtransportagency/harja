(ns harja.palvelin.palvelut.toimenpidekoodit
   (:require [com.stuartsierra.component :as component]
             [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
             [harja.palvelin.oikeudet :as oikeudet]
             [harja.kyselyt.toimenpidekoodit :refer [hae-kaikki-toimenpidekoodit] :as q]
             
             [clojure.java.jdbc :as jdbc]
             [clojure.tools.logging :as log]))

(declare hae-toimenpidekoodit
         tallenna-tehtavat
         lisaa-toimenpidekoodi
         poista-toimenpidekoodi
         muokkaa-toimenpidekoodi)


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


(defn tallenna-tehtavat [db user {:keys [lisattavat muokattavat poistettavat]}]
  (oikeudet/vaadi-rooli user oikeudet/rooli:jarjestelmavastuuhenkilo)
  (jdbc/with-db-transaction [c db]
                            (doseq [rivi lisattavat]
                              (lisaa-toimenpidekoodi c user rivi)
                              )
                            (doseq [rivi muokattavat]
                              (muokkaa-toimenpidekoodi c user rivi)
                              )
                            (doseq [id poistettavat]
                              (poista-toimenpidekoodi c user id)
                              )
                            (hae-kaikki-toimenpidekoodit c)))

(defn hae-toimenpidekoodit
  "Palauttaa toimenpidekoodit listana"
  [db kayttaja]
  (hae-kaikki-toimenpidekoodit db))

(defn lisaa-toimenpidekoodi
  "Lisää toimenpidekoodin, sisään tulevassa koodissa on oltava :nimi, :emo ja :yksikko. Emon on oltava 3. tason koodi."
  ;;[db {kayttaja :id} {nimi :nimi emo :emo}]
  [db user {:keys [nimi emo yksikko] :as rivi}]
  (let [luotu (q/lisaa-toimenpidekoodi<! db nimi emo yksikko (:id user))]
    {:taso 4
     :emo emo
     :nimi nimi
     :yksikko yksikko
     :id (:id luotu)}))
        
(defn poista-toimenpidekoodi
  "Merkitsee toimenpidekoodin poistetuksi. Palauttaa true jos koodi merkittiin poistetuksi, false muuten."
  [db user id]
  (= 1 (q/poista-toimenpidekoodi! db (:id user) id)))

(defn muokkaa-toimenpidekoodi
  "Muokkaa toimenpidekoodin nimeä ja yksikköä. Palauttaa true jos muokkaus tehtiin, false muuten."
  [db user {:keys [nimi emo yksikko id] :as rivi}]
  (= 1 (q/muokkaa-toimenpidekoodi! db (:id user) nimi yksikko id)))
