(ns harja.palvelin.palvelut.selainvirhe
  "Palvelu, jolla voi tallentaa logiin selainvirheen"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]))

(defn formatoi-selainvirhe [{:keys [id kayttajanimi]} {:keys [url viesti rivi sarake selain stack sijainti]}]
  (let [titles ["Selainvirhe" "Sijainti Harjassa" "URL" "Selain" "Rivi" "Sarake" "Käyttäjä" (when stack "Stack")]
        values [viesti sijainti url selain rivi sarake (str kayttajanimi " (" id ")") stack]]
    {:fields (vec (keep-indexed #(when (not (nil? %2))
                                    {:title %2 :value (get values %1)})
                                titles))}))

(defn formatoi-yhteyskatkos [{:keys [id kayttajanimi]} {:keys [yhteyskatkokset user-agent] :as katkostiedot}]
  (let [yhteyskatkokset (map #(assoc % :aika (c/from-date (:aika %)))
                             yhteyskatkokset)
        palvelulla-ryhmiteltyna (group-by :palvelu yhteyskatkokset)]
    {:text (str "Käyttäjä " kayttajanimi " (" id ")" " raportoi yhteyskatkoksista palveluissa:")
     :fields (mapv (fn [palvelu]
                     {:title (str palvelu)
                      :value (str  "Selain: " user-agent
                                   "(slack-n)Katkoksia: " (count (get palvelulla-ryhmiteltyna palvelu)) " kpl(slack-n)"
                                   "ensimmäinen: " (->> (map :aika (get palvelulla-ryhmiteltyna palvelu))
                                                        (sort t/after?)
                                                        (last))
                                   "(slack-n)viimeinen: " (->> (map :aika (get palvelulla-ryhmiteltyna palvelu))
                                                               (sort t/after?)
                                                               (first)))})
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
