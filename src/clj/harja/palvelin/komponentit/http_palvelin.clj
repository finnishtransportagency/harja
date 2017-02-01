(ns harja.palvelin.komponentit.http-palvelin
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as http]
            [compojure.core :as compojure]
            [compojure.route :as route]
            [clojure.string :as str]
            [taoensso.timbre :as log]

            [ring.middleware.cookies :as cookies]
            [ring.middleware.params :refer [wrap-params]]

            [cognitect.transit :as t]
            [schema.core :as s]
            ;; Pyyntöjen todennus (autentikointi)
            [harja.palvelin.komponentit.todennus :as todennus]
            ;; Metriikkadatan julkaisu
            [harja.palvelin.komponentit.metriikka :as metriikka]
            [harja.palvelin.index :as index]
            [harja.geo :as geo]
            [harja.transit :as transit]
            [harja.domain.roolit]

            [slingshot.slingshot :refer [try+ throw+]]

            [new-reliquary.core :as nr]
            [clojure.core.async :as async])
  (:import (java.text SimpleDateFormat)
           (java.io ByteArrayInputStream ByteArrayOutputStream)))

(defn transit-vastaus
  ([data] (transit-vastaus 200 data))
  ([status data]
   {:status  status
    :headers {"Content-Type" "application/transit+json"}
    :body    (transit/clj->transit data)}))

(defrecord AsyncResponse [channel])

(defn async-response? [res]
  (instance? AsyncResponse res))

