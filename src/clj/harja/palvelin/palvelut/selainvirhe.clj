(ns harja.palvelin.palvelut.selainvirhe
  "Palvelu, jolla voi tallentaa logiin selainvirheen"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.string :as st]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.java.io :as io]
            [harja.pvm :as pvm])
  (:import (com.atlassian.sourcemap SourceMapImpl SourceMap SourceMap$EachMappingCallback)))

(defn stack-tracen-rivit-sarakkeet-ja-tiedostopolku
  [stack-trace]
  (let [loydetyt-tiedot (re-seq #"\/([\/\w-]+\.js):(\d+):(\d+)" (str stack-trace))]
    (mapv #(hash-map :rivi (Integer. (nth % 2))
                     :sarake (Integer. (last %))
                     :tiedostopolku (second %))
          loydetyt-tiedot)))

(defn tunnista-selain-user-agentista
  [user-agent]
  (cond
    (and (re-find #"Chrome" user-agent)
         (re-find #"Safari" user-agent))       "Chrome"
    (and (re-find #"Safari" user-agent)
         (not (re-find #"Chrome" user-agent))) "Safari"
    (re-find #"Firefox" user-agent)            "Firefox"
    (re-find #"Trident/.*rv:([0-9]{1,}[\.0-9]{0,})|
               MSIE ([0-9]{1,}[\.0-9]{0,})" user-agent) "IE"
    :else nil))

(defn oikaise-virheen-rivit-ja-sarakkeet
  [{:keys [rivi sarake tiedostopolku]} selain]
  (let [rivi (dec rivi)
        rivi-teksti (-> (str "dev-resources/" tiedostopolku)
                        slurp
                        (st/split #"\n")
                        (get rivi))
        rivi-teksti-alku (subs rivi-teksti 0 sarake)
        sarake (case selain
                 "IE" (dec sarake)
                 "Firefox" (dec sarake)
                 "Chrome" (as-> rivi-teksti-alku $ (st/reverse $) (st/index-of $ " ") (- (count rivi-teksti-alku) $))
                 "Safari" (as-> rivi-teksti-alku $ (st/reverse $) (st/index-of $ " ") (- (count rivi-teksti-alku) $))
                 nil)]
    {:rivi rivi
     :sarake sarake}))

(defn tiedosto-rivi-ja-sarake
  [{:keys [rivi sarake tiedostopolku]}]
  (let [paikat (atom {})
        source-map (when (re-find #"harja" tiedostopolku)
                    (SourceMapImpl. (slurp (str "dev-resources/" tiedostopolku ".map"))))]
    (when source-map
      (.eachMapping source-map
                    (reify SourceMap$EachMappingCallback
                      (apply [this mapping]
                        (let [lahde-rivi (.getSourceLine mapping)
                              lahde-sarake (.getSourceColumn mapping)
                              generoitu-rivi (.getGeneratedLine mapping)
                              generoitu-sarake (.getGeneratedColumn mapping)
                              lahde-tiedosto (.getSourceFileName mapping)
                              lahde-symboli (.getSourceSymbolName mapping) ;; Valitettavasti ei anna oikeaa symbolia.
                              rivin-tiedot (get @paikat generoitu-rivi)
                              muutama-rivi-lahdekoodia #(let [tiedostopolku (str "dev-resources/" tiedostopolku)
                                                              tiedostopolku-ilman-paatetta  (apply str (take (- (count tiedostopolku) 3) tiedostopolku)) ;Otetaan '.js' pois
                                                              tiedosto (some (fn [tiedostopaate]
                                                                                (when (.exists (io/as-file (str tiedostopolku-ilman-paatetta tiedostopaate)))
                                                                                  (str tiedostopolku-ilman-paatetta tiedostopaate)))
                                                                             [".cljs" ".cljc"])
                                                              numeroi-rivit (fn [rivit]
                                                                              (map-indexed (fn [index rivi] (str (inc index) ": " rivi)) rivit))]
                                                            (as-> tiedosto $
                                                                  (slurp $)
                                                                  (st/split $ #"\n")
                                                                  (numeroi-rivit $)
                                                                  (drop (- lahde-rivi 2) $)
                                                                  (take 5 $)))]
                          (if (and (= generoitu-rivi rivi)
                                   (= generoitu-sarake sarake))
                            (swap! paikat
                                   assoc
                                   generoitu-rivi
                                   (update rivin-tiedot
                                           generoitu-sarake
                                           (fn [lahde-tiedot]
                                            (conj lahde-tiedot {:rivi (inc lahde-rivi)
                                                                :sarake (inc lahde-sarake)
                                                                :lahde-tiedosto lahde-tiedosto
                                                                :symboli lahde-symboli
                                                                :muutama-rivi-koodia (apply str (map #(str % "(slack-n)") (muutama-rivi-lahdekoodia)))}))))))))))
    @paikat))

(defn stack-trace-lahdekoodille
  [stack selain]
  (let [rivit-sarakkeet-ja-tiedostoppolku (stack-tracen-rivit-sarakkeet-ja-tiedostopolku stack)
        selain (tunnista-selain-user-agentista selain)
        rivit-ja-sarakkeet-oikaistu (mapv #(oikaise-virheen-rivit-ja-sarakkeet % selain) rivit-sarakkeet-ja-tiedostoppolku)
        stack-rivit-lahde (map #(tiedosto-rivi-ja-sarake %)
                               (map #(merge %1 %2) rivit-sarakkeet-ja-tiedostoppolku rivit-ja-sarakkeet-oikaistu))
        stack-lahde (apply str (map #(let [generoitu-rivi (first (keys %))
                                           generoitu-sarake (-> (vals %) first keys first)
                                           lahde-tiedot (-> (vals %) first vals ffirst)]
                                        (str "at " (apply str (map (fn [kansio]
                                                                     (str kansio "/"))
                                                                   (-> (:symboli lahde-tiedot) (st/split #"\.") butlast)))
                                                   (:lahde-tiedosto lahde-tiedot) ":"
                                                   (:rivi lahde-tiedot) ":"
                                                   (:sarake lahde-tiedot) "(slack-n)"
                                             "```" (:muutama-rivi-koodia lahde-tiedot) "```(slack-n)"))
                                    stack-rivit-lahde))]
      stack-lahde))

(defn formatoi-selainvirhe [{:keys [id kayttajanimi]} {:keys [url viesti rivi sarake selain stack sijainti]}]
  (let [titles ["Selainvirhe" "Sijainti Harjassa" "URL" "Selain" "Rivi" "Sarake" "Käyttäjä" (when stack "Stack") (when stack "Stack lähde")]
        stack-lahde (when stack (stack-trace-lahdekoodille stack selain))
        values [viesti sijainti url selain rivi sarake (str kayttajanimi " (" id ")") stack stack-lahde]]
    {:fields (vec (keep-indexed #(when (not (nil? %2))
                                    {:title %2 :value (get values %1)})
                                titles))}))

(defn formatoi-yhteyskatkos [{:keys [id kayttajanimi]} {:keys [yhteyskatkokset]}]
  (let [yhteyskatkokset (map #(assoc % :aika (c/from-date (:aika %)))
                             yhteyskatkokset)
        palvelulla-ryhmiteltyna (group-by :palvelu yhteyskatkokset)]
    {:text (str "Käyttäjä " kayttajanimi " (" id ")" " raportoi yhteyskatkoksista palveluissa:")
     :fields (mapv (fn [palvelu]
                     {:title (str palvelu)
                      :value (str  "Katkoksia " (count (get palvelulla-ryhmiteltyna palvelu)) " kpl(slack-n)"
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
