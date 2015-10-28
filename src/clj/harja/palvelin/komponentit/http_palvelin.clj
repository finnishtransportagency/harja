(ns harja.palvelin.komponentit.http-palvelin
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as http]
            [compojure.core  :as compojure]
            [compojure.route :as route]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            
            [ring.middleware.cookies :as cookies]
            [ring.middleware.params :refer [wrap-params]]

            [cognitect.transit :as t]
            [schema.core :as s]
            ;; Pyyntöjen todennus (autentikointi)
            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.palvelin.index :as index]
            [harja.geo :as geo]
            [harja.transit :as transit]
            [harja.domain.roolit]
            
            [slingshot.slingshot :refer [try+ throw+]])
  (:import (java.text SimpleDateFormat)))


(defn- reitita
  "Reititä sisääntuleva pyyntö käsittelijöille."
  [req kasittelijat]
  (apply compojure/routing
         (if (= "/" (:uri req))
           (assoc req :uri "/index.html")
           req) kasittelijat))

(defn- transit-palvelun-polku [nimi]
  (str "/_/" (name nimi)))

(defn ring-kasittelija [nimi kasittelija-fn]
  (let [polku (transit-palvelun-polku nimi)]
    (fn [req]
      (when (= polku (:uri req))
        (kasittelija-fn req)))))

(defn transit-vastaus [req data]
  {:status 200
   :headers {"Content-Type" "application/transit+json"} 
   :body (transit/clj->transit data)})

(defn- transit-post-kasittelija
  "Luo transit käsittelijän POST kutsuille annettuun palvelufunktioon."
  [nimi palvelu-fn optiot]
  (let [polku (transit-palvelun-polku nimi)]
    (fn [req]
      (when (and (= :post (:request-method req))
                 (= polku (:uri req)))
        (let [skeema (:skeema optiot)
              kysely (transit/lue-transit (:body req))
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
            (let [vastaus (try+
                           (palvelu-fn (:kayttaja req) kysely)
                           (catch harja.domain.roolit.EiOikeutta eo
                             ;; Valutetaan oikeustarkistuksen epäonnistuminen frontille asti
                             eo)
                           (catch Exception e
                             (log/warn e "Virhe POST palvelussa " nimi)
                             {:virhe (.getMessage e)}))]
              (transit-vastaus req vastaus))))))))

(def muokkaus-pvm-muoto "EEE, dd MMM yyyy HH:mm:ss zzz")

(defn- transit-get-kasittelija
  "Luo transit käsittelijän GET kutsuille annettuun palvelufunktioon."
  [nimi palvelu-fn optiot]
  (let [polku (transit-palvelun-polku nimi)]
    (fn [req]
      (when (and (= :get (:request-method req))
                 (= polku (:uri req)))
        (let [last-modified-fn (:last-modified optiot)
              last-modified (and last-modified-fn (last-modified-fn (:kayttaja req)))
              if-modified-since-header (some-> req :headers (get "if-modified-since"))
              if-modified-since (when if-modified-since-header
                                  (.parse (SimpleDateFormat. muokkaus-pvm-muoto) if-modified-since-header))]

          (if (and last-modified
                   if-modified-since
                   (not (.after last-modified if-modified-since)))
            {:status 304}
            (let [vastaus (palvelu-fn (:kayttaja req))]
              {:status 200
               :headers (merge {"Content-Type" "application/transit+json"}
                               (if last-modified
                                 {"cache-control" "private, max-age=0, must-revalidate"
                                  "Last-Modified" (.format (SimpleDateFormat. muokkaus-pvm-muoto) last-modified)}
                                 {"cache-control" "no-cache"}))
               :body (with-open [out (java.io.ByteArrayOutputStream.)]
                       (t/write (t/writer out :json) vastaus)
                       (java.io.ByteArrayInputStream. (.toByteArray out)))})))))))

