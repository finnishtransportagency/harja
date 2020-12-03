(ns harja.palvelin.komponentit.kehitysmoodi
  "Komponentti, joka on ajossa VAIN paikallisen kehityksen aikana.
  Tässä voidaan käynnistää palveluita, joita halutaan kehityksen aikana
  mutta ei tuotannossa."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [org.httpkit.client :as http]
            [compojure.core :refer [GET]]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(def +wmts-mml-url+ "https://harja-test.solitaservices.fi/harja/integraatiotesti/wmts")
(def +wmts-livi-url+ "https://harja-test.solitaservices.fi/harja/integraatiotesti/wmtslivi")


(def basic-auth-header (delay (str/trim-newline (slurp "../harja-testidata/.harja/mml"))))

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

(defrecord Kehitysmoodi []
  component/Lifecycle
  (start [{http :http-palvelin :as this}]
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
