(ns harja.palvelin.palvelut.debug
  "Erinäisiä vain JVH:lle tarkoitettuja palveluita, joilla voi selvitellä
  eri tilanteita, esim. TR-osiossa."
  (:require [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :as http]
            [harja.kyselyt.debug :as q]

            [harja.kyselyt.konversio :as konv]
            [cheshire.core :as cheshire]
            [harja.palvelin.integraatiot.api.reittitoteuma :as reittitoteuma]
            [taoensso.timbre :as log]))

(defn hae-toteuman-reitti-ja-pisteet [db toteuma-id]
  (let [tulos (konv/sarakkeet-vektoriin
               (map konv/alaviiva->rakenne
                    (q/hae-toteuman-reitti-ja-pisteet
                     db {:toteuma-id toteuma-id}))
               {:reittipiste :reittipisteet})]
    {:reitti (:reitti (first tulos))
     :reittipisteet (:reittipisteet (first tulos))}))

(defn geometrisoi-reittoteuma [db json]
  (let [parsittu  (cheshire/decode json)
        reitti (or (get-in parsittu ["reittitoteuma" "reitti"])
                   (get-in parsittu ["reittitoteumat" 0 "reittitoteuma" "reitti"]))
        pisteet (mapv (fn [{{koordinaatit "koordinaatit"} "reittipiste"}]
                        [(get koordinaatit "x") (get koordinaatit "y")])
                      reitti)]
    (reittitoteuma/hae-reitti db pisteet)))

(defn geometrisoi-reittipisteet [db pisteet]
  (reittitoteuma/hae-reitti db pisteet))

(defn vaadi-jvh! [palvelu-fn]
  (fn [user payload]
    (if-not (roolit/jvh? user)
      (log/error "DEBUG näkymän palvelua yritti käyttää ei-jvh: " user)
      (palvelu-fn payload))))


(defrecord Debug []
  component/Lifecycle
  (start [{db :db
           http :http-palvelin :as this}]
    (http/julkaise-palvelut
     http
     :debug-hae-toteuman-reitti-ja-pisteet
     (vaadi-jvh! (partial #'hae-toteuman-reitti-ja-pisteet db))
     :debug-geometrisoi-reittitoteuma
     (vaadi-jvh! (partial #'geometrisoi-reittoteuma db))
     :debug-geometrisoi-reittipisteet
     (vaadi-jvh! (partial #'geometrisoi-reittipisteet db)))
    this)

  (stop [{http :http-palvelin :as this}]
    (http/poista-palvelut
     http
     :debug-hae-toteuman-reitti-ja-pisteet
     :debug-geometrisoi-reittitoteuma
     :debug-geometrisoi-reittipisteet)
    this))
