(ns harja.palvelin.palvelut.selainvirhe
  "Palvelu, jolla voi tallentaa logiin selain virheen"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]))

(defn sanitoi [sisalto]
  (str/replace (str sisalto) #"[<>&]" (fn [merkki] (case merkki
                                                      "<" "&lt;"
                                                      ">" "&gt;"
                                                      "&" "&amp;"))))

(defn formatoi-selainvirhe [{:keys [id kayttajanimi]} {:keys [url viesti rivi sarake selain stack sijainti]}]
  {:fields
          (filterv (fn [mappi] (not (nil? (:title mappi))))
            (map #(hash-map :title %1 :value (sanitoi %2))
                  ["Selainvirhe" "Sijainti Harjassa" "URL" "Selain" "Rivi" "Sarake" "Käyttäjä" (when stack "Stack")]
                  [viesti sijainti url selain rivi sarake (str (sanitoi kayttajanimi) " (" (sanitoi id) ")") stack]))})

(defn formatoi-yhteyskatkos [{:keys [id kayttajanimi]} {:keys [yhteyskatkokset] :as katkostiedot}]
  (let [yhteyskatkokset (map #(assoc % :aika (c/from-date (:aika %)))
                             yhteyskatkokset)
        palvelulla-ryhmiteltyna (group-by :palvelu yhteyskatkokset)]
    {:text (str "Käyttäjä " (sanitoi kayttajanimi) " (" (sanitoi id) ")" " raportoi yhteyskatkoksista palveluissa:")
     :fields (mapv (fn [palvelu]
                      (hash-map :title (str palvelu)
                                :value (str "Katkoksia " (count (get palvelulla-ryhmiteltyna palvelu)) " kpl, "
                                           "ensimmäinen: " (->> (map :aika (get palvelulla-ryhmiteltyna palvelu))
                                                                (sort t/after?)
                                                                (first))
                                           "viimeinen: " (->> (map :aika (get palvelulla-ryhmiteltyna palvelu))
                                                              (sort t/after?)
                                                              (last)))))
                  (keys palvelulla-ryhmiteltyna))}))

(defn raportoi-selainvirhe
  "Logittaa yksittäisen selainvirheen"
  [user virhetiedot]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (log/error (formatoi-selainvirhe user virhetiedot)))

(defn raportoi-yhteyskatkos
  "Logittaa yksittäisen käyttäjän raportoimat selainvirheet"
  [user katkostiedot]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (log/debug "Vastaanotettu yhteyskatkostiedot: " (pr-str katkostiedot))
  (log/warn (formatoi-yhteyskatkos user katkostiedot))
  {:ok? true})


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
