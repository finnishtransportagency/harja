(ns harja.palvelin.integraatiot.api.analytiikka
  "Analytiikkaportaalille endpointit"
  (:require [clojure.set :as set]
            [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [compojure.core :refer [GET]]
            [compojure.core :refer :all]
            [compojure.route :refer :all]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yllapitokohteet-yleiset]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kevyesti-get-kutsu kasittele-get-kutsu]]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.toteumat :as toteuma-kyselyt]
            [harja.kyselyt.materiaalit :as materiaalit-kyselyt]
            [harja.kyselyt.toimenpidekoodit :as toimenpidekoodi-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.organisaatiot :as organisaatiot-kyselyt]
            [harja.kyselyt.tehtavamaarat :as tehtavamaarat-kyselyt]
            [harja.kyselyt.paikkaus :as paikkaus-kyselyt]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-kyselyt]
            [harja.kyselyt.turvallisuuspoikkeamat :as turvallisuuspoikkeamat]
            [harja.kyselyt.paallystys-kyselyt :as paallystys-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaravaus-kyselyt]
            [harja.palvelin.integraatiot.api.tyokalut.parametrit :as parametrit]
            [harja.palvelin.integraatiot.api.sanomat.analytiikka-sanomat :as analytiikka-sanomat]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat])
  (:import (java.text SimpleDateFormat))
  (:use [slingshot.slingshot :only [throw+]]))

(s/def ::alkuvuosi #(and (or
                           (string? %)
                           (integer? %))
                      (= (count (str %)) 4)
                      (if (string? %)
                        (number? (Integer/parseInt %))
                        (number? %))
                      (if (string? %)
                        (pos? (Integer/parseInt %))
                        (pos? %))))
(s/def ::loppuvuosi #(and (or
                            (string? %)
                            (integer? %))
                       (= (count (str %)) 4)
                       (if (string? %)
                         (number? (Integer/parseInt %))
                         (number? %))
                       (if (string? %)
                         (pos? (Integer/parseInt %))
                         (pos? %))))
(s/def ::urakka-id #(and (string? %) (not (nil? (konversio/konvertoi->int %))) (pos? (konversio/konvertoi->int %))))
(s/def ::alkuaika #(and (string? %) (>= (count %) 20) (or
                                                        (inst? (.parse (SimpleDateFormat. parametrit/pvm-aika-muoto) %))
                                                        (inst? (.parse (SimpleDateFormat. parametrit/pvm-aika-muotoZ) %)))))
(s/def ::loppuaika #(and (string? %) (>= (count %) 20) (or
                                                         (inst? (.parse (SimpleDateFormat. parametrit/pvm-aika-muoto) %))
                                                         (inst? (.parse (SimpleDateFormat. parametrit/pvm-aika-muotoZ) %)))))

(defn- tarkista-haun-parametrit [parametrit rajoita]
  (try
    (s/valid? ::loppuaika (:loppuaika parametrit))
    (s/valid? ::alkuaika (:alkuaika parametrit))
    (parametrivalidointi/tarkista-parametrit
      parametrit
      {:alkuaika "Alkuaika puuttuu"
       :loppuaika "Loppuaika puuttuu"})
    (catch Exception e
      (log/error "Virhe Analytiikka-api kutsussa:" e)
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+puutteelliset-parametrit+
                          :viesti "Poikkeus annetuissa parametreissa. Anna päivämäärät muodossa: yyyy-MM-dd'T'HH:mm:ss esim: 2005-01-01T00:00:00+03"}]})))
  (when (not (s/valid? ::alkuaika (:alkuaika parametrit)))
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Alkuaika väärässä muodossa: %s Anna muodossa: yyyy-MM-dd'T'HH:mm:ss esim: 2005-01-01T00:00:00+03" (:alkuaika parametrit))}))
  (when (not (s/valid? ::loppuaika (:loppuaika parametrit)))
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Loppuaika väärässä muodossa: %s Anna muodossa: yyyy-MM-dd'T'HH:mm:ss esim: 2005-01-02T00:00:00+03" (:loppuaika parametrit))}))

  ;; Rajoitetaan toteumien haku yhteen vuorokauteen, muuten meillä voi mennä tuotannosta levyt tukkoon
  (when rajoita
    (let [alkuaika-pvm (.parse (SimpleDateFormat. parametrit/pvm-aika-muoto) (:alkuaika parametrit))
          loppuaika-pvm (.parse (SimpleDateFormat. parametrit/pvm-aika-muoto) (:loppuaika parametrit))
          aikavali-sekunteina (pvm/aikavali-sekuntteina alkuaika-pvm loppuaika-pvm)
          syotetty-aikavali-tunteina-str (str (int (/ aikavali-sekunteina 60 60)))
          paiva-sekunteina 90000] ;; Käytetään 25 tuntia
      ;; Jos pyydetty aikaväli ylittää 25 tuntia, palautetaan virhe
      (when (> aikavali-sekunteina paiva-sekunteina)
        (virheet/heita-viallinen-apikutsu-poikkeus
          {:koodi virheet/+puutteelliset-parametrit+
           :viesti (format "Aikaväli ylittää sallitun rajan. Syötetty aikaväli: %s tuntia. Sallittu aikaväli max. 24 tuntia." syotetty-aikavali-tunteina-str)})))))

(def db-tehtavat->avaimet
  {:f1 :id
   :f2 :maara_maara
   :f3 :maara_yksikko
   :f4 :selite
   :f5 :tehtavaryhmaid})

(def db-materiaalit->avaimet
  {:f1 :id
   :f2 :materiaali
   :f3 :maara_maara
   :f4 :maara_yksikko})

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
                 (assoc-in [:reittipiste :koordinaatit] koordinaatit-3067))
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

(defn- poista-reittipistetaso
  "Tiivistetään json formaattia poistamalla reittipiste sen jälkeen, kun sen alle on saatu koottua kaikki tiedot."
  [reitti]
  (cond-> reitti
    true (assoc :aika (get-in reitti [:reittipiste :aika]))
    true (assoc :tehtavat (get-in reitti [:reittipiste :tehtavat]))
    true (assoc :koordinaatit (get-in reitti [:reittipiste :koordinaatit]))
    (not (nil? (get-in reitti [:reittipiste :koordinaatit-4326]))) (assoc :koordinaatit-4326 (get-in reitti [:reittipiste :koordinaatit-4326]))
    true (assoc :materiaalit (get-in reitti [:reittipiste :materiaalit]))
    true (dissoc :reittipiste)))

;; Meillä ei ole tietoa, että minkä kokoinen (MB) yksi toteuma on json muodossa. Joten joudutaan käyttämään hanska-arviota.
;; Aineistot on isoja ja se pitäisi generoida valmiiksi, jos haluaisi tietää tarkan summan. Joten hanska-arvio on
;; nopea, mutta vain suuntaa antava. Tällä hetkellä se antaa n. 20% liian suuria kokoja. Mittausten mukaan n. 0.0044 alkaa olla lälhellä totuutta.
(def arvioitu-toteuman-koko 0.006)

