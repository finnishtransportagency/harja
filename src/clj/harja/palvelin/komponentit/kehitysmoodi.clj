(ns harja.palvelin.komponentit.kehitysmoodi
  "Komponentti, joka on ajossa VAIN paikallisen kehityksen aikana.
  Tässä voidaan käynnistää palveluita, joita halutaan kehityksen aikana
  mutta ei tuotannossa."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [org.httpkit.client :as http]
            [compojure.core :refer [GET]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [harja.tyokalut.oikeudet-edn-export :as oikeudet-export]
            [taoensso.timbre :as log]))

;; Kartat haetaan tunnelin kautta, kun käytät harja-infra repon create_map_proxy scriptiä.
(def +wmts-mml-url+ "http://localhost:9999/wmts")
(def +wmts-livi-url+ "http://localhost:9999/wmtslivi")


(def basic-auth-header (delay (str/trim-newline (slurp "../.harja/mml"))))

(def debug-last-wmts-response (atom nil))

(def +kopioitavat-headerit+
  {"Cache-Control"  :cache-control
   "Content-Length" :content-length
   "Content-Type" :content-type
   "Date" :date
   "Expires" :expires
   "Last-modified" :last-modified})

(defn- headerit [headers]
  (reduce (fn [h [header keyword]]
            (if-let [v (get headers keyword)]
              (assoc h header v)
              h))
          {}
          +kopioitavat-headerit+))

(defn- wmts-osoite [uri]
  (let [osoite (str (if (str/includes? uri "/wmts/")
                      +wmts-mml-url+
                      +wmts-livi-url+)
                    (-> uri
                        (str/replace #"/wmts/" "/")
                        (str/replace #"/wmtslivi/" "/")))]
    osoite))

(defn- hae-karttakuva [{:keys [uri query-params] :as req}]
  (let [{:keys [status body headers] :as res}
        @(http/get (wmts-osoite uri)
                   {:query-params query-params
                    :headers {"Authorization" (str "Basic " @basic-auth-header)}
                    :timeout 500 ;; tarvitaan lyhyt timeout, koska harja-test palomuuri on teergrube ja hyydyttää appin jos käytetään tavan verkosta
                    })]
    (reset! debug-last-wmts-response res)
    {:status status
     :body body
     :headers (headerit headers)}))

;; --- Oikeudet-EDN automaattinen generointi ---

(defn- tiedoston-muokkausaika
  "Palauttaa tiedoston viimeisimmän muokkausajan millisekunteina.
  Palauttaa nil jos tiedostoa ei ole olemassa."
  [polku]
  (try
    (when-let [tiedosto (io/file polku)]
      (when (.exists tiedosto)
        (.lastModified tiedosto)))
    (catch Exception _e nil)))

(defn- excel-uudempi-kuin-edn?
  "Tarkistaa onko Excel-tiedosto uudempi kuin EDN-tiedostot.
  Palauttaa true jos:
  - Excel on uudempi kuin jompikumpi EDN-tiedosto
  - Jompikumpi EDN-tiedosto puuttuu
  - Excel-tiedosto puuttuu (palauttaa false ja logittaa varoituksen)"
  []
  (let [excel-mtime (tiedoston-muokkausaika "resources/roolit.xlsx")
        roolit-mtime (tiedoston-muokkausaika "resources/roolit.edn")
        oikeudet-mtime (tiedoston-muokkausaika "resources/oikeudet.edn")]
    (cond
      (nil? excel-mtime)
      (do
        (log/warn "⚠️  resources/roolit.xlsx ei löydy!")
        false)
      
      (or (nil? roolit-mtime) (nil? oikeudet-mtime))
      (do
        (log/info "📝 EDN-tiedostoja ei ole vielä generoitu")
        true)
      
      (or (> excel-mtime roolit-mtime)
          (> excel-mtime oikeudet-mtime))
      (do
        (log/info "🔄 Excel on uudempi kuin EDN-tiedostot")
        true)
      
      :else
      (do
        (log/debug "✅ EDN-tiedostot ovat ajan tasalla")
        false))))

(defn- generoi-oikeudet-edn-jos-tarpeen
  "Generoi oikeudet-EDN:t automaattisesti jos Excel on muuttunut.
  
  Tämä on kehittäjän mukavuusominaisuus - GitHub Actions validoi
  että EDN:t ovat ajan tasalla ennen mergea.
  
  Käynnistyy vain kehitysympäristössä (kehitysmoodi-komponentissa)."
  []
  (try
    (when (excel-uudempi-kuin-edn?)
      (log/info "🔄 Generoidaan oikeudet-EDN:t (Excel on muuttunut)...")
      (let [success? (oikeudet-export/generoi-kaikki!)]
        (if success?
          (log/info "✅ Oikeudet-EDN:t generoitu onnistuneesti")
          (log/warn "⚠️  Oikeudet-EDN:ien generointi epäonnistui"))))
    (catch Exception e
      (log/error e "❌ Virhe generoitaessa oikeudet-EDN:iä käynnistyksessä")
      ;; EI failata käynnistystä, vaikka generointi epäonnistuisi
      nil)))

(defrecord Kehitysmoodi []
  component/Lifecycle
  (start [{http :http-palvelin :as this}]
    ;; Generoi oikeudet-EDN:t automaattisesti jos Excel on muuttunut
    (generoi-oikeudet-edn-jos-tarpeen)
    
    (http-palvelin/julkaise-reitti
     http :wmts-mml (GET "/wmts/*" req (hae-karttakuva req)))
    (http-palvelin/julkaise-reitti
     http :wmts-livi (GET "/wmtslivi/*" req (hae-karttakuva req)))
    this)

  (stop [{http :http-palvelin :as this}]
    (http-palvelin/poista-palvelut http :wmts-mml :wmts-livi)
    this))

(defrecord Tuotantomoodi []
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(defn luo-kehitysmoodi [kehitysmoodi]
  (if kehitysmoodi
    (->Kehitysmoodi)
    (->Tuotantomoodi)))
