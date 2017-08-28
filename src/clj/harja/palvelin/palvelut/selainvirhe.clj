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
  (:import (com.atlassian.sourcemap SourceMapImpl SourceMap)))

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

(defn lue-tiedosto
  [tiedostonimi]
  (try (slurp (io/resource tiedostonimi))
       (catch java.lang.IllegalArgumentException e
          (when (= (.getMessage e) "Cannot open <nil> as a Reader.")
            (str "Lähdetiedostoa " tiedostonimi " ei löytynyt, joten muutamaa riviä koodia ei voida näyttää.")))))

(defn lahdetiedosto
  [tiedostopolku mapping kehitysmoodi]
  (if kehitysmoodi
    (let [tiedostopolku-ilman-paatetta  (apply str (take (- (count tiedostopolku) 3) tiedostopolku))] ;Otetaan '.js' pois
      (some (fn [tiedostopaate]
              (when (.exists (io/as-file (str "dev-resources/" tiedostopolku-ilman-paatetta tiedostopaate)))
                (str tiedostopolku-ilman-paatetta tiedostopaate)))
            [".cljs" ".cljc"]))
    (if-let [lahdetiedosto (re-find #"([\w\/_]+\.\w+)\?" (.getSourceFileName mapping))] ;; lähdetiedoston perässä saattaa olla timestamp. Otetaan se pois, jos on.
      (str "public/js/" (second lahdetiedosto))
      (str "public/js/" (.getSourceFileName mapping)))))

(defn lahde-tiedot
  [mapping kehitysmoodi tiedostopolku]
  (let [lahde-rivi (.getSourceLine mapping)
        lahde-sarake (.getSourceColumn mapping)
        lahdetiedosto (lahdetiedosto tiedostopolku mapping kehitysmoodi)
        muutama-rivi-lahdekoodia (let [numeroi-rivit (fn [rivit]
                                                        (map-indexed (fn [index rivi] (str (inc index) ": " rivi)) rivit))]
                                    (as-> lahdetiedosto $
                                          (lue-tiedosto $)
                                          (st/split $ #"\n")
                                          (numeroi-rivit $)
                                          (drop (if (> lahde-rivi 2)
                                                  (- lahde-rivi 2)
                                                  0)
                                                $)
                                          (take 5 $)))]
    {:rivi (inc lahde-rivi)
     :sarake (inc lahde-sarake)
     :lahdetiedosto lahdetiedosto
     :muutama-rivi-koodia (if (string? muutama-rivi-lahdekoodia)
                            muutama-rivi-lahdekoodia
                            (apply str (map #(str % "|||") muutama-rivi-lahdekoodia)))}))

(defn generoitu-stack->lahde-stack
  [{:keys [rivi sarake tiedostopolku]} kehitysmoodi]
  (let [source-map (when (re-find #"harja" tiedostopolku)
                    (try (-> (str tiedostopolku ".map") lue-tiedosto (SourceMapImpl.))
                         (catch Exception e ;; Jos lue-tiedosto palauttaa virhe-ilmoituksen, SourceMapImpl heittää exceptionin. Palautetaan nil.
                            nil)))]
    (when-let [mapping (and source-map (.getMapping source-map rivi sarake))]
      (lahde-tiedot mapping kehitysmoodi tiedostopolku))))


(defn lahdetiedoston-stack-trace
  [{:keys [rivi sarake lahdetiedosto muutama-rivi-koodia]}]
  (if (or (nil? rivi) (nil? sarake))
    "*Generoitua .js tiedostoa ei saatu mapattua .cljs tiedostoon joltain stack tracen riviltä.*|||"
    (str "at " lahdetiedosto ":"
               rivi ":"
               sarake "|||"
               (if (re-find #"\|\|\|" muutama-rivi-koodia)
                 (str "```" muutama-rivi-koodia "```")
                 muutama-rivi-koodia)
               "|||")))

(defn stack-lahde
  [rivit-sarakkeet-ja-tiedostopolku kehitysmoodi]
  (let [rivit-ja-sarakkeet-oikaistu (mapv #(oikaise-virheen-rivit-ja-sarakkeet %) rivit-sarakkeet-ja-tiedostopolku)
        lahdetiedoston-tiedot (vec (keep #(generoitu-stack->lahde-stack % kehitysmoodi)
                                         rivit-ja-sarakkeet-oikaistu))
        stack-lahde (apply str (map #(lahdetiedoston-stack-trace %)
                                    lahdetiedoston-tiedot))]
    stack-lahde))

(defn stack-trace-lahdekoodille
  [stack kehitysmoodi]
  (let [rivit-sarakkeet-ja-tiedostopolku (stack-tracen-rivit-sarakkeet-ja-tiedostopolku stack kehitysmoodi)]
    (if (-> rivit-sarakkeet-ja-tiedostopolku first :tiedostopolku)
      (stack-lahde rivit-sarakkeet-ja-tiedostopolku kehitysmoodi)
      (str "Generoitua javascript tiedostoa ei löytynyt polusta " (-> rivit-sarakkeet-ja-tiedostopolku first :tiedostopolku)))))

(defn formatoi-selainvirhe [{:keys [id kayttajanimi]} {:keys [url viesti rivi sarake selain stack sijainti]} kehitysmoodi]
  (let [titles ["Selainvirhe" "Sijainti Harjassa" "URL" "Selain" "Rivi" "Sarake" "Käyttäjä" (when stack "Stack") (when stack "Stack lähde")]
        stack-lahde (when stack (stack-trace-lahdekoodille stack kehitysmoodi))
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
                      :value (str  "Katkoksia " (count (get palvelulla-ryhmiteltyna palvelu)) " kpl|||"
                                   "ensimmäinen: " (->> (map :aika (get palvelulla-ryhmiteltyna palvelu))
                                                        (sort t/after?)
                                                        (last))
                                   "|||viimeinen: " (->> (map :aika (get palvelulla-ryhmiteltyna palvelu))
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
