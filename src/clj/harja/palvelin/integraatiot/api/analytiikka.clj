(ns harja.palvelin.integraatiot.api.analytiikka
  "Analytiikkaportaalille endpointit"
  (:require [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as s]
            [compojure.core :refer [POST GET DELETE]]
            [clojure.string :refer [join]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-get-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.toteumat :as toteuma-kyselyt]
            [harja.kyselyt.materiaalit :as materiaalit-kyselyt]
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
   :f3 :reittipiste_sijainti
   :f4 :reittipiste_materiaalit})

(defn rakenna-reittipiste-sijainti
  "Reittipisteen sijainnin tiedot tulevat row_to_json funktion käytön vuoksi tekstimuodossa, joten
  niiden käsittely koordinaattimuotoon on monimutkaista."
  [reitti]
  (let [sijainti (:sijainti (:reittipiste reitti))
        sijainnit (when sijainti (str/split sijainti #","))
        koordinaatit (when sijainnit {:x (str/replace (first sijainnit) #"\(" "")
                                      :y (str/replace (second sijainnit) #"\)" "")})
        reitti (-> reitti
                      (update-in [:reittipiste] dissoc :sijainti)
                      (assoc-in [:reittipiste :koodinaatit] koordinaatit))]
    reitti))

(defn rakenna-reittipiste-tehtavat [reitti]
  (let [tehtavat (:tehtavat (:reittipiste reitti))
        tehtavat (mapv
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
        materiaalit (mapv
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
  "Haetaan toteumat annettujen alku- ja loppuajan puitteissa."
  [db {:keys [alkuaika loppuaika] :as parametrit} kayttaja]
  (tarkista-toteumahaun-parametrit parametrit)
  (let [;; Materiaalikoodeja ei ole montaa, mutta niitä on vaikea yhdistää tietokantalauseeseen tehokkaasti
        ;; joten hoidetaan se koodilla
        materiaalikoodit (materiaalit-kyselyt/hae-materiaalikoodit db)
        ;; Haetaan reittitoteumat tietokannasta
        toteumat (toteuma-kyselyt/hae-reittitoteumat-analytiikalle db {:alkuaika alkuaika
                                                                       :loppuaika loppuaika} )
        toteumat (->> toteumat
                   ;; Muuta :f1 tyyppiset kolumnit oikean nimisiksi
                   (mapv #(update % :toteumatehtavat konversio/jsonb->clojuremap))
                   (mapv #(update % :toteumamateriaalit konversio/jsonb->clojuremap))
                   (mapv #(update % :reitti konversio/jsonb->clojuremap))
                   (mapv #(update % :toteumatehtavat
                            (fn [rivit]
                              (keep
                                (fn [r]
                                  (-> r
                                    (clojure.set/rename-keys db-tehtavat->avaimet)
                                    (konversio/alaviiva->rakenne)))
                                rivit))))
                   (mapv #(update % :toteumamateriaalit
                            (fn [rivit]
                              (keep
                                (fn [r]
                                  (when (not (nil? (:f1 r))) ;; Varmista että Left joinilla haettuja rivejä on
                                    (-> r
                                      (clojure.set/rename-keys db-materiaalit->avaimet)
                                      (konversio/alaviiva->rakenne))))
                                rivit))))
                   (mapv #(clojure.set/rename-keys % {:toteumamateriaalit :toteuma_materiaalit
                                                      :toteumatehtavat :toteuma_tehtavat}))
                   (mapv #(update % :reitti
                            (fn [rivit]
                              (keep
                                (fn [r]
                                  (let [r
                                        (when (not (nil? (:f1 r))) ;; Varmista että Left joinilla haettuja rivejä on
                                          (clojure.set/rename-keys r db-reitti->avaimet))
                                        ;; Muokkaa reittipisteen nimet oikein
                                        r (-> r
                                            (konversio/alaviiva->rakenne)
                                            (rakenna-reittipiste-sijainti)
                                            (rakenna-reittipiste-tehtavat)
                                            (rakenna-reittipiste-materiaalit materiaalikoodit))]
                                    r))
                                rivit)))))

        toteumat {:reittitoteumat
                  (mapv (fn [toteuma]
                          (konversio/alaviiva->rakenne toteuma))
                    toteumat)}]
    toteumat))

(defrecord Analytiikka []
  component/Lifecycle
  (start [{http :http-palvelin db :db-replica integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :analytiikka-toteumat
      (GET "/api/analytiikka/toteumat/:alkuaika/:loppuaika" request
        (kasittele-get-kutsu db integraatioloki
          :analytiikka-hae-toteumat request
          json-skeemat/analytiikkaportaali-toteuma-vastaus
          (fn [parametrit kayttaja db]
            (palauta-toteumat db parametrit kayttaja))
          true)))
          ;; Tarkista sallitaanko admin käyttälle API:en käyttöoikeus
          (if-not (ominaisuus-kaytossa? :salli-hallinnan-apin-kaytto)
            false
            true))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :analytiikka-toteumat)
    this))
