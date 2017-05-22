(ns harja.palvelin.palvelut.selainvirhe
  "Palvelu, jolla voi tallentaa logiin selain virheen"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.string :as str]
            [clj-time.core :as t]))

(defn sanitoi [sisalto]
  (str/replace (str sisalto) "<" "&lt;"))

(defn formatoi-selainvirhe [{:keys [id kayttajanimi]} {:keys [url viesti rivi sarake selain stack sijainti]}]
  [:table
   [:tr [:td {:valign "top"} [:b "Selainvirhe"]] [:td [:pre (sanitoi viesti)]]]
   [:tr [:td {:valign "top"} [:b "Sijainti Harjassa:"]] [:td [:pre sijainti]]]
   [:tr [:td {:valign "top"} [:b "URL:"]] [:td [:pre (sanitoi url)]]]
   [:tr [:td {:valign "top"} [:b "Selain: "]] [:td [:pre (sanitoi selain)]]]
   [:tr [:td {:valign "top"} [:b "Rivi: "]] [:td [:pre (sanitoi rivi)]]]
   [:tr [:td {:valign "top"} [:b "Sarake: "]] [:td [:pre (sanitoi sarake)]]]
   [:tr [:td {:valign "top"} [:b "Käyttäjä: "]] [:td [:pre (sanitoi kayttajanimi) " (" (sanitoi id) ")"]]]
   (when stack [:tr [:td {:valign "top"} [:b "stack: "]] [:td [:pre (sanitoi stack)]]])])

(defn formatoi-yhteyskatkos [{:keys [id kayttajanimi]} katkostiedot]
  (let [palvelulla-ryhmiteltyna (group-by :palvelu katkostiedot)]
    [:div "Käyttäjä " (str (sanitoi kayttajanimi) " (" (sanitoi id) ")") " raportoi yhteyskatkoksista palveluissa:"
     [:table
      (map (fn [palvelu]
             [:tr
              [:td {:valign "top"} [:b (str palvelu)]]
              [:td [:pre (str "Katkoksia " (count (get palvelulla-ryhmiteltyna palvelu)) " kpl, "
                              "ensimmäinen: " (first (sort t/after? (get palvelulla-ryhmiteltyna palvelu)))
                              "viimeinen: " (last (sort t/after? (get palvelulla-ryhmiteltyna palvelu))))]]])
           (keys palvelulla-ryhmiteltyna))]]))

(defn raportoi-selainvirhe
  "Logittaa yksittäisen selainvirheen"
  [user virhetiedot]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (log/error (formatoi-selainvirhe user virhetiedot)))

(defn raportoi-yhteyskatkos
  "Logittaa yksittäisen käyttäjän raportoimat selainvirheet"
  [user katkostiedot]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (log/warn (formatoi-yhteyskatkos user katkostiedot)))


(defrecord Selainvirhe []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :raportoi-selainvirhe (fn [user virhe]
                                              (raportoi-selainvirhe user virhe)))
    (julkaise-palvelu (:http-palvelin this)
                      :raportoi-yhteyskatkos (fn [user virhe]
                                               (raportoi-yhteyskatkos user virhe)))
    this)
  (stop [this]
    (poista-palvelut (:http-palvelin this) :raportoi-selainvirhe
                     :raportoi-yhteyskatkos)
    this))