(defmacro async [& body]
  `(->AsyncResponse (async/thread
                      (try+
                       (transit-vastaus ~@body)
                       (catch harja.domain.roolit.EiOikeutta eo#
                         (transit-vastaus 403 eo#))
                       (catch Throwable e#
                         (log/warn e# "Virhe async POST palvelussa")
                         (transit-vastaus 500 {:virhe (.getMessage e#)}))))))

(defn- reitita
  "Reititä sisääntuleva pyyntö käsittelijöille."
  [req kasittelijat]
  (apply compojure/routing
         (if
           (= "/" (:uri req))
           (assoc req :uri "/index.html")
           req)
         (remove nil? kasittelijat)))

(defn- transit-palvelun-polku [nimi]
  (str "/_/" (name nimi)))

(defn ring-kasittelija [nimi kasittelija-fn]
  (let [polku (transit-palvelun-polku nimi)]
    (fn [req]
      (when (= polku (:uri req))
        (kasittelija-fn req)))))

(defn- transit-post-kasittelija
  "Luo transit käsittelijän POST kutsuille annettuun palvelufunktioon."
  [nimi palvelu-fn optiot]
  (let [polku (transit-palvelun-polku nimi)]
    (fn [req]
      (when (and (= :post (:request-method req))
                 (= polku (:uri req)))
        (let [skeema (:skeema optiot)
              kysely (try (transit/lue-transit (:body req))
                          (catch Exception e
                            (log/warn (.getMessage e))
                            ::ei-validi-kysely))
              kysely (if-not skeema
                       kysely
                       (try
                         (if (= kysely ::ei-validi-kysely)
                           kysely
                           (s/validate skeema kysely))
                         (catch Exception e
                           (log/warn e "Palvelukutsu " nimi " ei-validilla datalla.")
                           ::ei-validi-kysely)))]
          (if (= kysely ::ei-validi-kysely)
            {:status 400
             :body   "Ei validi kysely"}
            (try+
             (let [palvelu-vastaus (palvelu-fn (:kayttaja req) kysely)]
               (if (async-response? palvelu-vastaus)
                 (http/with-channel req channel
                   (async/go
                     (let [vastaus (async/<! (:channel palvelu-vastaus))]
                       (http/send! channel vastaus)
                       (http/close channel))))
                 (transit-vastaus palvelu-vastaus)))
             (catch harja.domain.roolit.EiOikeutta eo
               ;; Valutetaan oikeustarkistuksen epäonnistuminen frontille asti
               (transit-vastaus 403 eo))
             (catch Throwable e
               (log/warn e "Virhe POST palvelussa " nimi ", payload: " (pr-str kysely))
               {:virhe (.getMessage e)}))))))))

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
              {:status  200
               :headers (merge {"Content-Type" "application/transit+json"}
                               (if last-modified
                                 {"cache-control" "private, max-age=0, must-revalidate"
                                  "Last-Modified" (.format (SimpleDateFormat. muokkaus-pvm-muoto) last-modified)}
                                 {"cache-control" "no-cache"}))
               :body    (with-open [out (ByteArrayOutputStream.)]
                          (t/write (t/writer out :json) vastaus)
                          (ByteArrayInputStream. (.toByteArray out)))})))))))

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

     :last-modified       fn (user -> date), palauttaa viimeisen muokkauspäivän käyttäjälle, jolla GET pyynnölle
                          voidaan tarkistaa onko muutoksia. Jos tätä ei anneta, ei selaimen cachetusta sallita.

     :ring-kasittelija?   Jos true, ei transit käsittelyä tehdä vaan anneta Ring request mäp suoraan palvelulle.
                          Palvelun tulee palauttaa Ring response mäppi.

     :tarkista-polku?     Ring käsittelijän julkaisussa voidaan antaa :tarkista-polku? false, jolloin käsittelijää
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
        token (index/tee-random-avain)
        salattu (index/laske-mac token)]
    (when (or (= uri "/")
              (= uri "/index.html"))
      {:status  200
       :headers {"Content-Type"  "text/html"
                 "Cache-Control" "no-cache, no-store, must-revalidate"
                 "Pragma"        "no-cache"
                 "Expires"       "0"}
       :cookies {"anti-csrf-token" {:value     salattu
                                    :http-only true
                                    :max-age   36000000}}
       :body    (index/tee-paasivu token kehitysmoodi)})))

(defn ls-index-kasittelija [kehitysmoodi req]
  (let [uri (:uri req)
        ;; Tuotantoympäristössä URI tulee aina ilman "/harja" osaa
        oikea-kohde "/harja/laadunseuranta/"]
    (cond
      (= uri "/laadunseuranta")
      {:status 301
       :headers {"Location" oikea-kohde}}

      (= uri "/laadunseuranta/index.html")
      {:status 301
       :headers {"Location" oikea-kohde}}

      (= uri "/laadunseuranta/")
      {:status  200
       :headers {"Content-Type"  "text/html"
                 "Cache-Control" "no-cache, no-store, must-revalidate"
                 "Pragma"        "no-cache"
                 "Expires"       "0"}
       :body    (index/tee-ls-paasivu kehitysmoodi)}
      :default
       nil)))

(defn wrap-anti-forgery
  "Vertaa headerissa lähetettyä tokenia http-only cookiessa tulevaan"
  [f anti-csrf-kaytossa?]
  (fn [{:keys [cookies headers uri] :as req}]
    (if (or (not (anti-csrf-kaytossa? req))
             (and (not (nil? (headers "x-csrf-token")))
                  (= (index/laske-mac (headers "x-csrf-token"))
                     (:value (cookies "anti-csrf-token")))))
      (f req)
      {:status  403
       :headers {"Content-Type" "text/html"}
       :body    "Access denied"})))

(defn- jaa-todennettaviin-ja-ei-todennettaviin [kasittelijat]
  (let [{ei-todennettavat true
         todennettavat false} (group-by #(or (:ei-todennettava %) false) kasittelijat)]
    [todennettavat ei-todennettavat]))

(defn- anti-csrf-kaytossa? [req]
  ;; ls- sisältävät palvelukutsut (mobiili laadunseuranta), ei käytä anti csrf tokenia
  (not (or (str/includes? (:uri req) "ls-")
           (str/includes? (:uri req) "laadunseuranta"))))

(defrecord HttpPalvelin [asetukset kasittelijat sessiottomat-kasittelijat lopetus-fn kehitysmoodi
                         mittarit]
  component/Lifecycle
  (start [{metriikka :metriikka :as this}]
    (log/info "HttpPalvelin käynnistetään portissa " (:portti asetukset))
    (when metriikka
      (metriikka/lisaa-mittari! metriikka "http" mittarit))
    (let [todennus (:todennus this)
          resurssit (route/resources "")
          dev-resurssit (when kehitysmoodi
                          (route/files "" {:root "dev-resources"}))]
      (swap! lopetus-fn
             (constantly
               (http/run-server
                 (cookies/wrap-cookies
                  (fn [req]
                    (try+
                     (metriikka/inc! mittarit :aktiiviset_pyynnot)
                     (let [[todennettavat ei-todennettavat] (jaa-todennettaviin-ja-ei-todennettaviin @sessiottomat-kasittelijat)
                           ui-kasittelijat (mapv :fn @kasittelijat)
                           ui-kasittelija (-> (apply compojure/routes ui-kasittelijat)
                                              (wrap-anti-forgery anti-csrf-kaytossa?))]

                       (or (reitita req (conj (mapv :fn ei-todennettavat)
                                              dev-resurssit resurssit))
                           (reitita (todennus/todenna-pyynto todennus req)
                                    (-> (mapv :fn todennettavat)
                                        (conj (partial index-kasittelija kehitysmoodi))
                                        (conj (partial ls-index-kasittelija kehitysmoodi))
                                        (conj ui-kasittelija)))))
                     (catch [:virhe :todennusvirhe] _
                       {:status 403 :body "Todennusvirhe"})
                     (finally
                       (metriikka/muuta! mittarit
                                         :aktiiviset_pyynnot dec
                                         :pyyntoja_palveltu inc)))))

                 {:port     (or (:portti asetukset) asetukset)
                  :thread   (or (:threads asetukset) 8)
                  :max-body (or (:max-body-size asetukset) (* 1024 1024 8))
                  :max-line 40960})))
      this))
  (stop [this]
    (log/info "HttpPalvelin suljetaan")
    (@lopetus-fn :timeout 100)
    this)

  HttpPalvelut
  (julkaise-palvelu [http-palvelin nimi palvelu-fn] (julkaise-palvelu http-palvelin nimi palvelu-fn nil))
  (julkaise-palvelu [http-palvelin nimi palvelu-fn optiot]
    (let [ar (arityt palvelu-fn)
          transaktio-fn (if (get optiot :trace true)
                          (fn [& args]
                            (nr/with-newrelic-transaction
                              (or (:kategoria optiot) "Backend palvelut")
                              (str nimi)
                              {}
                              #(apply palvelu-fn args)))
                          palvelu-fn)]
      (if (:ring-kasittelija? optiot)
        (swap! sessiottomat-kasittelijat conj {:nimi nimi
                                               :fn   (if (= false (:tarkista-polku? optiot))
                                                       transaktio-fn
                                                       (ring-kasittelija nimi transaktio-fn))
                                               :ei-todennettava (:ei-todennettava optiot)})
        (do
          (when-let [liikaa-parametreja (some #(when (or (= 0 %) (> % 2)) %) ar)]
            (log/fatal "Palvelufunktiolla on oltava 1 parametri (GET: user) tai 2 parametria (POST: user payload), oli: "
                       liikaa-parametreja
                       ", palvelufunktio: " palvelu-fn))
          (when (ar 2)
            ;; POST metodi, kutsutaan kutsusta parsitulla EDN objektilla
            (swap! kasittelijat
                   conj {:nimi nimi :fn (transit-post-kasittelija nimi transaktio-fn optiot)}))
          (when (ar 1)
            ;; GET metodi, vain käyttäjätiedot parametrina
            (swap! kasittelijat
                   conj {:nimi nimi :fn (transit-get-kasittelija nimi transaktio-fn optiot)}))))))

  (poista-palvelu [this nimi]
    (swap! kasittelijat
           (fn [kasittelijat]
             (filterv #(not= (:nimi %) nimi) kasittelijat)))))

(defn luo-http-palvelin [asetukset kehitysmoodi]
  (->HttpPalvelin asetukset (atom []) (atom []) (atom nil) kehitysmoodi
                  (metriikka/luo-mittari-ref {:aktiiviset_pyynnot 0
                                              :pyyntoja_palveltu 0})))

(defn julkaise-reitti
  ([http nimi reitti] (julkaise-reitti http nimi reitti true))
  ([http nimi reitti ei-todennettava?]
   (julkaise-palvelu http nimi (wrap-params reitti)
                     {:ring-kasittelija? true
                      :tarkista-polku?   false
                      :ei-todennettava   ei-todennettava?})))

(defn julkaise-palvelut [http & nimet-ja-palvelut]
  (doseq [[nimi palvelu-fn] (partition 2 nimet-ja-palvelut)]
    (julkaise-palvelu http nimi palvelu-fn)))

(defn poista-palvelut [http & palvelut]
  (doseq [p palvelut]
    (poista-palvelu http p)))
