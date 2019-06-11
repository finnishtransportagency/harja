(ns harja.palvelin.palvelut.selainvirhe
  "Palvelu, jolla voi tallentaa logiin selainvirheen"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(defn formatoi-yhteyskatkos [{:keys [id kayttajanimi]} {:keys [yhteyskatkokset]}]
  (let [yhteyskatkokset (map #(assoc % :aika (c/from-date (:aika %)))
                             yhteyskatkokset)
        palvelulla-ryhmiteltyna (group-by :palvelu yhteyskatkokset)]
    {:text (str "Käyttäjä " kayttajanimi " (" id ")" " raportoi yhteyskatkoksista palveluissa:")
     :fields (mapv (fn [palvelu]
                     {:title (str palvelu)
                      :value (str  "Katkoksia " (count (get palvelulla-ryhmiteltyna palvelu)) " kpl|||"
                                   "ensimmäinen: " (->> (map :aika (get palvelulla-ryhmiteltyna palvelu))
                                                        (sort t/after?)
                                                        (last))
                                   "|||viimeinen: " (->> (map :aika (get palvelulla-ryhmiteltyna palvelu))
                                                         (sort t/after?)
                                                         (first)))})
                  (keys palvelulla-ryhmiteltyna))}))

(defn raportoi-yhteyskatkos
  "Logittaa yksittäisen käyttäjän raportoimat selainvirheet"
  [user katkostiedot]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  ;;(log/debug "Vastaanotettu yhteyskatkostiedot: " (pr-str katkostiedot))
  (log/warn (formatoi-yhteyskatkos user katkostiedot))
  {:ok? true})


(defrecord Selainvirhe [kehitysmoodi]
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :raportoi-selainvirhe (fn [user virhe]
                                              ))
    (julkaise-palvelu (:http-palvelin this)
                      :raportoi-yhteyskatkos (fn [user virhe]
                                               (raportoi-yhteyskatkos user virhe)))
    this)
  (stop [this]
    (poista-palvelut (:http-palvelin this) :raportoi-selainvirhe
                     :raportoi-yhteyskatkos)
    this))
