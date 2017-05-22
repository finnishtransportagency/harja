(ns harja.palvelin.palvelut.selainvirhe
  "Palvelu, jolla voi tallentaa logiin selain virheen"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
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

(defn formatoi-virhe [{:keys [id kayttajanimi]} {:keys [url viesti rivi sarake selain stack sijainti]}]
  [:table
   [:tr [:td {:valign "top"} [:b "Selainvirhe"]] [:td [:pre (sanitoi viesti)]]]
   [:tr [:td {:valign "top"} [:b "Sijainti Harjassa:"]] [:td [:pre sijainti]]]
   [:tr [:td {:valign "top"} [:b "URL:"]] [:td [:pre (sanitoi url)]]]
   [:tr [:td {:valign "top"} [:b "Selain: "]] [:td [:pre (sanitoi selain)]]]
   [:tr [:td {:valign "top"} [:b "Rivi: "]] [:td [:pre (sanitoi rivi)]]]
   [:tr [:td {:valign "top"} [:b "Sarake: "]] [:td [:pre (sanitoi sarake)]]]
   [:tr [:td {:valign "top"} [:b "Käyttäjä: "]] [:td [:pre (sanitoi kayttajanimi) " (" (sanitoi id) ")"]]]
   (when stack [:tr [:td {:valign "top"} [:b "stack: "]] [:td [:pre (sanitoi stack)]]])])

(defn raportoi-selainvirhe
  "Logittaa yksittäisen selainvirheen"
  [user virhe]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (log/error (formatoi-virhe user virhe)))
