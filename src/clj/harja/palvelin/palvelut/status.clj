(ns harja.palvelin.palvelut.status
  (:require [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.kyselyt.status :as status-kyselyt]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [GET]]
            [clojure.core.async :as async]
            [clojure.set :as clj-set]
            [cheshire.core :refer [encode]]
            [harja.tyokalut.muunnos :as muunnos]
            [clojure.string :as str]))

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
                    (and (> (count @komponenttien-tila) 1)
                         (every? (fn [[_ host-tila]]
                                   (get-in host-tila [:db :kaikki-ok?]))
                                 @komponenttien-tila)))))

(defn replikoinnin-tila-ok?
  [timeout-ms komponenttien-tila]
  (tarkista-tila! timeout-ms
                  (fn []
                    (and (> (count @komponenttien-tila) 1)
                         (every? (fn [[_ host-tila]]
                                   (get-in host-tila [:db-replica :kaikki-ok?]))
                                 @komponenttien-tila)))))


(defn itmf-yhteyden-tila-ok?
  [timeout-ms komponenttien-tila]
  (tarkista-tila! timeout-ms
                  (fn []
                    (and (> (count @komponenttien-tila) 1)
                         (every? (fn [[_ host-tila]]
                                   (get-in host-tila [:itmf :kaikki-ok?]))
                                 @komponenttien-tila)))))

(defn harjan-tila-ok?
  [timeout-ms komponenttien-tila]
  (tarkista-tila! timeout-ms
                  (fn []
                    (and (> (count @komponenttien-tila) 1)
                         (every? (fn [[_ host-tila]]
                                   (get-in host-tila [:harja :kaikki-ok?]))
                                 @komponenttien-tila)))))

(defn tietokannan-tila [komponenttien-tila]
  (async/go
    (let [timeout-ms 50000
          yhteys-ok? (async/<! (dbn-tila-ok? timeout-ms (get komponenttien-tila :komponenttien-tila)))]
      {:ok? yhteys-ok?
       :komponentti :db
       :viesti (when-not yhteys-ok?
                 (str "Ei saatu yhteyttä kantaan " (muunnos/ms->s timeout-ms) " sekunnin kuluessa."))})))

(defn replikoinnin-tila [komponenttien-tila]
  (async/go
    (let [timeout-ms 50000
          replikoinnin-tila-ok? (async/<! (replikoinnin-tila-ok? timeout-ms (get komponenttien-tila :komponenttien-tila)))]
      {:ok? replikoinnin-tila-ok?
       :komponentti :db-replica
       :viesti (when-not replikoinnin-tila-ok?
                 (str "Replikoinnin viive on suurempi kuin " (muunnos/ms->s timeout-ms) " sekunttia"))})))



(defn itmf-yhteyden-tila [komponenttien-tila]
  (async/go
    (if (ominaisuus-kaytossa? :itmf)
      (let [timeout-ms 120000
            yhteys-ok? (async/<! (itmf-yhteyden-tila-ok? timeout-ms (get komponenttien-tila :komponenttien-tila)))]
        {:ok? yhteys-ok?
         :komponentti :itmf
         :viesti (when-not yhteys-ok?
                   (str "Ei saatu yhteyttä ITMF:ään " (muunnos/ms->s timeout-ms) " sekunnin kuluessa."))})
      {:ok? true
       :komponentti :itmf
       :viesti "ITMF ei ole käytössä"})))

(defn harjan-tila [komponenttien-tila]
  (async/go
    (let [timeout-ms 20000
          kaikki-ok? (async/<! (harjan-tila-ok? timeout-ms (get komponenttien-tila :komponenttien-tila)))]
      {:ok? kaikki-ok?
       :komponentti :harja
       :viesti (when-not kaikki-ok?
                 (apply str (interpose "\n"
                                       (map (fn [[host host-tila]]
                                              (str "HOST: " host "\n"
                                                   "VIESTI: " (get-in host-tila [:harja :viesti])))
                                            @(get komponenttien-tila :komponenttien-tila)))))})))

