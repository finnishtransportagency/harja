(ns harja.palvelin.komponentit.http-palvelin
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as http]
            [compojure.core :refer [make-route defroutes] :as compojure]
            [compojure.route :as route]
            [clojure.string :as str]
            [clojure.tools.logging :as log]

            ;; Pyyntöjen todennus (autentikointi)
            [harja.palvelin.komponentit.todennus :as todennus]))


(defn- reitita [req kasittelijat]
  "Reititä sisääntuleva pyyntö käsittelijöille."
  (log/debug "REQ ON: " (pr-str req))
  (log/debug "kasittelijat: " kasittelijat)
  (apply compojure/routing req kasittelijat))

(defn- edn-palvelun-polku [nimi]
  (str "/edn/" (name nimi)))

(defn- edn-post-kasittelija
  "Luo EDN käsittelijän POST kutsuille annettuun palvelufunktioon."
  [nimi palvelu-fn]
  (let [polku (edn-palvelun-polku nimi)]
    (fn [req]
      (when (and (= :post (:request-method req))
                 (= polku (:uri req)))
        (let [kysely (read-string (:body req))
              vastaus (palvelu-fn (:kayttaja req) kysely)]
          {:status 200
           :headers {"Content-Type" "application/edn"}
           :body (pr-str vastaus)})))))

(defn- edn-get-kasittelija
  "Luo EDN käsittelijän GET kutsuille annettuun palvelufunktioon."
  [nimi palvelu-fn]
  (let [polku (edn-palvelun-polku nimi)]
    (fn [req]
      (when (and (= :get (:request-method req))
                 (= polku (:uri req)))
        {:status 200
         :headers {"Content-Type" "text/plain"} ;"application/edn"}
         :body (pr-str (palvelu-fn (:kayttaja req)))}))))

(defrecord HttpPalvelin [portti kasittelijat lopetus-fn]
  component/Lifecycle
  (start [this]
    (log/info "HttpPalvelin käynnistetään portissa " portti)
    (let [todennus (:todennus this)]
      (swap! lopetus-fn
             (constantly
              (http/run-server (fn [req]
                                 (reitita (todennus/todenna-pyynto todennus req)
                                          (map :fn @kasittelijat)))
                               {:port portti})))
      this))
  (stop [this]
    (log/info "HttpPalvelin suljetaan")
    (@lopetus-fn :timeout 100)
    this))

(defn luo-http-palvelin [portti]
  (->HttpPalvelin portti (atom []) (atom nil)))

(defn- arityt 
  "Palauttaa funktion eri arityt. Esim. #{0 1} jos funktio tukee nollan ja yhden parametrin arityjä."
  [f]
  (->> f class .getDeclaredMethods
       (map #(-> % .getParameterTypes alength))
       (into #{})))

(defn julkaise-edn-palvelu 
  "Julkaise uusi EDN palvelu HTTP palvelimeen. Nimi on keyword, ja palvelu-fn on funktio joka ottaa
sisään käyttäjätiedot sekä sisään tulevan datan (POST body EDN muodossa parsittu) ja palauttaa Clojure 
tietorakenteen, joka muunnetaan EDN muotoon asiakkaalle lähetettäväksi. 
Jos funktio tukee yhden parametrin aritya, voidaan sitä kutsua myös GET metodilla. Palvelu julkaistaan
polkuun /edn/nimi (ilman keywordin kaksoispistettä)."
  [http-palvelin nimi palvelu-fn]
  (let [ar (arityt palvelu-fn)]
    (when (ar 2)
      ;; POST metodi, kutsutaan kutsusta parsitulla EDN objektilla
      (swap! (:kasittelijat http-palvelin)
             conj {:nimi nimi :fn (edn-post-kasittelija nimi palvelu-fn)}))
    (when (ar 1)
      ;; GET metodi, ei parametrejä
      (swap! (:kasittelijat http-palvelin)
             conj {:nimi nimi :fn (edn-get-kasittelija nimi palvelu-fn)}))))

(defn poista-edn-palvelu [http-palvelin nimi]
  (swap! (:kasittelijat http-palvelin)
         (fn [kasittelijat]
           (filterv #(not= (:nimi %) nimi) kasittelijat))))