(defn palauta-toteumat
  "Haetaan toteumat annettujen alku- ja loppuajan puitteissa.
  koordinaattimuutos-parametrilla voidaan hakea lisäksi reittipisteet EPSG:4326-muodossa."
  [db {:keys [alkuaika loppuaika koordinaattimuutos koko] :as parametrit} kayttaja]
  (log/info "Analytiikka API, toteumien haku, parametrit: " (pr-str parametrit))
  (tarkista-haun-parametrit parametrit true)
  (let [;; Koordinaattimuutos on oltava boolean
        kmuutos (= "true" koordinaattimuutos)
        ;; Koko parametri on oltava Integer - Jos sitä ei ole annettu, niin anna valtava koko, ettei se rajoita mitään
        koko (cond
               (and koko (not (str/includes? koko "."))) (Integer/parseInt koko)
               (and koko (str/includes? koko ".")) (Double/parseDouble koko)
               :default 1000)
        ;; Materiaalikoodeja ei ole montaa, mutta niitä on vaikea yhdistää tietokantalauseeseen tehokkaasti
        ;; joten hoidetaan se koodilla
        materiaalikoodit (materiaalit-kyselyt/hae-materiaalikoodit db)
        ;; Haetaan reittitoteumat tietokannasta
        alkudb (System/currentTimeMillis)
        toteumat (toteuma-kyselyt/hae-reittitoteumat-analytiikalle db {:alkuaika alkuaika
                                                                       :loppuaika loppuaika
                                                                       :koordinaattimuutos kmuutos})
        maara (count toteumat)
        loydetyn-aineiston-koko (* (count toteumat) arvioitu-toteuman-koko)
        loppudb (System/currentTimeMillis)
        _ (log/info "Analytiikka-toteumat db haku" (- loppudb alkudb) " ms. Toteumamäärä: " maara)
        _ (when (>= maara 100000)
            (log/info "Analytiikka-toteumat :: liian suuri aineisto:" maara "kpl"))

        ;; Päätellään aineistosta myös megabyte määräinen koko ja verrataan sitä parametrina saatuun
        _ (when (>= loydetyn-aineiston-koko koko)
            (log/info "Analytiikka-toteumat :: liian suuri aineisto (mb):" loydetyn-aineiston-koko " > " koko))

        ;; Toteumien kutsu ei käytä streamiä, joten on riskinä, että muisti loppuu kesken. Joten rajoitetaan määrää
        toteumat (when (and (< maara 100000) (< loydetyn-aineiston-koko koko))
                   (->> toteumat
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
                                         ;; Tietokanta palauttaa reittipisteen, joka poistetaan datan formatoinnin jälkeen
                                         r (-> r
                                             (konversio/alaviiva->rakenne)
                                             (rakenna-reittipiste-sijainti koordinaattimuutos)
                                             (rakenna-reittipiste-tehtavat)
                                             (rakenna-reittipiste-materiaalit materiaalikoodit)
                                             (poista-reittipistetaso))]
                                     r))
                                 rivit))))))
        toteumat (when (and (< koko 100000) (< loydetyn-aineiston-koko koko))
                   {:reittitoteumat
                    (map (fn [toteuma]
                           (konversio/alaviiva->rakenne toteuma))
                      toteumat)})]
    toteumat))

(defn palauta-materiaalit
  "Haetaan materiaalit ja palautetaan ne json muodossa"
  [db _ _]
  (log/info "Analytiikka API, materiaalien haku")
  (let [materiaalikoodit (materiaalit-kyselyt/listaa-materiaalikoodit db)
        materiaaliluokat (materiaalit-kyselyt/hae-materiaaliluokat db)
        vastaus {:materiaalikoodit materiaalikoodit
                 :materiaaliluokat materiaaliluokat}]
    vastaus))

(defn palauta-tehtavat
  "Haetaan tehtävät ja tehtäväryhmät ja palautetaan ne json muodossa"
  [db _ _]
  (log/info "Analytiikka API, tehtävien haku")
  (let [tehtavat (toimenpidekoodi-kyselyt/listaa-tehtavat db)
        tehtavat (map
                   #(update % :hinnoittelu konversio/pgarray->vector)
                   tehtavat)
        tehtavaryhmat (toimenpidekoodi-kyselyt/listaa-tehtavaryhmat db)
        vastaus {:tehtavat tehtavat
                 :tehtavaryhmat tehtavaryhmat}]
    vastaus))

(defn palauta-toimenpiteet
  "Haetaan toimenpiteet (taso 3) ja palautetaan ne json muodossa"
  [db _ _]
  (log/info "Analytiikka API, toimenpiteet haku")
  (let [toimenpiteet (toimenpidekoodi-kyselyt/listaa-toimenpiteet-analytiikalle db)
        ;; Wrapatään jokainen rivi "toimenpide" -nimiseen avaimen alle
        toimenpiteet (map (fn [t]
                            {:toimenpide t})
                          toimenpiteet)]
    ;; Palautetaan kaikki toimenpiteet toimenpiteet avaimen alla
    {:toimenpiteet toimenpiteet}))

(defn palauta-rahavaraukset
  "Haetaan rahavaraukset ja niiden tehtävät ja palautetaan ne json muodossa kysyjälle. Tosin kutsukäsittelijä muokkaa
  tämän aineiston jsoniksi."
  [db _ _]
  (log/info "Analytiikka API, rahavaraukset haku")
  (let [rahavaraukset (rahavaravaus-kyselyt/listaa-rahavaraukset-analytiikalle db)
        ;; Wrapatään jokainen rivi "rahavaraus" -nimiseen avaimen alle
        rahavaraukset (map (fn [r]
                            {:rahavaraus (assoc r :tehtavat (konversio/pgarray->vector (:tehtavat r)))})
                        rahavaraukset)]
    ;; Palautetaan kaikki rahavaraukset rahavaraukset avaimen alla
    {:rahavaraukset rahavaraukset}))

(defn palauta-urakat
  "Haetaan urakat ja palautetaan ne json muodossa"
  [db _ _]
  (log/info "Analytiikka API, urakoiden haku")
  (let [urakat (urakat-kyselyt/listaa-kaikki-urakat-analytiikalle db)
        vastaus {:urakat urakat}]
    vastaus))

(defn palauta-organisaatiot
  "Haetaan urakat ja palautetaan ne json muodossa"
  [db _ _]
  (log/info "Analytiikka API, organisaatioiden haku")
  (let [organisaatiot (organisaatiot-kyselyt/listaa-organisaatiot-analytiikalle db)
        vastaus {:organisaatiot organisaatiot}]
    vastaus))

