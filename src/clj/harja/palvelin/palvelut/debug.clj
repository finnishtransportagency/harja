(ns harja.palvelin.palvelut.debug
  "Erinäisiä vain JVH:lle tarkoitettuja palveluita, joilla voi selvitellä
  eri tilanteita, esim. TR-osiossa."
  (:require [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :as http]
            [harja.kyselyt.debug :as q]

            [harja.kyselyt.konversio :as konv]))

(defn hae-toteuman-reitti-ja-pisteet [db toteuma-id]
  (let [tulos (konv/sarakkeet-vektoriin
               (map konv/alaviiva->rakenne
                    (q/hae-toteuman-reitti-ja-pisteet
                     db {:toteuma-id toteuma-id}))
               {:reittipiste :reittipisteet})]
    {:reitti (:reitti (first tulos))
     :reittipisteet (:reittipisteet (first tulos))}))

(defn vaadi-jvh! [user]
  (roolit/jvh? user))


(defrecord Debug []
  component/Lifecycle
  (start [{db :db
           http :http-palvelin :as this}]
    (http/julkaise-palvelut
     http
     :debug-hae-toteuman-reitti-ja-pisteet
     (fn [user toteuma-id]
       (vaadi-jvh! user)
       (hae-toteuman-reitti-ja-pisteet db toteuma-id)))
    this)

  (stop [{http :http-palvelin :as this}]
    (http/poista-palvelut
     http
     :debug-hae-toteuman-reitti-ja-pisteet)
    this))
