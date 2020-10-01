(ns harja.palvelin.palvelut.status
  (:require [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [GET]]
            [clojure.core.async :as async]
            [clojure.set :as clj-set]
            [harja.palvelin.palvelut.jarjestelman-tila :as jarjestelman-tila]
            [harja.palvelin.komponentit.komponenttien-tila :as komponentin-tila]
            [cheshire.core :refer [encode]]
            [harja.tyokalut.muunnos :as muunnos]))

(defn tarkista-tila!
  "Palauttaa kanavan, jonka sisällä testataan predikaattia. Kokeilee timeout-ms ajan, että palauttaako annettu predikaatti true. Jos ei palauta annetussa ajassa, palauttaa false."
  [timeout-ms predicate]
  {:pre [(integer? timeout-ms)
         (ifn? predicate)]}
  (async/go
    (let [testin-lopettaja (async/chan)
          testi (async/go-loop [status-ok? (predicate)
                                kasketty-lopettamaan? (async/poll! testin-lopettaja)]
                  (if (or status-ok? (not (nil? kasketty-lopettamaan?)))
                    status-ok?
                    (do (async/<! (async/timeout 1000))
                        (recur (predicate)
                               (async/poll! testin-lopettaja)))))
          [arvo portti] (async/alts! [testi
                                      (async/timeout timeout-ms)])]
      (when-not (= portti testi)
        (async/put! testin-lopettaja :lopeta))
      (boolean arvo))))

(defn dbn-tila-ok?
  [timeout-ms komponenttien-tila]
  (tarkista-tila! timeout-ms
                  (fn []
                    (get-in @komponenttien-tila [:db :kaikki-ok?]))))

(defn replikoinnin-tila-ok?
  [timeout-ms komponenttien-tila]
  (tarkista-tila! timeout-ms
                  (fn []
                    (get-in @komponenttien-tila [:db-replica :kaikki-ok?]))))

(defn sonja-yhteyden-tila-ok?
  [db kehitysmoodi? timeout-ms komponenttien-tila]
  (letfn [(taman-palvelimen-tila-ok? []
            (get-in @komponenttien-tila [:sonja :kaikki-ok?]))
          (jonku-palvelimen-tila-ok? []
            (komponentin-tila/sonjayhteydet-kannasta-ok? (jarjestelman-tila/hae-sonjan-tila db kehitysmoodi?)))]
    (tarkista-tila! timeout-ms
                    (fn []
                      (or (taman-palvelimen-tila-ok?)
                          (jonku-palvelimen-tila-ok?))))))

(defn harjan-tila-ok?
  [timeout-ms komponenttien-tila]
  (tarkista-tila! timeout-ms
                  (fn []
                    (get-in @komponenttien-tila [:harja :kaikki-ok?]))))

(defn tietokannan-tila [komponenttien-tila]
  (async/go
    (let [timeout-ms 10000
          yhteys-ok? (async/<! (dbn-tila-ok? timeout-ms (get komponenttien-tila :komponenttien-tila)))]
      {:ok? yhteys-ok?
       :komponentti :db
       :viesti (when-not yhteys-ok?
                 (str "Ei saatu yhteyttä kantaan " (muunnos/ms->s timeout-ms) " sekunnin kuluessa."))})))

(defn replikoinnin-tila [komponenttien-tila]
  (async/go
    (let [timeout-ms 100000
          replikoinnin-tila-ok? (async/<! (replikoinnin-tila-ok? timeout-ms (get komponenttien-tila :komponenttien-tila)))]
      {:ok? replikoinnin-tila-ok?
       :komponentti :db-replica
       :viesti (when-not replikoinnin-tila-ok?
                 (str "Replikoinnin viive on suurempi kuin " (muunnos/ms->s timeout-ms) " sekunttia"))})))

(defn sonja-yhteyden-tila [komponenttien-tila db kehitysmoodi?]
  (async/go
    (let [timeout-ms 1000 #_120000
          yhteys-ok? (async/<! (sonja-yhteyden-tila-ok? db kehitysmoodi? timeout-ms (get komponenttien-tila :komponenttien-tila)))]
      {:ok? yhteys-ok?
       :komponentti :sonja
       :viesti (when-not yhteys-ok?
                 (str "Ei saatu yhteyttä Sonjaan " (muunnos/ms->s timeout-ms) " sekunnin kuluessa."))})))

(defn harjan-tila [komponenttien-tila]
  (async/go
    (let [timeout-ms 10000
          kaikki-ok? (async/<! (harjan-tila-ok? timeout-ms (get komponenttien-tila :komponenttien-tila)))]

      {:ok? kaikki-ok?
       :komponentti :harja
       :viesti (when-not kaikki-ok?
                 (get-in @(get komponenttien-tila :komponenttien-tila) [:harja :viesti]))})))

(defn- nimea-komponentin-tila [komponentti ok?]
  (clj-set/rename-keys {komponentti ok?}
                       {:harja :harja-ok?
                        :sonja :sonja-yhteys-ok?
                        :db :yhteys-master-kantaan-ok?
                        :db-replica :replikoinnin-tila-ok?}))

(defn koko-status [testit]
  (let [tilanteen-koonti (async/<!! (async/reduce (fn [{edellinen-status :status
                                                        edelliset-viestit :viesti :as koottu}
                                                       {:keys [ok? komponentti viesti]}]
                                                    (merge (assoc koottu
                                                                  :status (if (and (= edellinen-status 200)
                                                                                   ok?)
                                                                            200
                                                                            503)
                                                                  :viesti (if viesti
                                                                            (conj edelliset-viestit viesti)
                                                                            edelliset-viestit))
                                                           (nimea-komponentin-tila komponentti ok?)))
                                                  {:status 200 :viesti []}
                                                  testit))]
    (update tilanteen-koonti
            :viesti
            (fn [viestit]
              (apply str (interpose "\n" viestit))))))

(defrecord Status [status kehitysmoodi?]
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           komponenttien-tila :komponenttien-tila
           :as this}]
    (http-palvelin/julkaise-reitti
     http :status
     (GET "/status" _
          (let [testit (async/merge
                         [(tietokannan-tila komponenttien-tila)
                          (replikoinnin-tila komponenttien-tila)
                          (sonja-yhteyden-tila komponenttien-tila db kehitysmoodi?)
                          (harjan-tila komponenttien-tila)])
                {:keys [status] :as lahetettava-viesti} (koko-status testit)]
            {:status status
             :headers {"Content-Type" "application/json; charset=UTF-8"}
             :body (encode
                    (dissoc lahetettava-viesti :status))})))
    (http-palvelin/julkaise-reitti
      http :app-status
      (GET "/app_status" _
        (let [testit (async/merge
                       [(harjan-tila komponenttien-tila)])
              {:keys [status] :as lahetettava-viesti} (koko-status testit)]
          {:status status
           :headers {"Content-Type" "application/json; charset=UTF-8"}
           :body (encode
                   (select-keys lahetettava-viesti #{:viesti}))})))
    this)

  (stop [{http :http-palvelin :as this}]
    (http-palvelin/poista-palvelu http :status)
    this))

(defn luo-status [kehitysmoodi?]
  (->Status (atom {:viesti "Harja käynnistyy"}) kehitysmoodi?))