(defn- tarkista-parametrit-urakka-aikavali [parametrit]
  (let [pakolliset {:urakka-id "Urakka-id puuttuu"}
        alkuvuosi (konversio/konvertoi->int (:alkuvuosi parametrit))
        loppuvuosi (konversio/konvertoi->int (:loppuvuosi parametrit))]
    (parametrivalidointi/tarkista-parametrit parametrit pakolliset)
    (when (or (and
                (not (nil? (:alkuvuosi parametrit)))
                (not (s/valid? ::alkuvuosi (:alkuvuosi parametrit))))
            (and
              (not (nil? (:alkuvuosi parametrit)))
              (and (not (nil? alkuvuosi)) (not (nil? loppuvuosi)) (> alkuvuosi loppuvuosi))))
      (virheet/heita-viallinen-apikutsu-poikkeus
        {:koodi virheet/+puutteelliset-parametrit+
         :viesti (format "Alkuvuodessa: '%s' virhe. Anna muodossa: 2015 ja varmista, että se on pienempi, kuin loppuvuosi." (:alkuvuosi parametrit))}))
    (when (or (and
                (not (nil? (:loppuvuosi parametrit)))
                (not (s/valid? ::loppuvuosi (:loppuvuosi parametrit))))
            (and
              (not (nil? (:alkuvuosi parametrit)))
              (not (nil? (:loppuvuosi parametrit)))
              (> alkuvuosi loppuvuosi)))
      (virheet/heita-viallinen-apikutsu-poikkeus
        {:koodi virheet/+puutteelliset-parametrit+
         :viesti (format "Loppuvuodessa: '%s' virhe. Anna muodossa: 2023 ja varmista, että se on suurempi, kuin alkuvuosi" (:loppuvuosi parametrit))}))
    (when (or (nil? (:urakka-id parametrit)) (not (s/valid? ::urakka-id (:urakka-id parametrit))))
      (virheet/heita-viallinen-apikutsu-poikkeus
        {:koodi virheet/+puutteelliset-parametrit+
         :viesti (format "Urakka-id väärässä muodossa: '%s' Anna muodossa: 1234" (:urakka-id parametrit))}))))

(defn palauta-urakan-suunnitellut-materiaalimaarat
  "Palautetaan suunnitellut materiaalimaarat hoitovuosittain annetulle urakalle."
  [db {:keys [alkuvuosi loppuvuosi urakka-id] :as parametrit} kayttaja]
  (log/info "Analytiikka API palauta-urakan-suunnitellut-materiaalimaarat :: parametrit" (pr-str parametrit))
  (tarkista-parametrit-urakka-aikavali parametrit)
  (let [alkuvuosi (when-not (nil? alkuvuosi)
                    (konversio/konvertoi->int alkuvuosi))
        loppuvuosi (when-not (nil? loppuvuosi)
                     (konversio/konvertoi->int loppuvuosi))
        urakka-id (if (integer? urakka-id)
                    urakka-id
                    (konversio/konvertoi->int urakka-id))
        ;; Haetaan urakan tiedoista aikaväli, jolle suunnittelutiedot haetaan
        urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        ;; Vaadi urakan olemassa olo
        _ (when (nil? urakan-tiedot)
            (throw+ {:type virheet/+viallinen-kutsu+
                     :virheet [{:koodi virheet/+puutteelliset-parametrit+
                                :viesti (str "Urakkaa id:llä: " urakka-id " ei ole olemassa.")}]}))
        ;; Jos parametrina on annettu vuodet, niin käytetään niitä
        hoitokaudet (range (or alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot)))
                      (inc (or loppuvuosi (dec (pvm/vuosi (:loppupvm urakan-tiedot))))))

        ;; Alueurakoille saadaan suunnitellut materiaalit materiaalin_kaytto taulusta
        suunniteltu-materialimaara (materiaalit-kyselyt/hae-urakan-suunniteltu-materiaalin-kaytto db urakka-id)
        ;; Kootaan tulokset vuosittain
        vuosittainen-suunniteltu-materialimaara (reduce (fn [tulos vuosi]
                                                          (let [vuoden-materiaalit (filter
                                                                                     #(when (= vuosi (:hoitokauden-alkuvuosi %))
                                                                                        %)
                                                                                     suunniteltu-materialimaara)
                                                                tama-vuosi {:hoitokauden-alkuvuosi vuosi
                                                                            :suunnitellut-materiaalit vuoden-materiaalit}]
                                                            (conj tulos tama-vuosi)))
                                                  [] hoitokaudet)

        ;; MH-urakoiden suunnitellut materiaalitiedot tulee urakka_tehtavamaarat taulusta, mutta HJU urakoille on suunniteltu niitä myös materiaalin_kayttotauluun
        ;; Joten molempia hakuja on käytettävä.
        suunniteltu-tehtava-materiaalimaara (tehtavamaarat-kyselyt/hae-urakan-suunniteltu-materiaalin-kaytto-tehtavamaarista db urakka-id)

        vuosittainen-suunniteltu-tehtava-materiaalimaara (reduce (fn [tulos vuosi]
                                                                   (let [vuoden-suunnitelmat (filter #(when (= vuosi (:hoitokauden-alkuvuosi %))
                                                                                                        %) suunniteltu-tehtava-materiaalimaara)
                                                                         tama-vuosi {:hoitokauden-alkuvuosi vuosi
                                                                                     :suunnitellut-materiaalit vuoden-suunnitelmat}]
                                                                     (conj tulos tama-vuosi)))
                                                           [] hoitokaudet)

        ;; Suolan suunniteltu käyttö
        ;; Alueurakoille se haetaan suolasakko -taulusta - MH-urakoille suolan suunnittelu tulee muiden materiaalien mukana urakka_tehtavamaara -taulusta
        alueurakan-suolasuunnitelma (suolarajoitus-kyselyt/hae-suunniteltu-suolan-kaytto-hoitovuosittain-alueurakalle db {:urakka-id urakka-id})
        ;; Määritellään suolasta niin tarkat tiedot, kuin voidaan ilman, että määritellään sitä materiaaliksi, koska suolaus on laajempi materiaaliluokka
        suolamateriaali (merge (first (materiaalit-kyselyt/hae-talvisuolan-materiaaliluokka db))
                          {:materiaali_id nil
                           :materiaali nil
                           :materiaali_yksikko nil
                           :materiaali_tyyppi nil})

        tulos (reduce (fn [tulos vuosi]
                        (let [vuoden-tehtavat-mat (some #(when (= vuosi (:hoitokauden-alkuvuosi %))
                                                           %) vuosittainen-suunniteltu-tehtava-materiaalimaara)
                              vuoden-kaytto-materiaalit (some #(when (= vuosi (:hoitokauden-alkuvuosi %))
                                                                 %) vuosittainen-suunniteltu-materialimaara)
                              ;; Loopataan kaikki urakka_tehtavamaaran materiaalit yhdelle vuodelle läpi ja
                              ;; etsitään vastaavaa materiaalia materiaalin_kaytto -taulusta saadusta materiaalilistasta
                              yhd-materiaalit (reduce (fn [tulos tehtava_mat]
                                                        (let [sama-materiaali (some #(when (=
                                                                                             (:materiaali_id tehtava_mat)
                                                                                             (:materiaali_id %))
                                                                                       %)
                                                                                (:suunnitellut-materiaalit vuoden-kaytto-materiaalit))
                                                              uusi-maara (when sama-materiaali
                                                                           (+ (or (:maara sama-materiaali) 0) (or (:maara tehtava_mat) 0)))
                                                              uusi-tehtava-mat (if uusi-maara
                                                                                 (assoc tehtava_mat :maara uusi-maara)
                                                                                 tehtava_mat)]
                                                          (conj tulos uusi-tehtava-mat)))
                                                [] (:suunnitellut-materiaalit vuoden-tehtavat-mat))
                              loput-materiaalit (keep
                                                  (fn [vkm]
                                                    (let [onko-jo-lisatty? (some #(when (=
                                                                                          (:materiaali_id vkm)
                                                                                          (:materiaali_id %))
                                                                                    vkm)
                                                                             yhd-materiaalit)]
                                                      (when-not onko-jo-lisatty? vkm)))
                                                  (:suunnitellut-materiaalit vuoden-kaytto-materiaalit))

                              vuoden-suolat (some #(when (= vuosi (:hoitokauden-alkuvuosi %))
                                                     (merge suolamateriaali
                                                       {:maara (:talvisuolaraja %)}))
                                              alueurakan-suolasuunnitelma)
                              vuoden-suolat (if vuoden-suolat (conj [] vuoden-suolat) [])
                              lopulliset-yhd-materiaalit (concat yhd-materiaalit loput-materiaalit vuoden-suolat)
                              tama-vuosi {:hoitokauden-alkuvuosi vuosi
                                          :suunnitellut-materiaalit lopulliset-yhd-materiaalit}]
                          (conj tulos tama-vuosi)))
                [] hoitokaudet)]
    tulos))

