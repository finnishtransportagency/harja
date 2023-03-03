(ns harja.palvelin.integraatiot.api.analytiikka
  "Analytiikkaportaalille endpointit"
  (:require [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as s]
            [compojure.core :refer [POST GET DELETE]]
            [clojure.string :refer [join]]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kevyesti-get-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.toteumat :as toteuma-kyselyt]
            [harja.kyselyt.materiaalit :as materiaalit-kyselyt]
            [harja.kyselyt.toimenpidekoodit :as toimenpidekoodi-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.organisaatiot :as organisaatiot-kyselyt]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.api.tyokalut.parametrit :as parametrit])
  (:import (java.text SimpleDateFormat))
  (:use [slingshot.slingshot :only [throw+]]))

(s/def ::alkuaika #(and (string? %) (> (count %) 20) (inst? (.parse (SimpleDateFormat. parametrit/pvm-aika-muoto) %))))
(s/def ::loppuaika #(and (string? %) (> (count %) 20) (inst? (.parse (SimpleDateFormat. parametrit/pvm-aika-muoto) %))))

(defn- tarkista-toteumahaun-parametrit [parametrit]
  (parametrivalidointi/tarkista-parametrit
    parametrit
    {:alkuaika "Alkuaika puuttuu"
     :loppuaika "Loppuaika puuttuu"})
  (when (not (s/valid? ::alkuaika (:alkuaika parametrit)))
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Alkuaika väärässä muodossa: %s Anna muodossa: yyyy-MM-dd'T'HH:mm:ss esim: 2005-01-01T00:00:00+03" (:alkuaika parametrit))}))
  (when (not (s/valid? ::loppuaika (:loppuaika parametrit)))
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Loppuaika väärässä muodossa: %s Anna muodossa: yyyy-MM-dd'T'HH:mm:ss esim: 2005-01-02T00:00:00+03" (:loppuaika parametrit))})))

(def db-tehtavat->avaimet
  {:f1 :id
   :f2 :maara_maara
   :f3 :maara_yksikko
   :f4 :selite})

(def db-materiaalit->avaimet
  {:f1 :materiaali
   :f2 :maara_maara
   :f3 :maara_yksikko})

;; Mäpätään json row array tyyppiset elementit (:f<x> muotoiset kolumnien nimet) alaviivarakenteiseksi
;; mäpiksi, jotta data saadaan formatoitua skeeman mukaisesti
(def db-reitti->avaimet
  {:f1 :reittipiste_aika
   :f2 :reittipiste_tehtavat
   :f3 :reittipiste_sijainti_epsg4326
   :f4 :reittipiste_sijainti_epsg3067
   :f5 :reittipiste_materiaalit})

(defn rakenna-reittipiste-sijainti
  "Reittipisteen sijainnin tiedot tulevat row_to_json funktion käytön vuoksi tekstimuodossa, joten
  niiden käsittely koordinaattimuotoon on monimutkaista."
  [reitti lisaa-epsg-4326-koordinaatit?]
  (let [tee-koordinaatit (fn [sijainti x-avain y-avain]
                           (when sijainti (let [sijainnit (-> sijainti
                                                            (str/replace #"\(|\)" "")
                                                            (str/split #","))]
                                            {x-avain (first sijainnit)
                                             y-avain (second sijainnit)})))
        koordinaatit-3067 (tee-koordinaatit (get-in reitti [:reittipiste :sijainti :epsg3067]) :x :y)
        koordinaatit-4326 (when lisaa-epsg-4326-koordinaatit?
                            ;; PostGIS palauttaa epsg:4326-koordinaatin lon-lat järjestyksessä, täsmäten 3067:n järjestystä
                            ;; Käytetään avaimina :lat ja :lon, jotta pysyy selkeänä, mikä suunta on mikäkin
                            (tee-koordinaatit (get-in reitti [:reittipiste :sijainti :epsg4326]) :lon :lat))

        reitti (-> reitti
                 (update-in [:reittipiste] dissoc :sijainti)
                 (assoc-in [:reittipiste :koodinaatit] koordinaatit-3067))
        reitti (if lisaa-epsg-4326-koordinaatit?
                 (assoc-in reitti [:reittipiste :koordinaatit-4326] koordinaatit-4326)
                 reitti)]
    reitti))

(defn rakenna-reittipiste-tehtavat [reitti]
  (let [tehtavat (:tehtavat (:reittipiste reitti))
        tehtavat (map
                   #(-> %
                      (assoc-in [:tehtava :id] (:toimenpidekoodi %))
                      (dissoc :toimenpidekoodi))
                   tehtavat)
        reitti (-> reitti
                 (update-in [:reittipiste] dissoc :tehtavat)
                 (assoc-in [:reittipiste :tehtavat] tehtavat))]
    reitti))

(defn rakenna-reittipiste-materiaalit [reitti materiaalikoodit]
  (let [materiaalit (:materiaalit (:reittipiste reitti))
        materiaalit (map
                      (fn [materiaali]
                        (let [yksikko (:yksikko (first (filter (comp #{(:materiaalikoodi materiaali)} :id) materiaalikoodit)))
                              maara (:maara materiaali)]
                          (-> materiaali
                            (dissoc :maara)
                            (assoc-in [:maara :yksikko] yksikko)
                            (assoc-in [:maara :maara] maara))))
                      materiaalit)
        reitti (assoc reitti :materiaalit materiaalit)]
    reitti))

(defn palauta-toteumat
  "Haetaan toteumat annettujen alku- ja loppuajan puitteissa.
  koordinaattimuutos-parametrilla voidaan hakea lisäksi reittipisteet EPSG:4326-muodossa."
  [db {:keys [alkuaika loppuaika koordinaattimuutos] :as parametrit} kayttaja]
  (tarkista-toteumahaun-parametrit parametrit)
  (let [;; Materiaalikoodeja ei ole montaa, mutta niitä on vaikea yhdistää tietokantalauseeseen tehokkaasti
        ;; joten hoidetaan se koodilla
        materiaalikoodit (materiaalit-kyselyt/hae-materiaalikoodit db)
        ;; Haetaan reittitoteumat tietokannasta
        toteumat (toteuma-kyselyt/hae-reittitoteumat-analytiikalle db {:alkuaika alkuaika
                                                                       :loppuaika loppuaika
                                                                       :koordinaattimuutos koordinaattimuutos})
        toteumat (->> toteumat
                   (map (fn [toteuma]
                           (-> toteuma
                             (update :reitti konversio/jsonb->clojuremap)
                             (update :toteumatehtavat konversio/jsonb->clojuremap)
                             (update :toteumamateriaalit konversio/jsonb->clojuremap))))
                   (map #(update % :toteumatehtavat
                            (fn [rivit]
                              (keep
                                (fn [r]
                                  (-> r
                                    (clojure.set/rename-keys db-tehtavat->avaimet)
                                    (konversio/alaviiva->rakenne)))
                                rivit))))
                   (map #(update % :toteumamateriaalit
                            (fn [rivit]
                              (keep
                                (fn [r]
                                  (when (not (nil? (:f1 r))) ;; Varmista että Left joinilla haettuja rivejä on
                                    (-> r
                                      (clojure.set/rename-keys db-materiaalit->avaimet)
                                      (konversio/alaviiva->rakenne))))
                                rivit))))
                   (map #(clojure.set/rename-keys % {:toteumamateriaalit :toteuma_materiaalit
                                                      :toteumatehtavat :toteuma_tehtavat}))
                   (map #(update % :reitti
                            (fn [rivit]
                              (keep
                                (fn [r]
                                  (let [r
                                        (when (not (nil? (:f1 r))) ;; Varmista että Left joinilla haettuja rivejä on
                                          (clojure.set/rename-keys r db-reitti->avaimet))
                                        ;; Muokkaa reittipisteen nimet oikein
                                        r (-> r
                                            (konversio/alaviiva->rakenne)
                                            (rakenna-reittipiste-sijainti koordinaattimuutos)
                                            (rakenna-reittipiste-tehtavat)
                                            (rakenna-reittipiste-materiaalit materiaalikoodit))]
                                    r))
                                rivit)))))
        toteumat {:reittitoteumat
                  (map (fn [toteuma]
                          (konversio/alaviiva->rakenne toteuma))
                    toteumat)}]
    toteumat))

(defn palauta-materiaalit
  "Haetaan materiaalit ja palautetaan ne json muodossa"
  [db {:keys [alkuaika loppuaika koordinaattimuutos] :as parametrit} kayttaja]
  (let [materiaalikoodit (materiaalit-kyselyt/listaa-materiaalikoodit db)
        materiaaliluokat (materiaalit-kyselyt/hae-materiaaliluokat db)
        vastaus {:materiaalikoodit materiaalikoodit
                 :materiaaliluokat materiaaliluokat}
        _ (println "analytiikka :: palauta-materiaalit :: vastaus" vastaus)]
    vastaus))

(defn palauta-tehtavat
  "Haetaan tehtävät ja tehtäväryhmät ja palautetaan ne json muodossa"
  [db {:keys [alkuaika loppuaika koordinaattimuutos] :as parametrit} kayttaja]
  (let [tehtavat (toimenpidekoodi-kyselyt/listaa-tehtavat db)
        tehtavat (map
                   #(update % :hinnoittelu konv/pgarray->vector)
                   tehtavat)
        tehtavaryhmat (toimenpidekoodi-kyselyt/listaa-tehtavaryhmat db)
        vastaus {:tehtavat tehtavat
                 :tehtavaryhmat tehtavaryhmat}
        _ (println "analytiikka :: palauta-tehtavat :: vastaus" vastaus)]
    vastaus))

(defn palauta-urakat
  "Haetaan urakat ja palautetaan ne json muodossa"
  [db {:keys [alkuaika loppuaika koordinaattimuutos] :as parametrit} kayttaja]
  (let [urakat (urakat-kyselyt/listaa-urakat-analytiikalle db)
        vastaus {:urakat urakat}
        _ (println "analytiikka :: palauta-urakat :: vastaus" vastaus)]
    vastaus))

(defn palauta-organisaatiot
  "Haetaan urakat ja palautetaan ne json muodossa"
  [db {:keys [alkuaika loppuaika koordinaattimuutos] :as parametrit} kayttaja]
  (let [organisaatiot (organisaatiot-kyselyt/listaa-organisaatiot-analytiikalle db)
        vastaus {:organisaatiot organisaatiot}
        _ (println "analytiikka :: palauta-organisaatiot :: vastaus" vastaus)]
    vastaus))

(defrecord Analytiikka []
  component/Lifecycle
  (start [{http :http-palvelin db :db-replica integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :analytiikka-toteumat
      (GET "/api/analytiikka/toteumat/:alkuaika/:loppuaika" request
        (kasittele-kevyesti-get-kutsu db integraatioloki
          :analytiikka-hae-toteumat request
          (fn [parametrit kayttaja db]
            (palauta-toteumat db parametrit kayttaja))
          ;; Tarkista sallitaanko admin käyttälle API:en käyttöoikeus
          (not (ominaisuus-kaytossa? :toteumatyokalu)))))
    (julkaise-reitti
      http :analytiikka-materiaalit
      (GET "/api/analytiikka/materiaalit" request
        (kasittele-kevyesti-get-kutsu db integraatioloki
          :analytiikka-hae-materiaalikoodit request
          (fn [parametrit kayttaja db]
            (palauta-materiaalit db parametrit kayttaja))
          ;; Tarkista sallitaanko admin käyttälle API:en käyttöoikeus
          (not (ominaisuus-kaytossa? :toteumatyokalu)))))
    (julkaise-reitti
      http :analytiikka-tehtavat
      (GET "/api/analytiikka/tehtavat" request
        (kasittele-kevyesti-get-kutsu db integraatioloki
          :analytiikka-hae-tehtavat request
          (fn [parametrit kayttaja db]
            (palauta-tehtavat db parametrit kayttaja))
          ;; Tarkista sallitaanko admin käyttälle API:en käyttöoikeus
          (not (ominaisuus-kaytossa? :toteumatyokalu)))))
    (julkaise-reitti
      http :analytiikka-urakat
      (GET "/api/analytiikka/urakat" request
        (kasittele-kevyesti-get-kutsu db integraatioloki
          :analytiikka-hae-urakat request
          (fn [parametrit kayttaja db]
            (palauta-urakat db parametrit kayttaja))
          ;; Tarkista sallitaanko admin käyttälle API:en käyttöoikeus
          (not (ominaisuus-kaytossa? :toteumatyokalu)))))
    (julkaise-reitti
      http :analytiikka-organisaatiot
      (GET "/api/analytiikka/organisaatiot" request
        (kasittele-kevyesti-get-kutsu db integraatioloki
          :analytiikka-hae-organisaatiot request
          (fn [parametrit kayttaja db]
            (palauta-organisaatiot db parametrit kayttaja))
          ;; Tarkista sallitaanko admin käyttälle API:en käyttöoikeus
          (not (ominaisuus-kaytossa? :toteumatyokalu)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
      :analytiikka-toteumat
      :analytiikka-materiaalit
      :analytiikka-tehtavat
      :analytiikka-urakat
      :analytiikka-organisaatiot)
    this))