(defprotocol HttpPalvelut
  "Protokolla HTTP palveluiden julkaisemiseksi."

  (julkaise-palvelu
    [this nimi palvelu-fn]
    [this nimi palvelu-fn optiot]
    "Julkaise uusi palvelu HTTP palvelimeen. Nimi on keyword, ja palvelu-fn on funktio joka ottaa
sisään käyttäjätiedot sekä sisään tulevan datan (POST body transit muodossa parsittu) ja palauttaa Clojure 
tietorakenteen, joka muunnetaan transit muotoon asiakkaalle lähetettäväksi. 
Jos funktio tukee yhden parametrin aritya, voidaan sitä kutsua myös GET metodilla. Palvelu julkaistaan
  polkuun /edn/nimi (ilman keywordin kaksoispistettä).

Valinnainen optiot parametri on mäppi, joka voi sisältää seuraavat keywordit:

  :last-modified    fn (user -> date), palauttaa viimeisen muokkauspäivän käyttäjälle, jolla GET pyynnölle
                    voidaan tarkistaa onko muutoksia. Jos tätä ei anneta, ei selaimen cachetusta sallita.

  :ring-kasittelija?  Jos true, ei transit käsittelyä tehdä vaan anneta Ring request mäp suoraan palvelulle.
                      Palvelun tulee palauttaa Ring response mäppi.

  :tarkista-polku?    Ring käsittelijän julkaisussa voidaan antaa :tarkista-polku? false, jolloin käsittelijää
                      ei sidota normaaliin palvelupolkuun keyword nimen perusteella. Tässä tapauksessa 
                      käsittelijän vastuulla on tarkistaa itse polku. Käytetään compojure reittien julkaisuun.")

  (poista-palvelu [this nimi]
    "Poistaa nimetyn palvelun käsittelijän."))

(defn- arityt
  "Palauttaa funktion eri arityt. Esim. #{0 1} jos funktio tukee nollan ja yhden parametrin arityjä."
  [f]
  (->> f class .getDeclaredMethods
       (map #(-> % .getParameterTypes alength))
       (into #{})))

(defn index-kasittelija [kehitysmoodi req]
  (let [uri (:uri req)
        token (index/token-requestista req)]
    (when (or (= uri "/")
              (= uri "/index.html"))
      {:status 200
       :headers {"Content-Type" "text/html"}
       :cookies {"anti-csrf-token" {:value token
                                    :http-only true
                                    :max-age 36000000}}
       :body (index/tee-paasivu token kehitysmoodi)})))

(defn wrap-anti-forgery
  "Vertaa headerissa lähetettyä tokenia http-only cookiessa tulevaan"
  [f]
  (fn [req]
    (let [cookies (:cookies req)
          headers (:headers req)]
      (if (and (not (nil? (headers "x-csrf-token")))
               (= (headers "x-csrf-token")
                  (:value (cookies "anti-csrf-token"))))
        (f req)
        {:status 403
         :headers {"Content-Type" "text/html"}
         :body "Access denied"}))))

(defrecord HttpPalvelin [asetukset kasittelijat sessiottomat-kasittelijat lopetus-fn kehitysmoodi]
  component/Lifecycle
  (start [this]
    (log/info "HttpPalvelin käynnistetään portissa " (:portti asetukset))
    (let [todennus (:todennus this)
          resurssit (if kehitysmoodi
                      (route/files "" {:root "dev-resources"})
                      (route/resources ""))]
      (swap! lopetus-fn
             (constantly
              (http/run-server (cookies/wrap-cookies
                                (fn [req]
                                  (try+
                                   (let [ui-kasittelijat (mapv :fn @kasittelijat)
                                         uikasittelija (-> (apply compojure/routes ui-kasittelijat) 
                                                           (wrap-anti-forgery))]
                                     (reitita (todennus/todenna-pyynto todennus req)
                                              (-> (mapv :fn @sessiottomat-kasittelijat)
                                                  (conj (partial index-kasittelija kehitysmoodi) resurssit)
                                                  (conj uikasittelija))))
                                   (catch [:virhe :todennusvirhe] _
                                     {:status 403 :body "Todennusvirhe"}))))
                               
                               {:port (or (:portti asetukset) asetukset)
                                :thread (or (:threads asetukset) 8)
                                :max-body (or (:max-body-size asetukset) (* 1024 1024 8))})))
      this))
  (stop [this]
    (log/info "HttpPalvelin suljetaan")
    (@lopetus-fn :timeout 100)
    this)


  HttpPalvelut
  (julkaise-palvelu [http-palvelin nimi palvelu-fn] (julkaise-palvelu http-palvelin nimi palvelu-fn nil))
  (julkaise-palvelu [http-palvelin nimi palvelu-fn optiot]
    (if (:ring-kasittelija? optiot)
      (swap! sessiottomat-kasittelijat conj {:nimi nimi
                                             :fn (if (= false (:tarkista-polku? optiot))
                                                   palvelu-fn
                                                   (ring-kasittelija nimi palvelu-fn))})
      (let [ar (arityt palvelu-fn)
            liikaa-parametreja (some #(when (or (= 0 %) (> % 2)) %) ar)]
        (when liikaa-parametreja
          (log/fatal "Palvelufunktiolla on oltava 1 parametri (GET: user) tai 2 parametria (POST: user payload), oli: " liikaa-parametreja))
        (when (ar 2)
          ;; POST metodi, kutsutaan kutsusta parsitulla EDN objektilla
          (swap! kasittelijat
                 conj {:nimi nimi :fn (transit-post-kasittelija nimi palvelu-fn optiot)}))
        (when (ar 1)
          ;; GET metodi, vain käyttäjätiedot parametrina
          (swap! kasittelijat
                 conj {:nimi nimi :fn (transit-get-kasittelija nimi palvelu-fn optiot)})))))

  (poista-palvelu [this nimi]
    (swap! kasittelijat
           (fn [kasittelijat]
             (filterv #(not= (:nimi %) nimi) kasittelijat)))))

(defn luo-http-palvelin [asetukset kehitysmoodi]
  (->HttpPalvelin asetukset (atom []) (atom []) (atom nil) kehitysmoodi))

(defn julkaise-reitti [http nimi reitti]
  (julkaise-palvelu http nimi  (wrap-params reitti)
                    {:ring-kasittelija? true
                     :tarkista-polku? false}))


(defn julkaise-palvelut [http & palveluiden-nimet-ja-funktiot]
  (doseq [[nimi funktio] (partition 2 palveluiden-nimet-ja-funktiot)]
    (julkaise-palvelu http nimi funktio)))

(defn poista-palvelut [http & palvelut]
  (doseq [p palvelut]
    (poista-palvelu http p)))