(defn- tarkista-parametrit-aikavali [parametrit]
  (let [pakolliset {:alkuvuosi "Alkuvuosi puuttuu"
                    :loppuvuosi "Lopppuvuosi puuttuu"}
        alkuvuosi (if (string? (:alkuvuosi parametrit))
                    (konversio/konvertoi->int (:alkuvuosi parametrit))
                    (:alkuvuosi parametrit))
        loppuvuosi (if (string? (:loppuvuosi parametrit))
                     (konversio/konvertoi->int (:loppuvuosi parametrit))
                     (:loppuvuosi parametrit))]
    (parametrivalidointi/tarkista-parametrit parametrit pakolliset)
    (when (or
            (not (s/valid? ::alkuvuosi (:alkuvuosi parametrit)))
            (> alkuvuosi loppuvuosi))
      (virheet/heita-viallinen-apikutsu-poikkeus
        {:koodi virheet/+puutteelliset-parametrit+
         :viesti (format "Alkuvuodessa: '%s' virhe. Anna muodossa: 2015 ja varmista, että se on pienempi, kuin loppuvuosi." (:alkuvuosi parametrit))}))
    (when (or
            (not (s/valid? ::loppuvuosi (:loppuvuosi parametrit)))
            (> alkuvuosi loppuvuosi))
      (virheet/heita-viallinen-apikutsu-poikkeus
        {:koodi virheet/+puutteelliset-parametrit+
         :viesti (format "Loppuvuodessa: '%s' virhe. Anna muodossa: 2023 ja varmista, että se on suurempi, kuin alkuvuosi" (:loppuvuosi parametrit))}))))

(defn palauta-suunnitellut-materiaalimaarat
  "Palautetaan suunnitellut materiaalimaarat hoitovuosittain."
  [db {:keys [alkuvuosi loppuvuosi] :as parametrit} kayttaja]
  (log/info "Analytiikka API palauta-suunnitellut-materiaalimaarat :: parametrit" (pr-str parametrit))
  (tarkista-parametrit-aikavali parametrit)
  (let [;; Haetaan vain ne urakat, jotka ovat olleet voimassa valittuna vuosina
        urakat (urakat-kyselyt/listaa-urakat-analytiikalle-hoitovuosittain db {:alkuvuosi alkuvuosi
                                                                               :loppuvuosi loppuvuosi})
        suunnitellut-materiaalit (mapv (fn [urakka]
                                         (let [;; Rajoitetaan urakalta haettavia tietoja urakan voimassaoloon
                                               min-vuosi (if (< (konversio/konvertoi->int alkuvuosi) (pvm/vuosi (:alkupvm urakka)))
                                                           (pvm/vuosi (:alkupvm urakka))
                                                           alkuvuosi)
                                               max-vuosi (if (> (konversio/konvertoi->int loppuvuosi) (pvm/vuosi (:loppupvm urakka)))
                                                           (pvm/vuosi (:loppupvm urakka))
                                                           loppuvuosi)]
                                           {:urakka (:nimi urakka)
                                            :urakka-id (:id urakka)
                                            :vuosittaiset-suunnitelmat
                                            (palauta-urakan-suunnitellut-materiaalimaarat db
                                              {:alkuvuosi min-vuosi
                                               :loppuvuosi max-vuosi
                                               ;; Validaation yksinkertaistamiseksi välitetään kaikki stringinä
                                               :urakka-id (str (:id urakka))}
                                              kayttaja)}))
                                   urakat)]
    suunnitellut-materiaalit))

(defn palauta-urakan-suunnitellut-tehtavamaarat
  "Palautetaan suunnitellut tehtavamaarat yhdelle urakalle."
  [db {:keys [alkuvuosi loppuvuosi urakka-id] :as parametrit} kayttaja]
  (log/info "Analytiikka API palauta-urakan-suunnitellut-tehtavamaarat :: parametrit" (pr-str parametrit))
  (tarkista-parametrit-urakka-aikavali parametrit)
  (let [urakka-id (if (integer? urakka-id)
                    urakka-id
                    (konversio/konvertoi->int urakka-id))
        ;; Haetaan urakan tiedoista aikaväli, jolle suunnittelutiedot haetaan
        urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        ;; Vaadi urakan olemassaolo
        _ (when (nil? urakan-tiedot)
            (throw+ {:type virheet/+viallinen-kutsu+
                     :virheet [{:koodi virheet/+puutteelliset-parametrit+
                                :viesti (str "Urakkaa id:llä: " urakka-id " ei ole olemassa.")}]}))

        alkuvuosi (when-not (nil? alkuvuosi)
                    (konversio/konvertoi->int alkuvuosi))
        loppuvuosi (when-not (nil? loppuvuosi)
                     (konversio/konvertoi->int loppuvuosi))

        alkupvm (if alkuvuosi
                  (konversio/sql-date (pvm/luo-pvm-dec-kk alkuvuosi 10 01))
                  (:alkupvm urakan-tiedot))
        loppupvm (if loppuvuosi
                   (konversio/sql-date (pvm/luo-pvm-dec-kk loppuvuosi 9 30))
                   (:loppupvm urakan-tiedot))
        hoitokaudet (range (or alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot)))
                      (inc (or loppuvuosi (pvm/vuosi (:loppupvm urakan-tiedot)))))

        ;; Urakan tyyppi vaikuttaa siihen, mihin tehtävien määrät suunnitellaan
        suunnitellut-tehtavat (if (= "teiden-hoito" (:tyyppi urakan-tiedot))
                                (tehtavamaarat-kyselyt/hae-mhurakan-suunnitellut-tehtavamaarat
                                  db
                                  {:urakka-id urakka-id
                                   :hoitokauden-alkuvuodet hoitokaudet})
                                (tehtavamaarat-kyselyt/hae-alueurakan-suunnitellut-tehtavamaarat
                                  db
                                  {:urakka-id urakka-id
                                   :alkupvm alkupvm
                                   :loppupvm loppupvm}))

        vuosittaiset-suunnittelut (reduce (fn [tulos vuosi]
                                            (let [vuoden-tehtavat (filter #(when (= vuosi (:hoitokauden-alkuvuosi %))
                                                                             %) suunnitellut-tehtavat)
                                                  tama-vuosi {:hoitokauden-alkuvuosi vuosi
                                                              :suunnitellut-tehtavat vuoden-tehtavat}]
                                              (conj tulos tama-vuosi)))
                                    [] hoitokaudet)]
    vuosittaiset-suunnittelut))

