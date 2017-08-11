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
  [stack-trace kehitysmoodi]
  (let [loydetyt-tiedot (re-seq #"\/([\/\w-]+\.js):(\d+):(\d+)" (str stack-trace))]
    (mapv #(hash-map :rivi (Integer. (nth % 2))
                     :sarake (Integer. (last %))
                     :tiedostopolku (if kehitysmoodi
                                      (second %)
                                      "public/js/harja.js"))
          loydetyt-tiedot)))

(defn oikaise-virheen-rivit-ja-sarakkeet
  [{:keys [rivi sarake tiedostopolku]}]
  {:rivi (dec rivi)
   :sarake (dec sarake)
   :tiedostopolku tiedostopolku})

(defn tiedosto-rivi-ja-sarake
  [{:keys [rivi sarake tiedostopolku]} kehitysmoodi]
  (let [paikat (atom {})
        source-map (when (re-find #"harja" tiedostopolku)
                    (-> (str tiedostopolku ".map") (io/resource) slurp (SourceMapImpl.)))]
    (log/debug "TIEDOSTOPOLKU: " (io/resource tiedostopolku))
    (log/debug "LÄHDETIEDOSTO: " tiedostopolku)
    (when-let [mapping (.getMapping source-map rivi sarake)]
      (let [lahde-rivi (.getSourceLine mapping)
            lahde-sarake (.getSourceColumn mapping)
            generoitu-rivi (.getGeneratedLine mapping)
            generoitu-sarake (.getGeneratedColumn mapping)
            rivin-tiedot (get @paikat generoitu-rivi)
            lahdetiedosto (if kehitysmoodi
                            (let [tiedostopolku-ilman-paatetta  (apply str (take (- (count tiedostopolku) 3) tiedostopolku))] ;Otetaan '.js' pois
                              (some (fn [tiedostopaate]
                                      (when (.exists (io/as-file (str "dev-resources/" tiedostopolku-ilman-paatetta tiedostopaate)))
                                        (str "dev-resources/" tiedostopolku-ilman-paatetta tiedostopaate)))
                                    [".cljs" ".cljc"]))
                            (if-let [lahdetiedosto (re-find #"([\w\/_]+\.\w+)\?" (.getSourceFileName mapping))] ;; lähdetiedoston perässä saattaa olla timestamp. Otetaan se pois, jos on.
                              (second lahdetiedosto)
                              (.getSourceFileName mapping)))
            muutama-rivi-lahdekoodia (let [numeroi-rivit (fn [rivit]
                                                            (map-indexed (fn [index rivi] (str (inc index) ": " rivi)) rivit))]
                                        (try (as-> lahdetiedosto $
                                                   (slurp $)
                                                   (st/split $ #"\n")
                                                   (numeroi-rivit $)
                                                   (drop (if (> lahde-rivi 2)
                                                           (- lahde-rivi 2)
                                                           0)
                                                         $)
                                                   (take 5 $))
                                             (catch java.io.FileNotFoundException e
                                               (str "Lähdetiedostoa " lahdetiedosto " ei löytynyt, joten muutamaa riviä koodia ei voida näyttää."))))]
          (swap! paikat
                 assoc
                 generoitu-rivi
                 (update rivin-tiedot
                         generoitu-sarake
                         (fn [lahde-tiedot]
                          (conj lahde-tiedot {:rivi (inc lahde-rivi)
                                              :sarake (inc lahde-sarake)
                                              :lahdetiedosto lahdetiedosto
                                              :muutama-rivi-koodia (if (string? muutama-rivi-lahdekoodia)
                                                                      muutama-rivi-lahdekoodia
                                                                      (apply str (map #(str % "(slack-n)") muutama-rivi-lahdekoodia)))})))))
      @paikat)))

(defn stack-trace-lahdekoodille
  [stack user-agent kehitysmoodi]
  (let [rivit-sarakkeet-ja-tiedostopolku (stack-tracen-rivit-sarakkeet-ja-tiedostopolku stack kehitysmoodi)
        stack-lahde (if (-> rivit-sarakkeet-ja-tiedostopolku first :tiedostopolku (io/resource))
                      (let [rivit-ja-sarakkeet-oikaistu (mapv #(oikaise-virheen-rivit-ja-sarakkeet %) rivit-sarakkeet-ja-tiedostopolku)
                            stack-rivit-lahde (vec (keep #(tiedosto-rivi-ja-sarake % kehitysmoodi)
                                                         rivit-ja-sarakkeet-oikaistu))
                            stack-lahde (apply str (map #(let [generoitu-rivi (first (keys %))
                                                               generoitu-sarake (-> (vals %) first keys first)
                                                               lahde-tiedot (-> (vals %) first vals ffirst)]
                                                            (if (empty? %)
                                                              "*Generoitua .js tiedostoa ei saatu mapattua .cljs tiedostoon joltain stack tracen riviltä.*(slack-n)"
                                                              (str "at " (:lahdetiedosto lahde-tiedot) ":"
                                                                         (:rivi lahde-tiedot) ":"
                                                                         (:sarake lahde-tiedot) "(slack-n)"
                                                                         (if (re-find #"\(slack-n\)" (:muutama-rivi-koodia lahde-tiedot))
                                                                           (str "```" (:muutama-rivi-koodia lahde-tiedot) "```")
                                                                           (:muutama-rivi-koodia lahde-tiedot))
                                                                         "(slack-n)")))
                                                        stack-rivit-lahde))]
                        stack-lahde)
                      (str "Generoitua javascript tiedostoa ei löytynyt polusta " (-> rivit-sarakkeet-ja-tiedostopolku first :tiedostopolku)))]
      stack-lahde))

(defn formatoi-selainvirhe [{:keys [id kayttajanimi]} {:keys [url viesti rivi sarake selain stack sijainti]} kehitysmoodi]
  (let [titles ["Selainvirhe" "Sijainti Harjassa" "URL" "Selain" "Rivi" "Sarake" "Käyttäjä" (when stack "Stack") (when stack "Stack lähde")]
        stack-lahde (when stack (stack-trace-lahdekoodille stack selain kehitysmoodi))
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
  [user virhetiedot kehitysmoodi]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (log/error (formatoi-selainvirhe user virhetiedot kehitysmoodi)))

(defn raportoi-yhteyskatkos
  "Logittaa yksittäisen käyttäjän raportoimat selainvirheet"
  [user katkostiedot]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (log/debug "Vastaanotettu yhteyskatkostiedot: " (pr-str katkostiedot))
  (log/warn (formatoi-yhteyskatkos user katkostiedot))
  {:ok? true})


(defrecord Selainvirhe [kehitysmoodi]
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :raportoi-selainvirhe (fn [user virhe]
                                              (raportoi-selainvirhe user virhe kehitysmoodi)))
    (julkaise-palvelu (:http-palvelin this)
                      :raportoi-yhteyskatkos (fn [user virhe]
                                               (raportoi-yhteyskatkos user virhe)))
    this)
  (stop [this]
    (poista-palvelut (:http-palvelin this) :raportoi-selainvirhe
                     :raportoi-yhteyskatkos)
    this))
