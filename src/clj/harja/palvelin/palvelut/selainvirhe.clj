(ns harja.palvelin.palvelut.selainvirhe
  "Palvelu, jolla voi tallentaa logiin selain virheen"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.string :as str]))

(declare raportoi-selainvirhe)

(defrecord Selainvirhe []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :raportoi-selainvirhe (fn [user virhe]
                                              (raportoi-selainvirhe user virhe)))
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :raportoi-selainvirhe)
    this))

(defn sanitoi [sisalto]
  (str/replace (str sisalto) "<" "&lt;"))

(defn formatoi-virhe [{:keys [id]} {:keys [url viesti rivi sarake selain stack]}]
  [:table
   [:tr [:td {:valign "top"} [:b "Selainvirhe"]] [:td [:pre (sanitoi viesti)]]]
   [:tr [:td {:valign "top"} [:b "URL:"]] [:td [:pre (sanitoi url)]]]
   [:tr [:td {:valign "top"} [:b "selain: "]] [:td [:pre (sanitoi selain)]]]
   [:tr [:td {:valign "top"} [:b "rivi: "]] [:td [:pre (sanitoi rivi)]]]
   [:tr [:td {:valign "top"} [:b "sarake: "]] [:td [:pre (sanitoi sarake)]]]
   [:tr [:td {:valign "top"} [:b "käyttäjä id: "]] [:td [:pre (sanitoi id)]]]
   (when stack [:tr [:td {:valign "top"} [:b "stack: "]] [:td [:pre (sanitoi stack)]]])])

(defn raportoi-selainvirhe
  "Logittaa yksittäisen selainvirheen"
  [user virhe]
  (log/warn (formatoi-virhe user virhe)))
  
  