(defn palauta-suunnitellut-tehtavamaarat
  "Palautetaan suunnitellut tehtavamaarat hoitovuosittain."
  [db {:keys [alkuvuosi loppuvuosi] :as parametrit} kayttaja]
  (log/info "Analytiikka API palauta-suunnitellut-tehtavamaarat :: parametrit" (pr-str parametrit))
  (tarkista-parametrit-aikavali parametrit)
  (let [urakat (urakat-kyselyt/listaa-urakat-analytiikalle-hoitovuosittain db {:alkuvuosi alkuvuosi
                                                                               :loppuvuosi loppuvuosi})
        suunnitellut-tehtavat (mapv (fn [urakka]
                                      (let [;; Rajoitetaan urakalta haettavia tietoja urakan voimassaoloon
                                            min-vuosi (if (< (konversio/konvertoi->int alkuvuosi) (pvm/vuosi (:alkupvm urakka)))
                                                        (pvm/vuosi (:alkupvm urakka))
                                                        alkuvuosi)
                                            max-vuosi (if (> (konversio/konvertoi->int loppuvuosi) (pvm/vuosi (:loppupvm urakka)))
                                                        (pvm/vuosi (:loppupvm urakka))
                                                        loppuvuosi)]
                                        {:urakka (:nimi urakka)
                                         :urakka-id (:id urakka)
                                         :vuosittaiset-suunnitelmat
                                         (palauta-urakan-suunnitellut-tehtavamaarat db
                                           {:alkuvuosi min-vuosi
                                            :loppuvuosi max-vuosi
                                            ;; Validaation yksinkertaistamiseksi välitetään kaikki stringinä
                                            :urakka-id (str (:id urakka))}
                                           kayttaja)}))
                                urakat)]
    suunnitellut-tehtavat))

(defn hae-turvallisuuspoikkeamat [db {:keys [alkuaika loppuaika] :as parametrit} kayttaja]
  (log/info "Analytiikka API hae-turvallisuuspoikkeamat :: parametrit" (pr-str parametrit))
  (tarkista-haun-parametrit parametrit false)
  (let [turpot (turvallisuuspoikkeamat/hae-turvallisuuspoikkeamat-lahetettavaksi-analytiikalle db {:alku (pvm/rajapinta-str-aika->sql-timestamp alkuaika)
                                                                                                   :loppu (pvm/rajapinta-str-aika->sql-timestamp loppuaika)})
        ;; Konvertoidaan turpot sellaiseen muotoon, että ne voidaan kääntää kutsukäsittelyssä jsoniksi. Tässä vaiheessa ne ovat mäppeineä nimestään huolimatta
        json-turpot (map
                      #(analytiikka-sanomat/turvallisuuspoikkeamaviesti-json %)
                      (konversio/sarakkeet-vektoriin
                        (into [] turvallisuuspoikkeamat/turvallisuuspoikkeama-xf turpot)
                        {:korjaavatoimenpide :korjaavattoimenpiteet
                         :liite :liitteet
                         :kommentti :kommentit}))]
    {:turvallisuuspoikkeamat json-turpot}))

(defn hae-paallystysurakat [db {:keys [alkuaika loppuaika] :as parametrit}]
  (log/info "Analytiikka API paallystysurakat :: parametrit" (pr-str parametrit))
  (tarkista-haun-parametrit parametrit false)
  (let [paallystysurakat (urakat-kyselyt/hae-paallystysurakat-analytiikalle db {:alku (pvm/rajapinta-str-aika->sql-timestamp alkuaika)
                                                                                :loppu (pvm/rajapinta-str-aika->sql-timestamp loppuaika)})
        paallystysurakat (map #(-> % (set/rename-keys {:yhaid :yhaId
                                                       :harjaid :harjaId
                                                       :sampoid :sampotunnus})
                                 (konversio/array->vec :elyt)
                                 (konversio/array->vec :vuodet))
                           paallystysurakat)]
    {:paallystysurakat paallystysurakat}))

(defn- muodosta-paallystyskohdesanoma [kohde osien-pituudet]
  (let [pituus (tr-domain/laske-tien-pituus (osien-pituudet (:tr-numero kohde)) kohde)]
    (-> kohde
      (set/rename-keys {:yhaid :yhaId
                        :id :harjaId})
      (assoc :tierekisteriosoitevali {:karttapaivamaara (:karttapaivamaara kohde)
                                      :tienumero (:tr-numero kohde)
                                      :aosa (:tr-alkuosa kohde)
                                      :aet (:tr-alkuetaisyys kohde)
                                      :losa (:tr-loppuosa kohde)
                                      :let (:tr-loppuetaisyys kohde)})
      (assoc :pituus pituus)
      (dissoc :tr-numero :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys :karttapaivamaara))))

(defn- muodosta-alikohdesanoma [alikohde osien-pituudet]
  (-> alikohde
    (muodosta-paallystyskohdesanoma osien-pituudet)
    (assoc-in [:paallystystoimenpide :uusiPaallyste] (paallystys-ja-paikkaus/hae-apin-paallyste-koodilla (:uusi_paallyste alikohde)))
    (assoc-in [:paallystystoimenpide :paallystetyomenetelma] (paallystysilmoitus/tyomenetelman-nimi-koodilla (:tyomenetelma alikohde)))
    (assoc-in [:paallystystoimenpide :raekoko] (:raekoko alikohde))
    (assoc-in [:paallystystoimenpide :massamenekki] (:massamenekki alikohde))
    (assoc-in [:paallystystoimenpide :kokonaismassamaara] (:massamaara alikohde))
    (assoc-in [:tierekisteriosoitevali :kaista] (:tr-kaista alikohde))
    (assoc-in [:tierekisteriosoitevali :ajorata] (:tr-ajorata alikohde))
    (dissoc :tr-kaista :tr-ajorata :uusi_paallyste :tyomenetelma :raekoko :massamenekki :massamaara :yllapitokohde)))

