(ns harja.domain.tierekisteri.tietolajit
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.tyokalut.merkkijono :as merkkijono]
            [clojure.string :as str]
            [clojure.set :as set]
            [harja.pvm :as pvm]
            [clj-time.format :as df]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.walk :as walk])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn- jarjesta-ja-suodata-tietolajin-kuvaus [tietolajin-kuvaus]
  (sort-by :jarjestysnumero (filter :jarjestysnumero (:ominaisuudet tietolajin-kuvaus))))

(defn- heita-validointipoikkeus [tietolaji virhe]
  (let [viesti (str "Virhe tietolajin " tietolaji " arvojen käsittelyssä: " virhe)]
    (throw+ {:type virheet/+virhe-tietolajin-arvojen-kasittelyssa+
             :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+ :viesti viesti}]})))

(defn- validoi-tyyppi
  "Validoi, että annettu arvo on annettua tyyppiä. Jos ei ole, heittää poikkeuksen. Jos on, palauttaa nil."
  [arvo tietolaji kenttatunniste tietotyyppi koodisto pakollinen]
  (case tietotyyppi
    :merkkijono nil ;; Kaikki kentät ovat pohjimmiltaan merkkijonoja
    :numeerinen (when (and arvo (not (merkkijono/kokonaisluku? arvo)))
                  (heita-validointipoikkeus tietolaji (str "Kentän '" kenttatunniste "' arvo ei ole kokonaisluku.")))
    :paivamaara (when (or pakollinen arvo)
                  (try
                    (df/parse (df/formatter "yyyyMMdd") arvo)
                    (catch Exception e
                      (heita-validointipoikkeus tietolaji (str "Kentän '" kenttatunniste "' arvo ei ole muotoa iso-8601.")))))
    :koodisto (when (and (or
                           (and pakollinen (not (nil? koodisto)))
                           (not (empty? arvo)))
                         (empty? (filter #(= (str (:koodi %)) arvo) koodisto)))
                (heita-validointipoikkeus tietolaji (str "Kentän '" kenttatunniste "' arvo '" arvo "' ei sisälly koodistoon.")))))

(defn- validoi-pituus [arvo tietolaji kenttatunniste pituus]
  (when (< pituus (count arvo))
    (heita-validointipoikkeus tietolaji (str "Liian pitkä arvo '" arvo "' kentässä '" kenttatunniste "', maksimipituus: " pituus "."))))

(defn- validoi-pakollisuus [arvo tietolaji kenttatunniste pakollinen]
  (when (and pakollinen (not arvo))
    (heita-validointipoikkeus tietolaji (str "Pakollinen arvo puuttuu kentästä '" kenttatunniste "'."))))

(defn validoi-arvoalue [arvo tietolaji kenttatunniste tietotyyppi alaraja ylaraja]
  (when (and arvo (not (str/blank? arvo)) (= :numeerinen tietotyyppi))
    (let [arvo (BigDecimal. arvo)]
      (when (and alaraja (< arvo alaraja))
        (heita-validointipoikkeus tietolaji (format "Kentän arvon: %s pitää olla vähintään: %s" kenttatunniste alaraja)))
      (when (and ylaraja (> arvo ylaraja))
        (heita-validointipoikkeus tietolaji (format "Kentän arvon: %s pitää olla vähemmän kuin: %s" kenttatunniste ylaraja))))))

(defn validoi-arvo
  "Validoi, että annettu arvo täyttää kentän kuvauksen vaatimukset.
   Jos vaatimuksia ei täytetä, heittää poikkeuksen, muuten palauttaa nil."
  [arvo {:keys [kenttatunniste pakollinen pituus tietotyyppi koodisto alaraja ylaraja] :as kentan-kuvaus} tietolaji]
  (assert tietolaji "Arvoa ei voi validoida ilman tietolajia")
  (assert kentan-kuvaus "Arvoa ei voida validoida ilman kuvausta")
  (validoi-pakollisuus arvo tietolaji kenttatunniste pakollinen)
  (when arvo
    (validoi-pituus arvo tietolaji kenttatunniste pituus)
    (validoi-tyyppi arvo tietolaji kenttatunniste tietotyyppi koodisto pakollinen)
    (validoi-arvoalue arvo tietolaji kenttatunniste tietotyyppi alaraja ylaraja)))

(defn validoi-tietolajin-arvot
  "Tarkistaa, että tietolajin arvot on annettu oikein tietolajin kuvauksen mukaisesti.
   Jos arvoissa on ongelma, heittää poikkeuksen. Jos arvot ovat ok, palauttaa nil."
  [tietolaji arvot tietolajin-kuvaus]
  (assert tietolaji "Arvoja ei voi validoida ilman tietolajia")
  (assert arvot "Ei validoitavia arvoja")
  (assert tietolajin-kuvaus "Arvoja ei voida validoida ilman tietolajin kuvausta")
  (let [kenttien-kuvaukset (sort-by :jarjestysnumero (:ominaisuudet tietolajin-kuvaus))
        kuvatut-kenttatunnisteet (into #{} (map :kenttatunniste kenttien-kuvaukset))
        annetut-kenttatunnisteet (into #{} (keys arvot))
        ylimaaraiset-kentat (set/difference annetut-kenttatunnisteet kuvatut-kenttatunnisteet)]

    ;; Tarkista, ettei ole ylimääräisiä kenttiä
    (when-not (empty? ylimaaraiset-kentat)
      (heita-validointipoikkeus
        tietolaji
        (str "Tietolajin arvoissa on ylimääräisiä kenttiä, joita ei löydy tierekisterin tietolajin kuvauksesta: "
             (str/join ", " ylimaaraiset-kentat) ". Sallitut kentät: " (str/join ", " kuvatut-kenttatunnisteet))))

    ;; Ei ylimääräisiä kenttiä, validoi annetut kentät
    (doseq [kentan-kuvaus kenttien-kuvaukset]
      (validoi-arvo (clojure.walk/stringify-keys (get arvot (:kenttatunniste kentan-kuvaus)))
                    kentan-kuvaus
                    tietolaji))))

(defn- muunna-teksti-kentan-mukaiseen-tyyppiin [arvo-tekstina kentan-kuvaus]
  (case (:tietotyyppi kentan-kuvaus)
    :merkkijono arvo-tekstina
    :numeerinen (do (merkkijono/parsittavissa-intiksi? arvo-tekstina)
                    (Integer/parseInt arvo-tekstina))
    :paivamaara (do (merkkijono/iso-8601-paivamaara? arvo-tekstina)
                    (pvm/iso-8601->pvm arvo-tekstina))
    :koodisto arvo-tekstina))

(defn- hae-arvo
  "Ottaa arvot-stringin ja etsii sieltä halutun arvon käyttäen apuna kenttien-kuvaukset -mappia."
  [arvot-merkkijono kenttien-kuvaukset jarjestysnumero]
  (let [jarjestysnumeron-kentta (first (filter #(= (:jarjestysnumero %) jarjestysnumero)
                                               kenttien-kuvaukset))
        alkuindeksi (apply +
                           (map :pituus
                                (filter #(< (:jarjestysnumero %) jarjestysnumero)
                                        kenttien-kuvaukset)))
        loppuindeksi (+ alkuindeksi (:pituus jarjestysnumeron-kentta))
        arvo-teksti (str/trim (subs arvot-merkkijono alkuindeksi loppuindeksi))]
    arvo-teksti))

(defn- muunna-kentta-stringiksi [arvo kentan-kuvaus]
  (case (:tietotyyppi kentan-kuvaus)
    :merkkijono arvo
    :numeerinen (str arvo)
    :paivamaara (pvm/pvm->iso-8601 arvo)
    :koodisto arvo))

(defn- muodosta-kentta [tietolaji arvot-map {:keys [pituus kenttatunniste] :as kentan-kuvaus}]
  (let [arvo (get arvot-map kenttatunniste)]
    (validoi-arvo arvo kentan-kuvaus tietolaji)
    (merkkijono/tayta-oikealle pituus (if arvo arvo ""))))

(defn tietolajin-arvot-map->merkkijono
  "Ottaa arvot-mapin ja purkaa sen stringiksi käyttäen apuna annettua tietolajin kuvausta.
  Tietolajin kuvaus on Tierekisterin palauttama kuvaus tietolajista, muunnettuna Clojure-mapiksi."
  [arvot-map tietolajin-kuvaus]
  (let [tietolaji (:tunniste tietolajin-kuvaus)
        kenttien-kuvaukset (jarjesta-ja-suodata-tietolajin-kuvaus tietolajin-kuvaus)
        string-osat (map (partial muodosta-kentta tietolaji arvot-map) kenttien-kuvaukset)]
    (str/join string-osat)))

(defn- pura-kentta [arvot-merkkijono
                    tietolaji
                    kenttien-kuvaukset
                    {:keys [jarjestysnumero kenttatunniste] :as kentan-kuvaus}]
  (let [arvo (hae-arvo arvot-merkkijono kenttien-kuvaukset jarjestysnumero)]
    (validoi-arvo arvo kentan-kuvaus tietolaji)
    {kenttatunniste arvo}))

(defn testaa-arvojen-versio!
  "Tierekisteri voi muuttaa kenttien kuvauksia, joka heijastuu merkkijonon pituudessa.
   Harjassa ei osata käsitellä version muutosta, joten nakataan poikkeus."
  [arvot-merkkijono kenttien-kuvaukset tietolaji]
  (let [nykyisen-version-pituus (transduce (map :pituus)
                                           +
                                           0
                                           kenttien-kuvaukset)
        testattavan-arvojen-pituus (count arvot-merkkijono)]
    (when-not (= nykyisen-version-pituus testattavan-arvojen-pituus)
      (throw+ {:type virheet/+virhe-tietolajin-arvojen-versiossa+
               :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+ :viesti (str "Virhe tietolajin " tietolaji " versiossa")}]}))))

(defn tietolajin-arvot-merkkijono->map
  "Ottaa arvot-stringin ja purkaa sen mapiksi käyttäen apuna annettua tietolajin kuvausta.
  Tietolajin kuvaus on Tierekisterin palauttama kuvaus tietolajista, muunnettuna Clojure-mapiksi."
  [arvot-merkkijono tietolajin-kuvaus]
  (let [tietolaji (:tunniste tietolajin-kuvaus)
        kenttien-kuvaukset (jarjesta-ja-suodata-tietolajin-kuvaus tietolajin-kuvaus)
        _ (testaa-arvojen-versio! arvot-merkkijono kenttien-kuvaukset tietolaji)
        map-osat (mapv
                   (partial pura-kentta arvot-merkkijono tietolaji kenttien-kuvaukset)
                   kenttien-kuvaukset)]
    (reduce merge map-osat)))

(defn- muunna-tietolajin-arvot-stringiksi [tietolajin-kuvaus arvot-map]
  (tietolajin-arvot-map->merkkijono
    (clojure.walk/stringify-keys arvot-map)
    tietolajin-kuvaus))

(defn validoi-ja-muunna-arvot-merkkijonoksi
  "Hakee tietolajin kuvauksen, validoi arvot sen pohjalta ja muuntaa arvot merkkijonoksi"
  [tierekisteri arvot tietolaji]
  (when tierekisteri
    (let [vastaus (tierekisteri/hae-tietolaji
                    tierekisteri
                    tietolaji
                    nil)
          tietolajin-kuvaus (:tietolaji vastaus)]
      (try
        (validoi-tietolajin-arvot
          tietolaji
          (clojure.walk/stringify-keys arvot)
          tietolajin-kuvaus)
        (catch Exception e
          (throw+ {:type virheet/+viallinen-kutsu+
                   :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+ :viesti (.getMessage e)}]})))
      (muunna-tietolajin-arvot-stringiksi
        tietolajin-kuvaus
        arvot))))

(defn validoi-ja-muunna-merkkijono-arvoiksi
  "Hakee tietolajin kuvauksen, muuntaa merkkijonon arvoiksi ja validoi ne"
  [tierekisteri merkkijono tietolaji]
  (when tierekisteri
    (let [vastaus (tierekisteri/hae-tietolaji tierekisteri tietolaji nil)
          tietolajin-kuvaus (:tietolaji vastaus)
          arvot (walk/keywordize-keys (tietolajin-arvot-merkkijono->map merkkijono tietolajin-kuvaus))]
      (try
        (validoi-tietolajin-arvot
          tietolaji
          (clojure.walk/stringify-keys arvot)
          tietolajin-kuvaus)
        (catch Exception e
          (throw+ {:type virheet/+viallinen-kutsu+
                   :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+ :viesti (.getMessage e)}]})))
      arvot)))
