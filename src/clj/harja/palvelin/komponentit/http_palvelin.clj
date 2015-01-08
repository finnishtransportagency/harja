(ns harja.palvelin.komponentit.http-palvelin
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as http]
            [compojure.core  :as compojure]
            [compojure.route :as route]
            [clojure.string :as str]
            [clojure.tools.logging :as log]

            [cognitect.transit :as t]
            [schema.core :as s]
            ;; Pyyntöjen todennus (autentikointi)
            [harja.palvelin.komponentit.todennus :as todennus]))


(defn- reitita [req kasittelijat]
  "Reititä sisääntuleva pyyntö käsittelijöille."
  (log/debug "REQ " (:uri req))
  ;(log/debug "kasittelijat: " kasittelijat)
  (apply compojure/routing
         (if (= "/" (:uri req))
           (assoc req :uri "/index.html")
           req) kasittelijat))

(defn- transit-palvelun-polku [nimi]
  (str "/_/" (name nimi)))

(defn- transit-post-kasittelija
  "Luo transit käsittelijän POST kutsuille annettuun palvelufunktioon."
  [nimi palvelu-fn skeema]
  (let [polku (transit-palvelun-polku nimi)]
    (fn [req]
      (when (and (= :post (:request-method req))
                 (= polku (:uri req)))
        (let [kysely (t/read (t/reader (:body req) :json))
              kysely (if-not skeema
                       kysely
                       (try
                         (s/validate skeema kysely)
                         (catch Exception e
                           (log/warn e "Palvelukutsu " nimi " ei-validilla datalla.")
                           ::ei-validi-kysely)))]
          (if (= kysely ::ei-validi-kysely)
            {:status 400
             :body "Ei validi kysely"}
            
            (let [vastaus (palvelu-fn (:kayttaja req) kysely)]
              {:status 200
               :headers {"Content-Type" "application/transit+json"}
               :body (with-open [out (java.io.ByteArrayOutputStream.)]
                       (t/write (t/writer out :json) vastaus)
                       (java.io.ByteArrayInputStream. (.toByteArray out)))})))))))

(defn- transit-get-kasittelija
  "Luo transit käsittelijän GET kutsuille annettuun palvelufunktioon."
  [nimi palvelu-fn]
  (let [polku (transit-palvelun-polku nimi)]
    (fn [req]
      (when (and (= :get (:request-method req))
                 (= polku (:uri req)))
        {:status 200
         :headers {"Content-Type" "application/transit+json"}
         :body (t/write (palvelu-fn (:kayttaja req)))}))))


(defrecord HttpPalvelin [portti kasittelijat lopetus-fn kehitysmoodi]
  component/Lifecycle
  (start [this]
    (log/info "HttpPalvelin käynnistetään portissa " portti)
    (let [todennus (:todennus this)
          resurssit (if kehitysmoodi
                      (route/files "" {:root "dev-resources"})
                      ;;(let [files-route (route/files "" {:root "dev-resources"})]
                      ;;  (fn [req]
                      ;;    (let [resp (files-route req)]
                      ;;      (if (= 200 (:status resp))
                      ;;        (update-in resp :headers 
                      (route/resources ""))]
      (swap! lopetus-fn
             (constantly
              (http/run-server (fn [req]
                                 (reitita (todennus/todenna-pyynto todennus req)
                                          (conj (mapv :fn @kasittelijat)
                                                resurssit)))
                               {:port portti})))
      this))
  (stop [this]
    (log/info "HttpPalvelin suljetaan")
    (@lopetus-fn :timeout 100)
    this))

(defn luo-http-palvelin [portti kehitysmoodi]
  (->HttpPalvelin portti (atom []) (atom nil) kehitysmoodi))

(defn- arityt 
  "Palauttaa funktion eri arityt. Esim. #{0 1} jos funktio tukee nollan ja yhden parametrin arityjä."
  [f]
  (->> f class .getDeclaredMethods
       (map #(-> % .getParameterTypes alength))
       (into #{})))

(defn julkaise-palvelu 
  "Julkaise uusi palvelu HTTP palvelimeen. Nimi on keyword, ja palvelu-fn on funktio joka ottaa
sisään käyttäjätiedot sekä sisään tulevan datan (POST body transit muodossa parsittu) ja palauttaa Clojure 
tietorakenteen, joka muunnetaan transit muotoon asiakkaalle lähetettäväksi. 
Jos funktio tukee yhden parametrin aritya, voidaan sitä kutsua myös GET metodilla. Palvelu julkaistaan
  polkuun /edn/nimi (ilman keywordin kaksoispistettä)."
  ([http-palvelin nimi palvelu-fn] (julkaise-palvelu http-palvelin nimi palvelu-fn nil))
  ([http-palvelin nimi palvelu-fn skeema]
     (let [ar (arityt palvelu-fn)]
       (when (ar 2)
         ;; POST metodi, kutsutaan kutsusta parsitulla EDN objektilla
         (swap! (:kasittelijat http-palvelin)
                conj {:nimi nimi :fn (transit-post-kasittelija nimi palvelu-fn skeema)}))
       (when (ar 1)
         ;; GET metodi, vain käyttäjätiedot parametrina
         (swap! (:kasittelijat http-palvelin)
                conj {:nimi nimi :fn (transit-get-kasittelija nimi palvelu-fn)})))))

(defn poista-palvelu [http-palvelin nimi]
  (swap! (:kasittelijat http-palvelin)
         (fn [kasittelijat]
           (filterv #(not= (:nimi %) nimi) kasittelijat))))

