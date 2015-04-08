(ns harja.palvelin.palvelut.selainvirhe
  "Palvelu, jolla voi tallentaa logiin selain virheen"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

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

(defn formatoi-virhe [{:keys [id]} {:keys [url viesti rivi sarake selain]}]
  (str "Selainvirhe: " viesti ", URL: " url ", selain: " selain ", rivi: " rivi ", sarake: " sarake ", käyttäjä id: " id))

(defn raportoi-selainvirhe
  "Logittaa yksittäisen selainvirheen"
  [user virhe]
  (log/warn (formatoi-virhe user virhe)))
  
  