(defn- nimea-komponentin-tila [komponentti ok?]
  (clj-set/rename-keys {komponentti ok?}
                       {:harja :harja-ok?
                        :itmf :itmf-yhteys-ok?
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

(defn- hae-status
  "Haetaan komponenttien status tietokannasta."
  [db]
  (let [komponenttien-tila (status-kyselyt/hae-komponenttien-tila db)
        tilaviesti (reduce (fn [viestit {:keys [status komponentti palvelin] :as tila}]
                        (let [viesti (cond
                                       (= "nok" status) (str "Palvelin: " palvelin " Komponentti: " (str/upper-case komponentti) " rikki")
                                       (= "hidas" status) (str "Palvelin: " palvelin " Komponentti: " (str/upper-case komponentti) " käy hitaalla.")
                                       (= "ei-kaytossa" status) (str "Palvelin: " palvelin " Komponentti: " (str/upper-case komponentti) " ei ole käytössä")
                                       :else nil)
                              tulos (if viesti
                                      (str viesti ", " viestit)
                                      viestit)]
                          tulos))
                "" komponenttien-tila)
        tarkista-status-fn (fn [status]
                             (or (= "ok" status) (= "ei-kaytossa" status) false))
        ;; Yksittäinen komponentti on ok, jos joltakin palvelimelta on saatu ok status
        itmf-yhteys-ok? (or (some #(tarkista-status-fn (:status %)) (filter #(= (:komponentti %) "itmf") komponenttien-tila)) false)
        replikoinnin-tila-ok? (or (some #(tarkista-status-fn (:status %)) (filter #(= (:komponentti %) "replica") komponenttien-tila)) false)
        yhteys-master-kantaan-ok? (or (some #(tarkista-status-fn (:status %)) (filter #(= (:komponentti %) "db") komponenttien-tila)) false)
        ;; Harja on ok, mikäli kaikki komponentit on ok
        harja-ok? (every? true? [itmf-yhteys-ok? replikoinnin-tila-ok? yhteys-master-kantaan-ok?])
        viesti (cond
                 (empty? tilaviesti) "Harja ok"
                 (and (not (empty? tilaviesti)) harja-ok?)  (str "Harja ok" ", " tilaviesti)
                 :else tilaviesti)]
    {:status (if harja-ok? 200 503)
     :harja-ok? harja-ok?
     :itmf-yhteys-ok? itmf-yhteys-ok?
     :replikoinnin-tila-ok? replikoinnin-tila-ok?
     :yhteys-master-kantaan-ok? yhteys-master-kantaan-ok?
     :viesti viesti}))

(defrecord Status [kehitysmoodi?]
  component/Lifecycle
  (start [{http :http-palvelin
           komponenttien-tila :komponenttien-tila
           db :db
           :as this}]
    (http-palvelin/julkaise-reitti
      http :uusi-status
      (GET "/uusi-status" _
        (let [{:keys [status] :as lahetettava-viesti} (hae-status db)]
          (do
            (when (not (= status 200))
              (log/error "Status palauttaa virheen, viesti:\n" lahetettava-viesti))
            {:status status
             :headers {"Content-Type" "application/json; charset=UTF-8"}
             :body (encode
                     (dissoc lahetettava-viesti :status))}))))
    (http-palvelin/julkaise-reitti
     http :status
     (GET "/status" _
          (let [testit (async/merge
                         [(tietokannan-tila komponenttien-tila)
                          (replikoinnin-tila komponenttien-tila)
                          (itmf-yhteyden-tila komponenttien-tila)
                          (harjan-tila komponenttien-tila)])
                {:keys [status] :as lahetettava-viesti} (koko-status testit)]
            (do
              (when (not (= status 200))
                (log/error "Status palauttaa virheen, viesti:\n" lahetettava-viesti))
              {:status status
               :headers {"Content-Type" "application/json; charset=UTF-8"}
               :body (encode
                       (dissoc lahetettava-viesti :status))}))))
    (http-palvelin/julkaise-reitti
      http :app-status
      (GET "/app_status" _
        (let [testit (async/merge
                       [(harjan-tila komponenttien-tila)])
              {:keys [status] :as lahetettava-viesti} (koko-status testit)
              ;; Lähetä "Harja ok" tyhjän viestin sijasta, mikäli harjan tila on OK.
              ;;  Jos jotakin on pielessä, niin koko-status fn kokoaa virheviestit lähetettäväksi.
              lahetettava-viesti  (if (= 200 status)
                                    (assoc lahetettava-viesti :viesti "Harja ok")
                                    lahetettava-viesti)]
          {:status status
           :headers {"Content-Type" "application/json; charset=UTF-8"}
           :body (encode
                   (select-keys lahetettava-viesti #{:viesti}))})))
    (http-palvelin/julkaise-reitti
      http :app-status-local
      (GET "/app_status_local" _
        (let [harja-ok? (async/<!! (tarkista-tila! (* 1000 10)
                                                (fn []
                                                  (-> komponenttien-tila :komponenttien-tila deref (get tapahtuma-apurit/host-nimi) :harja :kaikki-ok?))))]
          {:status (if harja-ok? 200 503)
           :headers {"Content-Type" "application/json; charset=UTF-8"}
           :body (encode
                   ;; Jos harja ei ole OK, lähetä vain tyhjä viesti.
                   {:viesti (if harja-ok? "Harja ok" "")})})))
    this)

  (stop [{http :http-palvelin :as this}]
    (http-palvelin/poista-palvelu http :uusi-status)
    (http-palvelin/poista-palvelu http :status)
    (http-palvelin/poista-palvelu http :app-status)
    (http-palvelin/poista-palvelu http :app-status-local)
    this))

(defn luo-status [kehitysmoodi?]
  (->Status kehitysmoodi?))