(defn hae-paallystyskohteet [db {:keys [alkuaika loppuaika] :as parametrit}]
  (log/info "Analytiikka API paallystyskohteet :: parametrit" (pr-str parametrit))
  (tarkista-haun-parametrit parametrit false)
  (let [paallystyskohteet (paallystys-kyselyt/hae-paallystyskohteet-analytiikalle db
                            {:alku (pvm/rajapinta-str-aika->sql-timestamp alkuaika)
                             :loppu (pvm/rajapinta-str-aika->sql-timestamp loppuaika)})
        osien-pituudet (yllapitokohteet-yleiset/laske-osien-pituudet db paallystyskohteet)
        alikohteet (paallystys-kyselyt/hae-paallystyksen-alikohteet-analytiikalle db
                     {:alku (pvm/rajapinta-str-aika->sql-timestamp alkuaika)
                      :loppu (pvm/rajapinta-str-aika->sql-timestamp loppuaika)})
        paallystyskohteet (map (fn [kohde]
                                 (assoc (muodosta-paallystyskohdesanoma kohde osien-pituudet)
                                   :alikohteet (map #(muodosta-alikohdesanoma % osien-pituudet)
                                                 (filter #(= (:yllapitokohde %) (:id kohde)) alikohteet))))
                            paallystyskohteet)]
    {:paallystyskohteet paallystyskohteet}))

(defn hae-paallystyskohteiden-aikataulut [db {:keys [alkuaika loppuaika] :as parametrit}]
  (log/info "Analytiikka API paallystyskohteden aikataulut :: parametrit" (pr-str parametrit))
  (tarkista-haun-parametrit parametrit false)
  (let [aikataulut (paallystys-kyselyt/hae-paallystyskohteiden-aikataulut-analytiikalle db
                     {:alku (pvm/rajapinta-str-aika->sql-timestamp alkuaika)
                      :loppu (pvm/rajapinta-str-aika->sql-timestamp loppuaika)})
        aikataulut (map #(cond-> (set/rename-keys % {:yllapitokohde :paallystyskohde
                                                     :kohde_alku :kohdeAloitettu
                                                     :paallystys_alku :paallystysAloitettu
                                                     :paallystys_loppu :paallystysValmis
                                                     :valmis_tiemerkintaan :valmisTiemerkintaan
                                                     :tiemerkinta_takaraja :tiemerkintaTakaraja
                                                     :tiemerkinta_alku :tiemerkintaAloitettu
                                                     :tiemerkinta_loppu :tiemerkintaValmis
                                                     :kohde_valmis :kohdeValmis})

                           ;; Muuutetaan aikaleimat stringeiksi, jos ne ovat olemassa
                           (not (nil? (:kohde_alku %)))
                           (update :kohdeAloitettu pvm/sql-aika->pvm-str)
                           (not (nil? (:paallystys_alku %)))
                           (update :paallystysAloitettu pvm/sql-aika->pvm-str)
                           (not (nil? (:paallystys_loppu %)))
                           (update :paallystysValmis pvm/sql-aika->pvm-str)
                           (not (nil? (:valmis_tiemerkintaan %)))
                           (update :valmisTiemerkintaan pvm/sql-aika->pvm-str)
                           (not (nil? (:tiemerkinta_takaraja %)))
                           (update :tiemerkintaTakaraja pvm/sql-aika->pvm-str)
                           (not (nil? (:tiemerkinta_alku %)))
                           (update :tiemerkintaAloitettu pvm/sql-aika->pvm-str)
                           (not (nil? (:tiemerkinta_loppu %)))
                           (update :tiemerkintaValmis pvm/sql-aika->pvm-str)
                           (not (nil? (:kohde_valmis %)))
                           (update :kohdeValmis pvm/sql-aika->pvm-str))
                     aikataulut)]
    {:aikataulut aikataulut}))

(defn- muodosta-paallystysilmoitus [paallystysilmoitus]
  (-> paallystysilmoitus
    (set/rename-keys {:toteutunut-hinta :toteutunutHinta})
    (assoc :yhaLahetyksenTila (cond
                                (and
                                  (false? (:lahetys-onnistunut paallystysilmoitus))
                                  (some? (:lahetetty paallystysilmoitus)))
                                "Epäonnistunut"

                                (nil? (:lahetetty paallystysilmoitus))
                                "Ei lähetetty"

                                :else
                                "Lähetetty onnistuneesti"))
    (dissoc :lahetys-onnistunut :lahetetty :id)))

(defn- muodosta-kulutuskerrostoimenpide [kulutuskerrostoimenpide]
  (-> kulutuskerrostoimenpide
    (update :massat #(-> (konversio/sarakkeet-vektoriin % {:runkoaine :runkoaineet
                                                           :lisaaine :lisaaineet
                                                           :sideaine :sideaineet})
                       (first)
                       (dissoc :id)))
    (update-in [:massat :runkoaineet] (fn [runkoaineet] (map #(dissoc % :id) runkoaineet)))
    (update-in [:massat :lisaaineet] (fn [lisaaineet] (map #(dissoc % :id) lisaaineet)))
    (update-in [:massat :sideaineet] (fn [sideaineet] (map #(dissoc % :id) sideaineet)))
    (update :massat (fn [massa] (when-not (nil? (:massatyyppi massa)) massa)))
    (set/rename-keys {:pinta-ala :pintaAla
                      :massat :massa})))

(defn- muodosta-alustatoimenpide [alustatoimenpide]
  (-> alustatoimenpide
    (dissoc :id)
    (update :massat #(-> (konversio/sarakkeet-vektoriin % {:runkoaine :runkoaineet
                                                           :lisaaine :lisaaineet
                                                           :sideaine :sideaineet})
                       (first)
                       (dissoc :id)))
    (update-in [:massat :runkoaineet] (fn [runkoaineet] (map #(dissoc % :id) runkoaineet)))
    (update-in [:massat :lisaaineet] (fn [lisaaineet] (map #(dissoc % :id) lisaaineet)))
    (update-in [:massat :sideaineet] (fn [sideaineet] (map #(dissoc % :id) sideaineet)))
    (update :massat (fn [massa] (when-not (nil? (:massatyyppi massa)) massa)))
    (update :murske (fn [murske] (when-not (nil? (:tyyppi murske)) murske)))
    (set/rename-keys {:pinta-ala :pintaAla
                      :lisatty-paksuus :lisattyPaksuus
                      :verkon-tyyppi :verkkotyyppi
                      :verkon-tarkoitus :verkonTarkoitus
                      :verkon-sijainti :verkonSijainti
                      :massat :massa})))

(defn hae-paallystysilmoitukset [db {:keys [alkuaika loppuaika] :as parametrit}]
  (log/info "Analytiikka API paallystysilmoitukset :: parametrit" (pr-str parametrit))
  (tarkista-haun-parametrit parametrit false)
  (let [paallystysilmoitukset (paallystys-kyselyt/hae-paallystysilmoitukset-analytiikalle db
                                {:alku (pvm/rajapinta-str-aika->sql-timestamp alkuaika)
                                 :loppu (pvm/rajapinta-str-aika->sql-timestamp loppuaika)})

        kulutuskerrostp (as-> (paallystys-kyselyt/hae-paallystysilmoitusten-kulutuskerroksen-toimenpiteet-analytiikalle db
                                {:alku (pvm/rajapinta-str-aika->sql-timestamp alkuaika)
                                 :loppu (pvm/rajapinta-str-aika->sql-timestamp loppuaika)}) kktp
                          (konversio/sarakkeet-vektoriin (map konversio/alaviiva->rakenne kktp)
                            {:massa :massat} :alikohde)
                          (map muodosta-kulutuskerrostoimenpide
                            kktp))

        alustatp (as-> (paallystys-kyselyt/hae-paallystysilmoitusten-alustan-toimenpiteet-analytiikalle db
                         {:alku (pvm/rajapinta-str-aika->sql-timestamp alkuaika)
                          :loppu (pvm/rajapinta-str-aika->sql-timestamp loppuaika)}) atp
                   (konversio/sarakkeet-vektoriin (map konversio/alaviiva->rakenne atp)
                     {:massa :massat})
                   (map muodosta-alustatoimenpide atp))

        paallystysilmoitukset (map #(-> % (assoc
                                            :kulutuskerrokselleTehdytToimetPOT2
                                            (keep (fn [kk] (when (= (:paallystysilmoitus kk) (:id %))
                                                             (dissoc kk :paallystysilmoitus))) kulutuskerrostp)
                                            :alustalleTehdytToimetPOT2
                                            (keep (fn [a] (when (= (:paallystysilmoitus a) (:id %))
                                                            (dissoc a :paallystysilmoitus))) alustatp))
                                      (muodosta-paallystysilmoitus))
                                paallystysilmoitukset)]
    {:paallystysilmoitukset paallystysilmoitukset}))

(defn hae-hoidon-paikkauskustannukset [db {:keys [alkuaika loppuaika] :as parametrit}]
  (log/info "Analytiikka API hoidon paikkauskustannukset :: parametrit" (pr-str parametrit))
  (tarkista-haun-parametrit parametrit false)
  (let [kulut (map #(set/rename-keys % {:id :harjaId})
                (paallystys-kyselyt/hae-hoidon-paallystyksen-kulut-analytiikalle db
                  {:alku (pvm/rajapinta-str-aika->sql-timestamp alkuaika)
                   :loppu (pvm/rajapinta-str-aika->sql-timestamp loppuaika)}))]

    {:kulut kulut}))

(defn hae-paikkauskohteet [db {:keys [alkuaika loppuaika] :as parametrit}]
  (log/info "Analytiikka API paikkauskohteet :: parametrit" (pr-str parametrit))
  (tarkista-haun-parametrit parametrit false)
  (let [paikkauskohteet (map #(-> %
                                (konversio/alaviiva->rakenne)
                                (set/rename-keys {:id :harjaId
                                                  :ulkoinen-id :ulkoinenId
                                                  :paallystyskohde :paallystyskohdeId
                                                  :yhalahetyksen-tila :yhalahetyksenTila
                                                  :suunniteltu-maara :suunniteltuMaara
                                                  :suunniteltu-hinta :suunniteltuHinta
                                                  :tiemerkintapvm :valmisTiemerkintaan
                                                  :ilmoitettu-virhe :ilmoitettuVirhe
                                                  :toteutunut-hinta :toteutunutHinta
                                                  :tiemerkintaa-tuhoutunut? :tiemerkintaaTuhoutunut})
                                (update :tila (fn [tila] (case tila
                                                           "hylatty"
                                                           "Hylätty"
                                                           ("tilattu"
                                                             "valmis"
                                                             "tarkistettu"
                                                             "ehdotettu")
                                                           (str/capitalize tila)
                                                           nil)))
                                (update :yhalahetyksenTila
                                  (fn [tila]
                                    (case tila
                                      ("odottaa_vastausta"
                                        "lahetetty")
                                      "Lähetetty onnistuneesti"

                                      "virhe"
                                      "Epäonnistunut"

                                      "Ei lähetetty"))))
                          (paikkaus-kyselyt/hae-paikkauskohteet-analytiikalle db
                            {:alku (pvm/rajapinta-str-aika->sql-timestamp alkuaika)
                             :loppu (pvm/rajapinta-str-aika->sql-timestamp loppuaika)}))]

    {:paikkauskohteet paikkauskohteet}))

(defn hae-paikkaukset [db {:keys [alkuaika loppuaika] :as parametrit}]
  (log/info "Analytiikka API paikkaukset  :: parametrit" (pr-str parametrit))
  (tarkista-haun-parametrit parametrit false)
  (let [paikkaukset (map #(-> %
                            (konversio/alaviiva->rakenne)
                            (set/rename-keys {:id :harjaId
                                              :ulkoinen-id :ulkoinenId
                                              :paikkauskohde-id :paikkauskohdeId
                                              :pinta-ala :pintaAla})
                            (update :loppupvm (fn [pvm] (if (nil? pvm)
                                                        nil
                                                        (pvm/sql-aika->pvm-str pvm))))
                            (update :alkupvm (fn [pvm] (if (nil? pvm)
                                                          nil
                                                          (pvm/sql-aika->pvm-str pvm)))))
                      (paikkaus-kyselyt/hae-paikkaukset-analytiikalle db
                        {:alku (pvm/rajapinta-str-aika->sql-timestamp alkuaika)
                         :loppu (pvm/rajapinta-str-aika->sql-timestamp loppuaika)}))]
    {:paikkaukset paikkaukset}))

(defrecord Analytiikka [kehitysmoodi?]
  component/Lifecycle
  (start [{http :http-palvelin db :db-replica integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :analytiikka-toteumat
      (GET "/api/analytiikka/toteumat/:alkuaika/:loppuaika" request
        (kasittele-kevyesti-get-kutsu db integraatioloki "analytiikka"
          :analytiikka-hae-toteumat request
          (fn [parametrit kayttaja db]
            (palauta-toteumat db parametrit kayttaja))
          ;; Vaaditaan analytiikka-oikeudet
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-toteumat-koko
      (GET "/api/analytiikka/toteumat/:alkuaika/:loppuaika/:koordinaattimuutos/:koko" request
        (kasittele-kevyesti-get-kutsu db integraatioloki "analytiikka"
          :analytiikka-hae-toteumat request
          (fn [parametrit kayttaja db]
            (palauta-toteumat db parametrit kayttaja))
          ;; Vaaditaan analytiikka-oikeudet
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-suunnitellut-materiaalit-hoitovuosi
      (GET "/api/analytiikka/suunnitellut-materiaalit/:alkuvuosi/:loppuvuosi" request
        (kasittele-kevyesti-get-kutsu db integraatioloki "analytiikka"
          :analytiikka-hae-suunnitellut-materiaalimaarat request
          (fn [parametrit kayttaja db]
            (palauta-suunnitellut-materiaalimaarat db parametrit kayttaja))
          ;; Vaaditaan analytiikka-oikeudet
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-suunnitellut-materiaalit-urakka
      (GET "/api/analytiikka/suunnitellut-materiaalit/:urakka-id" request
        (kasittele-kevyesti-get-kutsu db integraatioloki "analytiikka"
          :analytiikka-hae-suunnitellut-materiaalimaarat request
          (fn [parametrit kayttaja db]
            (palauta-urakan-suunnitellut-materiaalimaarat db parametrit kayttaja))
          ;; Vaaditaan analytiikka-oikeudet
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-suunnitellut-tehtavamaarat-hoitovuosi
      (GET "/api/analytiikka/suunnitellut-tehtavat/:alkuvuosi/:loppuvuosi" request
        (kasittele-kevyesti-get-kutsu db integraatioloki "analytiikka"
          :analytiikka-hae-suunnitellut-tehtavamaarat request
          (fn [parametrit kayttaja db]
            (palauta-suunnitellut-tehtavamaarat db parametrit kayttaja))
          ;; Vaaditaan analytiikka-oikeudet
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-suunnitellut-tehtavamaarat-urakka
      (GET "/api/analytiikka/suunnitellut-tehtavat/:urakka-id" request
        (kasittele-kevyesti-get-kutsu db integraatioloki "analytiikka"
          :analytiikka-hae-suunnitellut-tehtavamaarat request
          (fn [parametrit kayttaja db]
            (palauta-urakan-suunnitellut-tehtavamaarat db parametrit kayttaja))
          ;; Vaaditaan analytiikka-oikeudet
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-materiaalit
      (GET "/api/analytiikka/materiaalit" request
        (kasittele-kevyesti-get-kutsu db integraatioloki "analytiikka"
          :analytiikka-hae-materiaalikoodit request
          (fn [parametrit kayttaja db]
            (palauta-materiaalit db parametrit kayttaja))
          ;; Vaaditaan analytiikka-oikeudet
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-tehtavat
      (GET "/api/analytiikka/tehtavat" request
        (kasittele-kevyesti-get-kutsu db integraatioloki "analytiikka"
          :analytiikka-hae-tehtavat request
          (fn [parametrit kayttaja db]
            (palauta-tehtavat db parametrit kayttaja))
          ;; Vaaditaan analytiikka-oikeudet
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-toimenpiteet
      (GET "/api/analytiikka/toimenpiteet" request
        (kasittele-kevyesti-get-kutsu db integraatioloki "analytiikka"
          :analytiikka-hae-toimenpiteet request
          (fn [parametrit kayttaja db]
            (palauta-toimenpiteet db parametrit kayttaja))
          ;; Vaaditaan analytiikka-oikeudet
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-toimenpiteet
      (GET "/api/analytiikka/rahavaraukset" request
        (kasittele-kevyesti-get-kutsu db integraatioloki "analytiikka"
          :analytiikka-hae-rahavaraukset request
          (fn [parametrit kayttaja db]
            (palauta-rahavaraukset db parametrit kayttaja))
          ;; Vaaditaan analytiikka-oikeudet
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-urakat
      (GET "/api/analytiikka/urakat" request
        (kasittele-kevyesti-get-kutsu db integraatioloki "analytiikka"
          :analytiikka-hae-urakat request
          (fn [parametrit kayttaja db]
            (palauta-urakat db parametrit kayttaja))
          ;; Vaaditaan analytiikka-oikeudet
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-organisaatiot
      (GET "/api/analytiikka/organisaatiot" request
        (kasittele-kevyesti-get-kutsu db integraatioloki "analytiikka"
          :analytiikka-hae-organisaatiot request
          (fn [parametrit kayttaja db]
            (palauta-organisaatiot db parametrit kayttaja))
          ;; Vaaditaan analytiikka-oikeudet
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-turvallisuuspoikkeamat
      (GET "/api/analytiikka/turvallisuuspoikkeamat/:alkuaika/:loppuaika" request
        (kasittele-get-kutsu db integraatioloki :analytiikka-hae-turvallisuuspoikkeamat request
          json-skeemat/+turvallisuuspoikkeamien-vastaus+
          (fn [parametrit kayttaja db]
            (hae-turvallisuuspoikkeamat db parametrit kayttaja))
          ;; Vaaditaan analytiikka-oikeudet
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-paallystysurakat
      (GET "/api/analytiikka/paallystysurakat/:alkuaika/:loppuaika" parametrit
        (kasittele-get-kutsu db integraatioloki :analytiikka-hae-paallystysurakat parametrit
          json-skeemat/+analytiikka-paallystysurakoiden-haku-vastaus+
          (fn [parametrit _kayttaja db]
            (hae-paallystysurakat db parametrit))
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-paallystyskohteet
      (GET "/api/analytiikka/paallystyskohteet/:alkuaika/:loppuaika" parametrit
        (kasittele-get-kutsu db integraatioloki :analytiikka-hae-paallystyskohteet parametrit
          json-skeemat/+analytiikka-paallystyskohteiden-haku-vastaus+
          (fn [parametrit _kayttaja db]
            (hae-paallystyskohteet db parametrit))
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-hae-paallystyskohteiden-aikataulut
      (GET "/api/analytiikka/paallystyskohteiden-aikataulut/:alkuaika/:loppuaika" parametrit
        (kasittele-get-kutsu db integraatioloki :analytiikka-hae-paallystyskohteiden-aikataulut parametrit
          json-skeemat/+analytiikka-paallystyskohteiden-aikataulujen-haku-vastaus+
          (fn [parametrit _kayttaja db]
            (hae-paallystyskohteiden-aikataulut db parametrit))
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-hae-paallystysilmoitukset
      (GET "/api/analytiikka/paallystysilmoitukset/:alkuaika/:loppuaika" parametrit
        (kasittele-get-kutsu db integraatioloki :analytiikka-hae-paallystysilmoitukset parametrit
          json-skeemat/+analytiikka-paallystysilmoitusten-haku-vastaus+
          (fn [parametrit _kayttaja db]
            (hae-paallystysilmoitukset db parametrit))
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-hae-hoidon-paikkauskustannukset
      (GET "/api/analytiikka/hoidon-paikkauskustannukset/:alkuaika/:loppuaika" parametrit
        (kasittele-get-kutsu db integraatioloki :analytiikka-hae-hoidon-paikkaukset parametrit
          json-skeemat/+analytiikka-hoidon-paikkaukset-haku-vastaus+
          (fn [parametrit _kayttaja db]
            (hae-hoidon-paikkauskustannukset db parametrit))
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-hae-paikkauskohteet
      (GET "/api/analytiikka/paikkauskohteet/:alkuaika/:loppuaika" parametrit
        (kasittele-get-kutsu db integraatioloki :analytiikka-hae-paikkauskohteet parametrit
          json-skeemat/+analytiikka-paikkauskohteiden-haku-vastaus+
          (fn [parametrit _kayttaja db]
            (hae-paikkauskohteet db parametrit))
          :analytiikka)))

    (julkaise-reitti
      http :analytiikka-hae-paikkaukset
      (GET "/api/analytiikka/paikkaukset/:alkuaika/:loppuaika" parametrit
        (kasittele-get-kutsu db integraatioloki :analytiikka-hae-paikkaukset parametrit
          json-skeemat/+analytiikka-paikkausten-haku-vastaus+
          (fn [parametrit _kayttaja db]
            (hae-paikkaukset db parametrit))
          :analytiikka)))

    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
      :analytiikka-toteumat
      :analytiikka-toteumat-koko
      :analytiikka-materiaalit
      :analytiikka-tehtavat
      :analytiikka-toimenpiteet
      :analytiikka-urakat
      :analytiikka-organisaatiot
      :analytiikka-suunnitellut-materiaalit-hoitovuosi
      :analytiikka-suunnitellut-materiaalit-urakka
      :analytiikka-suunnitellut-tehtavamaarat-hoitovuosi
      :analytiikka-suunnitellut-tehtavamaarat-urakka
      :analytiikka-turvallisuuspoikkeamat
      :analytiikka-paallystysurakat
      :analytiikka-paallystyskohteet
      :analytiikka-hae-paallystyskohteiden-aikataulut
      :analytiikka-hae-paallystysilmoitukset
      :analytiikka-hae-hoidon-paikkauskustannukset
      :analytiikka-hae-paikkauskohteet
      :analytiikka-hae-paikkaukset)
    this))
