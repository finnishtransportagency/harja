(ns harja.palvelin.komponentit.kehitysmoodi
  "Komponentti, joka on ajossa VAIN paikallisen kehityksen aikana.
  Tässä voidaan käynnistää palveluita, joita halutaan kehityksen aikana
  mutta ei tuotannossa."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [org.httpkit.client :as http]
            [compojure.core :refer [GET]]
            [clojure.string :as str]))

(def +wmts-url+ "https://karttakuva.maanmittauslaitos.fi")

(def basic-auth-header (delay (slurp "../.harja/mml")))

(def debug-last-wmts-response (atom nil))

(defn hae-karttakuva [{:keys [uri query-params] :as req}]
  (let [{:keys [status body headers] :as res}
        @(http/get (str +wmts-url+
                        (str/replace uri #"/wmts/" "/"))
                   {:query-params query-params
                    :headers {"Authorization" (str "Basic " @basic-auth-header)}})]
    (reset! debug-last-wmts-response res)
    {:status status
     :body body
     :headers {"Cache-Control"  (:cache-control headers)
               "Content-Length" (:content-length headers)
               "Content-Type" (:content-type headers)
               "Date" (:date headers)
               "Expires" (:expires headers)
               "Last-modified" (:last-modified headers)}}))

(defrecord Kehitysmoodi []
  component/Lifecycle
  (start [{http :http-palvelin :as this}]
    (http-palvelin/julkaise-reitti
     http :wmts
     (GET "/wmts/*" req
          (hae-karttakuva req)))
    this)

  (stop [{http :http-palvelin :as this}]
    (http-palvelin/poista-palvelu http :wmts)
    this))

(defrecord Tuotantomoodi []
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(defn luo-kehitysmoodi [kehitysmoodi]
  (if kehitysmoodi
    (->Kehitysmoodi)
    (->Tuotantomoodi)))
