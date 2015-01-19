(ns harja.palvelin.palvelut.toimenpidekoodit
   (:require [com.stuartsierra.component :as component]
             [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
             [harja.skeema :as skeema]

             [harja.kyselyt.toimenpidekoodit :refer [hae-kaikki-toimenpidekoodit] :as q]))

(declare hae-toimenpidekoodit
         lisaa-toimenpidekoodi
         poista-toimenpidekoodi)

(defrecord Toimenpidekoodit []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this) :hae-toimenpidekoodit
                      (fn [kayttaja _]
                        (hae-toimenpidekoodit (:db this) kayttaja)))
    (julkaise-palvelu (:http-palvelin this) :lisaa-toimenpidekoodi
                      (fn [kayttaja koodi]
                        (lisaa-toimenpidekoodi (:db this) kayttaja koodi)))
    (julkaise-palvelu (:http-palvelin this) :poista-toimenpidekoodi
                      (fn [kayttaja koodi]
                        (poista-toimenpidekoodi (:db this) kayttaja koodi)))
    this)

  (stop [this]
    (doseq [p [:hae-toimenpidekoodit :lisaa-toimenpidekoodi :poista-toimenpidekoodi]]
      (poista-palvelu (:http-palvelin this) p))
    this))


(defn hae-toimenpidekoodit
  "Palauttaa toimenpidekoodit listana"
  [db kayttaja]
  (hae-kaikki-toimenpidekoodit db))

(defn lisaa-toimenpidekoodi
  "Lisää toimenpidekoodin, sisään tulevassa koodissa on oltava :nimi ja :emo. Emon on oltava 3. tason koodi."
  [db {kayttaja :id} {nimi :nimi emo :emo}]
  (let [luotu (q/lisaa-toimenpidekoodi<! db nimi emo kayttaja)]
    {:taso 4
     :emo emo
     :nimi nimi
     :id (:id luotu)}))
        
(defn poista-toimenpidekoodi
  "Merkitsee toimenpidekoodin poistetuksi. Palauttaa true jos koodi merkittiin poistetuksi, false muuten."
  [db {kayttaja :id} {id :id}]
  (= 1 (q/poista-toimenpidekoodi! db kayttaja id)))
