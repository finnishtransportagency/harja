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
  (log/info "stack: " stack)
  (str "Selainvirhe: " (sanitoi viesti)
       ", URL: " (sanitoi url)
       ", selain: " (sanitoi selain)
       ", rivi: " (sanitoi rivi)
       ", sarake: " (sanitoi sarake)
       ", käyttäjä id: " (sanitoi id)
       ", stack: <pre>" (sanitoi stack) "</pre>"))

(defn raportoi-selainvirhe
  "Logittaa yksittäisen selainvirheen"
  [user virhe]
  (log/warn (formatoi-virhe user virhe)))
  
  
