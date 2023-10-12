(ns harja.testi
  "Harjan testauksen apukoodia."
  (:require
    [clojure.test :refer :all]
    [clojure.core.async :as async :refer [alts! >! <! go timeout chan <!!]]
    [taoensso.timbre :as log]
    [harja.palvelin.asetukset :as a]
    [harja.palvelin.komponentit.todennus :as todennus]
    [harja.palvelin.komponentit.http-palvelin :as http]
    [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
    [harja.palvelin.komponentit.tietokanta :as tietokanta]
    [harja.palvelin.integraatiot.jms :as jms]
    [harja.palvelin.komponentit.liitteet :as liitteet]
    [harja.palvelin.komponentit.vienti :as vienti]
    [harja.palvelin.raportointi.excel :as excel]
    [tarkkailija.palvelin.komponentit
     [event-tietokanta :as event-tietokanta]
     [tapahtumat :as tapahtumat]
     [jarjestelma-rajapinta :as rajapinta]
     [uudelleen-kaynnistaja :as uudelleen-kaynnistaja]]
    [tarkkailija.palvelin.palvelut.tapahtuma :as tapahtuma]
    [com.stuartsierra.component :as component]
    [clj-time.core :as t]
    [clj-time.coerce :as tc]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
    [harja.kyselyt.konversio :as konv]
    [harja.pvm :as pvm]
    [clj-gatling.core :as gatling]
    [clojure.java.jdbc :as jdbc]
    [harja.tyokalut.env :as env]
    [slingshot.slingshot :refer [throw+ try+]]
    [clojure.java.io :as io])
  (:import (org.postgresql.util PSQLException)
           (java.util Locale)
           (java.lang Boolean Exception)
           (java.util.concurrent TimeoutException)
           (java.sql Statement)
           (java.text SimpleDateFormat)
           (java.util Date)))

(def jarjestelma nil)

(defn <!!-timeout [kanava timeout]
  (let [[arvo valmistunut-kanava] (async/alts!! [kanava
                                                 (async/timeout timeout)])]
    (if (not= valmistunut-kanava kanava)
      (throw (TimeoutException. (str "Ei saatu arvoa ajassa " timeout)))
      arvo)))

(Locale/setDefault (Locale. "fi" "FI"))

(def ^{:dynamic true
       :doc "Jos käytössä on oikea JMS komponentti, pitää se aloittaa ennen testien ajoa. Anna lista JMS clienttejä"} *aloitettavat-jmst* nil)
(def ^{:dynamic true
       :doc "Jos halutaan testissä lisätä kuuntelijoita, pitää ne lisätä ennen kunntelun aloitusta."} *lisattavia-kuuntelijoita?* false)
(def ^{:dynamic true
       :doc "Callback sen jälkeen kun on jms(t) käynnistetty. Hyvä paikka purgeta jonoja."} *jms-kaynnistetty-fn* nil)
(def ^{:dynamic true
       :doc "Tänne mapissa kanavan nimi funktio pareja, joiden pitäs olla testissä käytössä.
             Tällä on vaikutusta vain jos *lisattavia-kuuntelijoita?* on truthy"} *lisattavat-kuuntelijat* nil)
(def ^:dynamic *kaynnistyksen-jalkeen-hook* nil)
(def ^:dynamic *ennen-sulkemista-hook* nil)

(def sonja-aloitus-go (atom nil))

(defn lisaa-kuuntelijoita!
  "Helpperi funktio, jolla voi lisätä kuuntelijoita testiin, jos käyttää laajenna-integraatiojarjestelmafixturea, jolle
   on lisätty oikea sonja"
  [kuuntelijat]
  {:pre [(not (nil? *lisattavat-kuuntelijat*))
         (not (nil? @sonja-aloitus-go))
         (map? kuuntelijat)]}
  (async/>!! *lisattavat-kuuntelijat* kuuntelijat)
  (async/<!! @sonja-aloitus-go))

(defn ei-lisattavia-kuuntelijoita! []
  "Helpperi funktio, jolla voi ilmoittaa, ettei ole lisättäviä kuuntelijoita testiin, jos käyttää laajenna-integraatiojarjestelmafixturea, jolle
   on lisätty oikea sonja"
  {:pre [(not (nil? *lisattavat-kuuntelijat*))]}
  (async/>!! *lisattavat-kuuntelijat* :ei-lisattavaa) )

(defn circleci? []
  (not (nil? (env/env "CIRCLE_BRANCH"))))

;; Ei täytetä Jenkins-koneen levytilaa turhilla logituksilla
;; eikä tehdä traviksen logeista turhan pitkiä
(log/merge-config!
  {:appenders
   {:println
    {:min-level
     (cond
       (env/env "HARJA_NOLOG" false)
       :fatal

       :default
       ;:debug -- Aseta Debug level käyttöön, mikäli tarvitsee selvittää jotain tiettyä ongelmaa, joka ei muuten tule esille
       :info
       )}}})

(def testitietokanta {:palvelin (env/env "HARJA_TIETOKANTA_HOST" "localhost")
                      :portti (env/env "HARJA_TIETOKANTA_PORTTI" 5432)
                      :tietokanta "harjatest"
                      :kayttaja "harjatest"
                      :salasana nil
                      :tarkkailun-timeout-arvot {:paivitystiheys-ms 3000
                                                 :kyselyn-timeout-ms 10000}
                      :tarkkailun-nimi :db})

; temppitietokanta jonka omistaa harjatest. käytetään väliaikaisena tietokantana jotta templatekanta
; (harjatest_template) ja testikanta (harjatest) ovat vapaina droppausta ja templaten kopiointia varten.
(def temppitietokanta {:palvelin (env/env "HARJA_TIETOKANTA_HOST" "localhost")
                       :portti (env/env "HARJA_TIETOKANTA_PORTTI" 5432)
                       :tietokanta "temp"
                       :kayttaja "harjatest"
                       :salasana nil
                       :tarkkailun-timeout-arvot {:paivitystiheys-ms 3000
                                                  :kyselyn-timeout-ms 10000}
                       :tarkkailun-nimi :db-temppi})

(defn odota-ehdon-tayttymista [ehto-fn viesti max-aika-ms]
  (loop [max-ts (+ max-aika-ms (System/currentTimeMillis))]
    (if (> (System/currentTimeMillis) max-ts)
      (assert false (str "Ehto '" viesti "' ei täyttynyt " max-aika-ms "ms kuluessa"))
      (when-not (ehto-fn)
        (recur max-ts)))))

(defn odota [ehto-fn viesti max-aika]
  (odota-ehdon-tayttymista ehto-fn viesti max-aika))

(defn odota-arvo
  "Odottaa, että annetuun atomiin on tullut arvo. Palauttaa arvon.
   Ottaa optionaalisesti maksimiajan, joka odotetaan (oletus 5 sekuntia)."
  ([atom] (odota-arvo atom 5000))
  ([atom max-aika]
   (odota #(not (nil? @atom)) "Atomiin on tullut ei-nil arvo" max-aika)
   @atom))

(defn luo-testitietokanta []
  {:datasource (tietokanta/luo-yhteyspool testitietokanta)})

(defn luo-temppitietokanta []
  {:datasource (tietokanta/luo-yhteyspool temppitietokanta)})

(defn luo-liitteidenhallinta []
  (liitteet/->Liitteet nil))

(defonce db (:datasource (luo-testitietokanta)))
(defonce temppidb (:datasource (luo-temppitietokanta)))

(def ds {:datasource db})

(defn q
  "Kysele Harjan kannasta yksikkötestauksen yhteydessä.
   Palauttaa vectorin, jossa item on riviä esittävä vector,
   jossa kyseltyjen sarakkeiden arvot ovat järjestyksessä.

   Esim: SELECT id, nimi FROM urakka
   palauttaisi
   [[4, 'Oulun alueurakka']
    [5, 'Joensuun alueurakka']]"
  [& sql]
  (with-open [c (.getConnection db)
              ps (.prepareStatement c (reduce str sql))
              rs (.executeQuery ps)]
    (let [cols (-> (.getMetaData rs) .getColumnCount)]
      (loop [res []
             more? (.next rs)]
        (if-not more?
          res
          (recur (conj res (loop [row []
                                  i 1]
                             (if (<= i cols)
                               (do
                                 (recur (conj row (.getObject rs i))
                                        (inc i)))
                               row)))
                 (.next rs)))))))

(defn q-map
  "Kysele Harjan kannasta yksikkötestauksen yhteydessä.
   Palauttaa vectorin, jossa item on map, jonka avaimina
   ovat kysellyt sarakkeet avaimina

   Esim. SELECT id, nimi FROM urakka
   palauttaisi
   [{:id 4 :nimi 'Oulun alueurakka'}
    {:id 5 :nimi 'Joensuun alueurakka'}]."
  [& sql]
  (with-open [c (.getConnection db)
              ps (.prepareStatement c (reduce str sql))
              rs (.executeQuery ps)]
    (let [cols (-> (.getMetaData rs) .getColumnCount)]
      (loop [res []
             more? (.next rs)]
        (if-not more?
          res
          (recur (conj res (loop [row {}
                                  i 1]
                             (if (<= i cols)
                               (recur (assoc row
                                        (keyword (-> (.getMetaData rs)
                                                     (.getColumnName i)))
                                        (.getObject rs i))
                                      (inc i))
                               row)))
                 (.next rs)))))))

(defn u
  "Päivitä Harjan kantaa yksikkötestauksen yhteydessä"
  [& sql]
  (with-open [c (.getConnection db)
              ps (.prepareStatement c (reduce str sql))]
    (.executeUpdate ps)))

(defn i
  "Tee SQL insert Harjan kantaan yksikkötestauksen yhteydessä, ja palauta generoitu id"
  [& sql]
  (with-open [c (.getConnection db)
              ps (.prepareStatement c (reduce str sql) Statement/RETURN_GENERATED_KEYS)]
    (.executeUpdate ps)
    (let [generated-keys (.getGeneratedKeys ps)
          next? (.next generated-keys)]
      (when next?
        (.getLong generated-keys 1)))))

(defn- tapa-backend-kannasta [ps kanta]
  (with-open [rs (.executeQuery ps (str "SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = '" kanta "' AND pid <> pg_backend_pid()"))]
    ;; Jos rs ei sisällä rivejä, kanta on jo tapettu
    (when (.next rs)
      (let [kanta-tapettu-onnistuneesti? (.getBoolean rs 1)]
        (when-not kanta-tapettu-onnistuneesti?
          (throw+ {:type :virhe-kannan-tappamisessa
                   :viesti (str "Kantaa " kanta " ei saatu kiinni.")}))))))

(defn odota-etta-kanta-pystyssa [db]
  (let [timeout-s 10]
    (jdbc/with-db-connection [db db]
                             (with-open [c (jdbc/get-connection db)
                                         stmt (jdbc/prepare-statement c
                                                                      "SELECT 1;"
                                                                      {:timeout timeout-s
                                                                       :result-type :forward-only
                                                                       :concurrency :read-only})
                                         rs (.executeQuery stmt)]
                               (let [kanta-ok? (if (.next rs)
                                                 (= 1 (.getObject rs 1))
                                                 false)]
                                 (when-not kanta-ok?
                                   (log/error (str "Ei saatu kantaan yhteyttä " timeout-s " sekunnin kuluessa"))))))))

(defn- luo-kannat-uudelleen []
  (alter-var-root #'db (fn [_]
                         (com.mchange.v2.c3p0.DataSources/destroy db)
                         (:datasource (luo-testitietokanta))))
  (alter-var-root #'ds (fn [_]
                         {:datasource db}))
  (alter-var-root #'temppidb (fn [_]
                               (com.mchange.v2.c3p0.DataSources/destroy temppidb)
                               (:datasource (luo-temppitietokanta)))))

(defn arvo-vapaa-portti
  "Arpoo vapaan portinnumeron ja palauttaa sen"
  []
  (let [s (doto (java.net.ServerSocket. 0)
            (.setReuseAddress true))]
    (try
      (.getLocalPort s)
      (finally (.close s)))))

(defn yrita-querya
  ([f n] (yrita-querya f n true nil))
  ([f n log?] (yrita-querya f n log? nil))
  ([f n log? param?]
   (loop [n-kierros 0]
     (if (= n-kierros n)
       (throw (Exception. "Queryn yrittäminen epäonnistui"))
       (let [tulos (try+
                     (when (> n-kierros 0)
                       (println "yrita-querya: yritys" n-kierros))

                     (if param?
                       (f n-kierros)
                       (f))
                     (catch [:type :virhe-kannan-tappamisessa] {:keys [viesti]}
                       (println "yrita-querya: virhe kannan tappamisesssa:" viesti)
                       (Thread/sleep 500)
                       (when log?
                         (log/warn viesti))
                       ::virhe)
                     (catch PSQLException e
                       (println "yrita-querya: psql:" e)
                       (Thread/sleep 500)
                       (when log?
                         (log/warn e "- yritetään uudelleen, yritys" n-kierros))
                       ::virhe))
             virhe? (= tulos ::virhe)]
         (if virhe?
           (recur (inc n-kierros))
           tulos))))))

(defonce ^:private testikannan-luonti-lukko (Object.))

(defn postgresql-versio-num []
  (let [postgresql-versio (ffirst (q "SELECT version()"))
        versio-num (second (re-find #"PostgreSQL (\d+.\d+)" postgresql-versio))]
    (some->
      versio-num
      (Float/parseFloat))))

(def testikanta-yritysten-lkm 15)
(defn pudota-ja-luo-testitietokanta-templatesta
  "Droppaa tietokannan ja luo sen templatesta uudelleen"
  []
  (let [postgresql-versio (or (postgresql-versio-num) 0)
        versio-13-tai-isompi? (>= postgresql-versio 13)]
    (locking testikannan-luonti-lukko
      (with-open [c (.getConnection temppidb)
                  ps (.createStatement c)]

        (yrita-querya (fn [] (tapa-backend-kannasta ps "harjatest_template")) testikanta-yritysten-lkm true)
        (yrita-querya (fn [] (tapa-backend-kannasta ps "harjatest")) testikanta-yritysten-lkm true)
        (yrita-querya (fn [n]
                        ;; Huom: "with (force)" toimii PostgreSQl 13 alkaen.
                        ;; FIXME: Poista versio-check, kun PostgreSQL 13 on otettu kaikkialla käyttöön.
                        (.executeUpdate ps (str "DROP DATABASE IF EXISTS harjatest"
                                             (when versio-13-tai-isompi?
                                               " with (force)")))
                        (async/<!! (async/timeout (* n 1000)))
                        (.executeUpdate ps "CREATE DATABASE harjatest TEMPLATE harjatest_template"))
          testikanta-yritysten-lkm
          true
          true))
      (luo-kannat-uudelleen)
      (odota-etta-kanta-pystyssa {:datasource db})
      (odota-etta-kanta-pystyssa {:datasource temppidb}))))

(defn katkos-testikantaan!
  "Varsinaisen katkoksen tekeminen ilman system komentoja ei oikein onnistu, joten pudotetaan
   kanta pois ja luodaan se uusiksi.

   Tämä palauttaa kanavan, josta pitää ensin lukea arvo ulos. Tämä arvo indikoi, että nyt on yhteys
   kantaan poikki. Sen jälkeen testissä voipi tehdä mitä haluaa katkoksen aikana. Testin tulee antaa kanavaan
   takaisin jokin arvo, jotta kanta luodaan taas uusiksi merkkaamaan katkoksen päätöstä. Tämän jälkeen
   ei ole pakko lukea kanavasta enää arvoja ulos, mutta sinne laitetaan vielä yksi arvo merkkaamaan, että
   kanta on valmis.

   Jos tämä prosessi ei onnistu 30 sekunnin kuluessa, kanava suljetaan."
  []
  (let [kanava (async/chan)
        db-name (:tietokanta testitietokanta)
        timeout (* 30 1000)
        testikanta-data-placeholder (str (gensym "kanta"))
        kanta-asetukset {:dbtype "postgresql"
                         :classname "org.postgresql.Driver"
                         :dbname (:tietokanta testitietokanta)
                         :host (System/getenv "HARJA_TIETOKANTA_HOST")
                         :port (:portti testitietokanta)
                         :user (:kayttaja testitietokanta)
                         :password (:salasana testitietokanta)}
        tmpkanta-asetukset {:dbtype "postgresql"
                            :classname "org.postgresql.Driver"
                            :dbname (:tietokanta temppitietokanta)
                            :host (System/getenv "HARJA_TIETOKANTA_HOST")
                            :port (:portti temppitietokanta)
                            :user (:kayttaja temppitietokanta)
                            :password (:salasana temppitietokanta)}
        kierroksia 5
        tapa-kanta (fn [kanta-asetukset db-name]
                     (yrita-querya (fn [n-kierros]
                                     (try
                                       (jdbc/with-db-connection [db kanta-asetukset]
                                                                (with-open [c (jdbc/get-connection db)
                                                                            ps (.createStatement c)]
                                                                  (.executeUpdate ps (str "UPDATE pg_database SET datallowconn = 'false' WHERE datname = '" db-name "'"))
                                                                  (tapa-backend-kannasta ps db-name)
                                                                  #_(with-open [rs (tapa-backend-kannasta ps db-name)]
                                                                      (if (.next rs)
                                                                        (let [tulos (.getObject rs 1)]
                                                                          (when-not (and (instance? Boolean
                                                                                                    tulos)
                                                                                         (= "true" (.toString tulos)))
                                                                            (throw (Exception. (str "Ei saatu kiinni. Tulos: " tulos " type: " (type tulos))))))
                                                                        (throw (Exception. "Ei saatu kiinni. koska yhteys ei palauttanut mitään"))))))
                                       (catch Exception e
                                         (when (= n-kierros kierroksia)
                                           (throw e)))))
                                   kierroksia true true))]
    (go (let [[arvo _] (async/alts!! [(go (jdbc/with-db-connection [db tmpkanta-asetukset]
                                                                   (with-open [c (jdbc/get-connection db)
                                                                               ps (.createStatement c)]
                                                                     (.executeUpdate ps (str "CREATE USER " testikanta-data-placeholder " WITH SUPERUSER"))))
                                          (tapa-kanta tmpkanta-asetukset db-name)
                                          (jdbc/with-db-connection [db {:datasource temppidb}]
                                                                   (with-open [c (jdbc/get-connection db)
                                                                               ps (.createStatement c)]
                                                                     (.executeUpdate ps (str "CREATE DATABASE " testikanta-data-placeholder " OWNER " testikanta-data-placeholder " TEMPLATE " db-name))
                                                                     (.executeUpdate ps (str "DROP DATABASE IF EXISTS " db-name))))
                                          (async/>! kanava :katkos-kaynnissa)
                                          ;; Odotetaan, että saadaan laittaa kanta takaisin pystyyn
                                          (async/<! kanava)
                                          (println "------------------------------------")
                                          (println "---> KÄYNNISTEÄÄN UUSIKSI")
                                          (tapa-kanta tmpkanta-asetukset testikanta-data-placeholder)
                                          (jdbc/with-db-connection [db tmpkanta-asetukset]
                                                                   (with-open [c (jdbc/get-connection db)
                                                                               ps (.createStatement c)]
                                                                     (.executeUpdate ps (str "CREATE DATABASE " db-name " OWNER " db-name " TEMPLATE " testikanta-data-placeholder ""))))
                                          (println "---> KÄYNNISTETTY"))
                                      (go (async/<! (async/timeout timeout))
                                          ::timeout)])]
          (jdbc/with-db-connection [db kanta-asetukset]
                                   (with-open [c (jdbc/get-connection db)
                                               ps (.createStatement c)]
                                     #_(yrita-querya (fn [] (tapa-backend-kannasta ps testikanta-data-placeholder)) 5 false)
                                     (yrita-querya (fn [] (.executeUpdate ps (str "DROP DATABASE IF EXISTS " testikanta-data-placeholder))) 5)
                                     (yrita-querya (fn [] (.executeUpdate ps (str "DROP USER IF EXISTS " testikanta-data-placeholder))) 5)))
          (println "---> TMP KANTA TAPETTU")
          (async/put! kanava :kanta-uudetaan-kaynnissa)
          (async/close! kanava)
          (when (= ::timeout arvo)
            (throw (TimeoutException. (str "Possukaktkoksen käsittely timeouttas " timeout " ms jälkeen"))))))
    kanava))

(defprotocol FeikkiHttpPalveluKutsu
  (kutsu-palvelua
    ;; GET
    [this nimi kayttaja]

    ;; POST
    [this nimi kayttaja payload]
    "kutsu HTTP palvelufunktiota suoraan.")

  (kutsu-karttakuvapalvelua
    ;; POST
    [this nimi kayttaja payload koordinaatti extent])
  
  (kutsu-excel-palvelua
    ;; Palauttaa halutun excelin testiin ennen kuin siitä generoidaan
    ;; oikea excel-tiedosto, eli raporttielementteinä.
    [this nimi kayttaja payload])

  (kutsu-excel-vienti-palvelua
    [this nimi kayttaja params tiedostonimi]))

(defn- palvelua-ei-loydy [nimi]
  (is false (str "Palvelua " nimi " ei löydy!"))
  {:error "Palvelua ei löydy"})

(defn- arg-count [f]
  {:pre [(instance? clojure.lang.AFunction f)]}
  (-> f class .getDeclaredMethods first .getParameterTypes alength))

(defn- post-kutsu? [f]
  (= 2 (arg-count f)))

(defn- heita-jos-ei-ole-validi [spec palvelun-nimi payload]
  (when-not (s/valid? spec payload)
    (throw (Exception. (str "Palvelun " palvelun-nimi " ei ole validi.
    Riippuen testista, tämä voi olla sekä odotettu tila että virhe!
    Payload: " payload "
    Spec selitys: " (s/explain-str spec payload))))))

(defn- wrap-validointi [nimi palvelu-fn {:keys [kysely-spec vastaus-spec]}]
  (as-> palvelu-fn f
        (if kysely-spec
          (fn [user payload]
            (heita-jos-ei-ole-validi kysely-spec (str nimi " kysely") payload)
            (f user payload))
          f)

        (if vastaus-spec
          (if (post-kutsu? f)
            (fn [user payload]
              (let [v (f user payload)]
                (heita-jos-ei-ole-validi vastaus-spec (str nimi " vastaus") v)
                v))

            (fn [user]
              (let [v (f user)]
                (heita-jos-ei-ole-validi vastaus-spec (str nimi " vastaus") v)
                v)))
          f)))

(defn testi-http-palvelin
  "HTTP 'palvelin' joka vain ottaa talteen julkaistut palvelut."
  []
  (let [palvelut (atom {})]
    (reify
      http/HttpPalvelut
      (julkaise-palvelu [_ nimi palvelu-fn]
        (swap! palvelut assoc nimi palvelu-fn))
      (julkaise-palvelu [_ nimi palvelu-fn optiot]

        (swap! palvelut assoc nimi
               (wrap-validointi nimi palvelu-fn optiot)))
      (poista-palvelu [_ nimi]
        (swap! palvelut dissoc nimi))

      FeikkiHttpPalveluKutsu
      (kutsu-palvelua [_ nimi kayttaja]
        (if-let [palvelu (get @palvelut nimi)]
          (palvelu kayttaja)
          (palvelua-ei-loydy nimi)))
      (kutsu-palvelua [_ nimi kayttaja payload]
        (if-let [palvelu (get @palvelut nimi)]
          (let [vastaus (palvelu kayttaja payload)]
            (if (http/async-response? vastaus)
              (async/<!! (:channel vastaus))
              vastaus))
          (palvelua-ei-loydy nimi)))

      (kutsu-karttakuvapalvelua [_ nimi kayttaja payload koordinaatti extent]
        ((get @palvelut :karttakuva-klikkaus)
         kayttaja
         {:parametrit (assoc payload "_" nimi)
          :koordinaatti koordinaatti
          :extent (or extent
                      [-550093.049087613 6372322.595126259 1527526.529326106 7870243.751025201])}))

      (kutsu-excel-palvelua [_ nimi kayttaja payload]
        (with-redefs
          [;; Sen sijaan, että muodostetaan excel, palautetaan raporttielementit
           ;; mapissa, jolloin siitä ei yritetä tehdä tiedostoa
           excel/muodosta-excel (fn [raportti _] {:raportti raportti})
           ;; parametrien luku onnistuu testeissä ilman kikkailuja, toisin kuin ringin kautta.
           vienti/lue-body-parametrit identity]
          ((get @palvelut :excel)
           {:kayttaja kayttaja
            :body payload
            :params {"_" nimi}})))

      (kutsu-excel-vienti-palvelua [_ nimi kayttaja params tiedostonimi]
        (if-let [palvelu (get @palvelut nimi)]
          (let [tiedosto (io/file tiedostonimi)
                ;; Muunnetaan mapin avaimet ja arvot stringiksi, kuten käy tällaisissa kutsuissa.
                params (into {} (map (fn [[k v]]
                                       [(name k) (str v)]) params))
                payload {:kayttaja kayttaja
                         :params (merge params
                                   {"file" {:filename tiedostonimi
                                            :tempfile (io/file tiedosto)}})}
                vastaus (palvelu payload)]
            (if (http/async-response? vastaus)
              (async/<!! (:channel vastaus))
              vastaus))
          (palvelua-ei-loydy nimi))))))

(defn materiaali-haun-pg-Array->map
  [haku]
  ;; Materiaalien haku näyttää vähän rumalta, koska q funktio
  ;; käyttää jdbc funktioita suoraan eikä konvertoi PgArrayta nätisti Clojure vektoriksi.
  ;; Siksipä se muunnos täytyy tehdä itse.
  (mapv (fn [materiaali-pg-array]
          (let [materiaali-vector (konv/array->vec materiaali-pg-array 0)
                muutokset (transduce
                            ;; Jostain syystä pgobject->map ei tykänny :double :long tai :date tyypeistä
                            (comp (map #(konv/pgobject->map %
                                                            :pvm :string
                                                            :maara :string
                                                            :lisatieto :string
                                                            :id :string
                                                            :hairiotilanne :string
                                                            :toimenpide :string
                                                            :luotu :string))
                                  ;; tyhja-nilliksi-fn:ta käytetään siten, että tyhjä string palautetaan nillinä.
                                  ;; Muussa tapauksessa käytetään annettua funktiota ihan normisti annettuun arvoon.
                                  ;; Tämä siksi, että Javan Integer funktio ei pidä tyhjistä stringeistä.
                                  (map #(let [tyhja-string->nil (fn [funktio teksti]
                                                                  (if (= teksti "")
                                                                    nil (funktio teksti)))]
                                          (assoc % :pvm (tyhja-string->nil pvm/dateksi (:pvm %))
                                                   :maara (tyhja-string->nil (fn [x] (Integer. x)) (:maara %))
                                                   :id (tyhja-string->nil (fn [x] (Integer. x)) (:id %))
                                                   :hairiotilanne (tyhja-string->nil (fn [x] (Integer. x)) (:hairiotilanne %))
                                                   :toimenpide (tyhja-string->nil (fn [x] (Integer. x)) (:toimenpide %))
                                                   :luotu (tyhja-string->nil pvm/dateksi (:pvm %))))))
                            conj [] (first materiaali-vector))
                nimi (second materiaali-vector)]
            {:muutokset muutokset :nimi nimi}))
        haku))


(defn hae-urakan-id-nimella [nimi]
  (let [urakka-id (ffirst (q (str "SELECT id
                                     FROM urakka
                                    WHERE nimi = '" nimi "';")))]
    (if urakka-id
      urakka-id
      (throw (Exception. (format "Annetulla nimellä: '%s'  ei löydy urakkaa!!" nimi))))))

(defn kutsu-http-palvelua
  "Lyhyt muoto testijärjestelmän HTTP palveluiden kutsumiseen."
  ([nimi kayttaja]
   (kutsu-palvelua (:http-palvelin jarjestelma) nimi kayttaja))
  ([nimi kayttaja payload]
   (kutsu-palvelua (:http-palvelin jarjestelma) nimi kayttaja payload)))

(def testikayttajien-lkm (atom nil))
(def pohjois-pohjanmaan-hallintayksikon-id (atom nil))
(def oulun-alueurakan-2005-2010-id (atom nil))
(def oulun-alueurakan-2014-2019-id (atom nil))
(def tampereen-alueurakan-2017-2022-id (atom nil))
(def oulun-maanteiden-hoitourakan-2019-2024-id (atom nil))
(def oulun-maanteiden-hoitourakan-2019-2024-sopimus-id (atom nil))
(def kajaanin-alueurakan-2014-2019-id (atom nil))
(def vantaan-alueurakan-2014-2019-id (atom nil))
(def oulun-alueurakan-lampotila-hk-2014-2015 (atom nil))
(def oulun-alueurakan-2005-2010-paasopimuksen-id (atom nil))
(def oulun-alueurakan-2014-2019-paasopimuksen-id (atom nil))
(def kajaanin-alueurakan-2014-2019-paasopimuksen-id (atom nil))
(def pudasjarven-alueurakan-id (atom nil))
(def muhoksen-paallystysurakan-id (atom nil))
(def muhoksen-paallystysurakan-paasopimuksen-id (atom nil))
(def muhoksen-paikkausurakan-id (atom nil))
(def muhoksen-paikkausurakan-paasopimuksen-id (atom nil))
(def kemin-alueurakan-2019-2023-id (atom nil))
(def iin-maanteiden-hoitourakan-2021-2026-id (atom nil))
(def iin-maanteiden-hoitourakan-lupaussitoutumisen-id (atom nil))
(def raahen-maanteiden-hoitourakan-2023-2028-id (atom nil))
(def raahen-maanteiden-hoitourakan-2023-2028-sopimus-id (atom nil))

(def yit-rakennus-id (atom nil))
(def destia-id (atom nil))
(def kemin-aluerakennus-id (atom nil))

(def paikkauskohde-tyomenetelmat (atom nil))

(defn hae-testikayttajat []
  (ffirst (q (str "SELECT count(*) FROM kayttaja;"))))

(defn hae-merivaylien-hallintayksikon-id []
  (ffirst (q (str "SELECT id
                   FROM   organisaatio
                   WHERE  nimi = 'Meriväylät';"))))

(defn hae-sisavesivaylien-hallintayksikon-id []
  (ffirst (q (str "SELECT id
                   FROM   organisaatio
                   WHERE  nimi = 'Sisävesiväylät';"))))

(defn hae-saimaan-kanavaurakan-paasopimuksen-id []
  (ffirst (q (str "SELECT id
                   FROM   sopimus
                   WHERE  nimi = 'Saimaan huollon pääsopimus';"))))

(defn hae-saimaan-kanavaurakan-lisasopimuksen-id []
  (ffirst (q (str "SELECT id
                   FROM   sopimus
                   WHERE  nimi = 'Saimaan huollon lisäsopimus';"))))

(defn hae-kohde-palli []
  (ffirst (q (str "SELECT id FROM kan_kohde WHERE nimi = 'Pälli';"))))

(defn hae-kohteenosat-palli []
  (first (q (str "SELECT id, tyyppi FROM kan_kohteenosa WHERE \"kohde-id\" = (SELECT id FROM kan_kohde WHERE nimi = 'Pälli');"))))

(defn hae-vaylanhoito-ei-yksiloity-tpk-id []
  (ffirst (q (str "SELECT id
                     FROM tehtava t4
                    WHERE t4.nimi = 'Ei yksilöity' AND t4.emo = (select id from toimenpide t3 WHERE t3.nimi = 'Laaja toimenpide' and t3.taso = 3
                          AND t3.emo = (select id from toimenpide t2 WHERE t2.nimi = 'Vesiliikenteen käyttöpalvelut' and t2.taso = 2
                          AND t2.emo = (SELECT id FROM toimenpide t1 WHERE t1.nimi = 'Käyttö, meri' AND t1.taso = 1)));"))))


(defn hae-kohde-soskua []
  (ffirst (q (str "SELECT id FROM kan_kohde WHERE nimi = 'Soskua';"))))

(defn hae-kohde-kansola []
  (ffirst (q (str "SELECT id FROM kan_kohde WHERE nimi = 'Kansola';"))))

(defn hae-kohteenosat-kansola []
  (first (q (str "SELECT id, tyyppi FROM kan_kohteenosa WHERE \"kohde-id\" = (SELECT id FROM kan_kohde WHERE nimi = 'Kansola');"))))

(defn hae-kohde-iisalmen-kanava []
  (ffirst (q (str "SELECT id FROM kan_kohde WHERE nimi = 'Iisalmen kanava';"))))

(defn hae-saimaan-kanavan-tikkalasaaren-sulun-kohde-id []
  (ffirst (q "SELECT kk.id
              FROM kan_kohde kk
                JOIN kan_kohde_urakka kku ON kk.id = kku.\"kohde-id\"
                JOIN urakka u ON u.id = kku.\"urakka-id\"
              WHERE u.nimi = 'Saimaan kanava' AND kk.nimi = 'Tikkalansaaren sulku';")))

(defn hae-saimaan-kanavan-materiaalit []
  (let [haku (q "SELECT muutokset, nimi, yksikko
                 FROM vv_materiaalilistaus
                 WHERE \"urakka-id\"  = (SELECT id FROM urakka WHERE nimi = 'Saimaan kanava');")
        saimaan-materiaalit (materiaali-haun-pg-Array->map haku)]
    saimaan-materiaalit))

(defn hae-helsingin-vesivaylaurakan-materiaalit []
  (let [haku (q "SELECT muutokset, nimi, yksikko
                 FROM vv_materiaalilistaus
                 WHERE \"urakka-id\"  = (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL');")
        helsingin-materiaalit (materiaali-haun-pg-Array->map haku)]
    helsingin-materiaalit))

(defn hae-helsingin-vesivaylaurakan-urakoitsija []
  (ffirst (q (str "SELECT urakoitsija
                   FROM   urakka
                   WHERE  nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'"))))

(defn hae-urakoitsijan-urakka-idt [urakoitsija-id]
  (map :id (q-map (str "SELECT id
                   FROM   urakka
                   WHERE  urakoitsija = " urakoitsija-id ";"))))

(defn hae-saimaan-kanavaurakan-toimenpiteet
  ([] (hae-saimaan-kanavaurakan-toimenpiteet false))
  ([q-map?]
   (let [haku-fn (if q-map? q-map q)]
     (haku-fn (str "SELECT id, toimenpidekoodi, tyyppi
           FROM kan_toimenpide
           WHERE urakka=" (hae-urakan-id-nimella "Saimaan kanava"))))))

(defn hae-helsingin-reimari-toimenpide-ilman-hinnoittelua []
  (ffirst (q (str "SELECT id FROM reimari_toimenpide
                   WHERE
                   \"urakka-id\" = (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')
                   AND id NOT IN (SELECT \"toimenpide-id\" FROM vv_hinnoittelu_toimenpide) LIMIT 1;"))))

(defn hae-helsingin-reimari-toimenpiteet-molemmilla-hinnoitteluilla
  ([]
   (hae-helsingin-reimari-toimenpiteet-molemmilla-hinnoitteluilla {}))
  ([{:keys [limit] :as optiot}]
   (q (str "SELECT id FROM reimari_toimenpide
                    WHERE
                    \"urakka-id\" = (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')
                    AND id IN (SELECT \"toimenpide-id\" FROM vv_hinnoittelu_toimenpide WHERE poistettu=false GROUP BY \"toimenpide-id\" HAVING COUNT(\"hinnoittelu-id\")=2)"
           (when limit
             (str " LIMIT " limit))
           ";"))))

(defn hae-helsingin-reimari-toimenpide-yhdella-hinnoittelulla
  ([]
   (hae-helsingin-reimari-toimenpide-yhdella-hinnoittelulla {}))
  ([{:keys [hintaryhma?] :as optiot}]
   (let [tulos (q (str "SELECT id FROM reimari_toimenpide
                    WHERE
                    \"urakka-id\" = (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')
                    AND id IN (SELECT \"toimenpide-id\"
                               FROM vv_hinnoittelu_toimenpide AS ht
                               INNER JOIN vv_hinnoittelu AS h ON h.id=ht.\"hinnoittelu-id\"
                               WHERE h.poistettu = FALSE AND ht.poistettu = FALSE
                               AND ht.\"toimenpide-id\" NOT IN (" (str/join ", " (mapv first (hae-helsingin-reimari-toimenpiteet-molemmilla-hinnoitteluilla))) ")"
                       (when (some? hintaryhma?)
                         (str " AND hintaryhma = " hintaryhma?))
                       ");"))]
     (ffirst tulos))))

(defn hae-kiintio-id-nimella [nimi]
  (ffirst (q (str "SELECT id
                   FROM   vv_kiintio
                   WHERE  nimi = '" nimi "'"))))

(defn hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja
  ([]
   (hae-helsingin-vesivaylaurakan-hinnoittelu-ilman-hintoja {}))
  ([{:keys [hintaryhma?] :as optiot}]
   (ffirst (q (str "SELECT vv_hinnoittelu.id FROM vv_hinnoittelu
                    LEFT JOIN vv_hinta ON vv_hinta.\"hinnoittelu-id\" = vv_hinnoittelu.id
                    WHERE \"urakka-id\" = (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')
                    AND vv_hinta.\"hinnoittelu-id\" IS NULL"
                   (when (some? hintaryhma?)
                     (str " AND hintaryhma = " hintaryhma?))
                   " LIMIT 1")))))

(defn hae-helsingin-vesivaylaurakan-hinnoittelut-jolla-toimenpiteita []
  (set (map :id (q-map "SELECT id FROM vv_hinnoittelu
                        WHERE EXISTS (SELECT \"toimenpide-id\" FROM vv_hinnoittelu_toimenpide
                                      WHERE \"hinnoittelu-id\" = vv_hinnoittelu.id)
                              AND \"urakka-id\" = (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL');"))))

(defn hae-helsingin-vesivaylaurakan-hinnoittelut-jolla-ei-toimenpiteita []
  (set (map :id (q-map "SELECT id FROM vv_hinnoittelu
                        WHERE NOT EXISTS (SELECT \"toimenpide-id\" FROM vv_hinnoittelu_toimenpide
                                          WHERE \"hinnoittelu-id\" = vv_hinnoittelu.id)
                              AND \"urakka-id\" = (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL');"))))

(defn hae-vantaan-vesivaylaurakan-hinnoittelu []
  (ffirst (q (str "SELECT id FROM vv_hinnoittelu
                   WHERE \"urakka-id\" = (SELECT id FROM urakka WHERE nimi = 'Vantaan väyläyksikön väylänhoito ja -käyttö, Itäinen SL')
                   LIMIT 1;"))))

(defn hae-vantaan-vesivaylaurakan-hinta []
  (ffirst (q (str "SELECT id FROM vv_hinta
                   WHERE \"hinnoittelu-id\" IN (SELECT id FROM vv_hinnoittelu WHERE nimi = 'Vantaan urakan testihinnoittelu')
                   LIMIT 1;"))))

(defn hae-annetun-urakan-paasopimuksen-id [urakka]
  (ffirst (q (str "SELECT id
                   FROM sopimus
                   WHERE urakka=" urakka " AND
                         paasopimus IS NULL"))))

(defn hae-helsingin-vesivaylaurakan-paasopimuksen-id []
  (ffirst (q (str "SELECT id
                   FROM   sopimus
                   WHERE  nimi = 'Helsingin väyläyksikön pääsopimus'"))))

(defn hae-vayla-hietarasaari []
  (ffirst (q (str "SELECT vaylanro
                   FROM   vv_vayla
                   WHERE  nimi = 'Hietasaaren läntinen rinnakkaisväylä'"))))

(defn hae-helsingin-vesivaylaurakan-sivusopimuksen-id []
  (ffirst (q (str "SELECT id
                   FROM   sopimus
                   WHERE  nimi = 'Helsingin väyläyksikön sivusopimus'"))))

(defn hae-oulujoen-sillan-id []
  (ffirst (q (str "SELECT id FROM silta WHERE siltanimi = 'Oulujoen silta';"))))

(defn hae-pyhajoen-sillan-id []
  (ffirst (q (str "SELECT id FROM silta WHERE siltanimi = 'Pyhäjoen silta';"))))

(defn hae-oulun-alueurakan-2005-2012-urakoitsija []
  (ffirst (q (str "SELECT urakoitsija
                   FROM   urakka
                   WHERE  nimi = 'Oulun alueurakka 2005-2012'"))))

(defn hae-oulun-alueurakan-2014-2019-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Oulun alueurakka 2014-2019'"))))

(defn hae-tampereen-alueurakan-2017-2022-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Tampereen alueurakka 2017-2022'"))))

(defn hae-kemin-paallystysurakan-2019-2023-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Kemin päällystysurakka'"))))

(defn hae-aktiivinen-oulu-testi-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Aktiivinen Oulu Testi'"))))

(defn hae-oulun-maanteiden-hoitourakan-2019-2024-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Oulun MHU 2019-2024'"))))
(defn hae-oulun-maanteiden-hoitourakan-2019-2024-sopimus-id []
  (ffirst (q (str "SELECT id FROM sopimus where urakka = (SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Oulun MHU 2019-2024')"))))

(defn hae-iin-maanteiden-hoitourakan-2021-2026-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Iin MHU 2021-2026'"))))

(defn hae-iin-maanteiden-hoitourakan-2021-2026-sopimus-id []
  (ffirst (q (str "SELECT id FROM sopimus where urakka = (SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Iin MHU 2021-2026')"))))

(defn hae-iin-maanteiden-hoitourakan-lupaussitoutumisen-id []
  (ffirst (q (str "SELECT id
                   FROM   lupaus_sitoutuminen
                   WHERE  \"urakka-id\" = (SELECT id FROM urakka where nimi = 'Iin MHU 2021-2026')"))))


(defn hae-raahen-maanteiden-hoitourakan-2023-2028-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Raahen MHU 2023-2028'"))))

(defn hae-raahen-maanteiden-hoitourakan-2023-2028-sopimus-id []
  (ffirst (q (str "SELECT id FROM sopimus where urakka = (SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Raahen MHU 2023-2028')"))))



(defn hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi [toimenpidekoodi]
  (ffirst (q (str "SELECT id from toimenpideinstanssi where urakka = (select id FROM urakka WHERE  nimi = 'Oulun MHU 2019-2024') AND
                   toimenpide = (select id from toimenpide where koodi = '" toimenpidekoodi "');"))))

(defn hae-toimenpideinstanssi-id [urakka-id toimenpidekoodi]
  (ffirst (q (str "SELECT id from toimenpideinstanssi where urakka = " urakka-id " AND
                   toimenpide = (select id from toimenpide where koodi = '" toimenpidekoodi "');"))))

(defn hae-toimenpidekoodin-id [nimi koodi]
  (ffirst (q (str "SELECT id from tehtava where nimi = '" nimi "' AND emo = (select id from toimenpide WHERE koodi = '" koodi "');"))))

(defn hae-tehtavaryhman-id [nimi]
  (ffirst (q (str "SELECT id from tehtavaryhma where nimi = '" nimi "';"))))

(defn hae-yit-rakennus-id []
  (ffirst (q (str "SELECT id FROM organisaatio WHERE nimi = 'YIT Rakennus Oy'"))))

(defn hae-destia-id []
  (ffirst (q (str "SELECT id FROM organisaatio WHERE nimi = 'Destia Oy'"))))

(defn hae-kemin-aluerakennus-id []
  (ffirst (q (str "SELECT id FROM organisaatio WHERE nimi = 'Kemin Alueurakoitsija Oy'"))))

(defn hae-kajaanin-alueurakan-2014-2019-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Kajaanin alueurakka 2014-2019'"))))

(defn hae-vapaa-urakoitsija-id []
  (ffirst (q (str "SELECT id FROM organisaatio
                   WHERE tyyppi = 'urakoitsija'
                   AND id NOT IN (SELECT urakoitsija FROM urakka WHERE urakoitsija IS NOT NULL);"))))

(defn hae-vapaa-sopimus-id []
  (ffirst (q (str "SELECT id FROM sopimus
                   WHERE urakka IS NULL;"))))

(defn hae-vapaat-sopimus-idt []
  (map :id (q-map (str "SELECT id FROM sopimus
                   WHERE urakka IS NULL AND harjassa_luotu IS TRUE;"))))

(defn hae-vantaan-alueurakan-2014-2019-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Vantaan alueurakka 2009-2019'"))))

(defn hae-reimari-toimenpide-poiujen-korjaus []
  (ffirst (q (str "SELECT id
                   FROM   reimari_toimenpide
                   WHERE  lisatieto = 'Poijujen korjausta kuten on sovittu';"))))

(defn hae-kiintioon-kuuluva-reimari-toimenpide []
  (ffirst (q (str "SELECT id
                   FROM   reimari_toimenpide
                   WHERE  lisatieto = 'Kiintiöön kuuluva jutska'
                          AND \"kiintio-id\" IS NOT NULL;"))))

(defn hae-kiintio-siirtyneiden-poijujen-korjaus []
  (ffirst (q (str "SELECT id
                   FROM   vv_kiintio
                   WHERE  nimi = 'Siirtyneiden poijujen siirto';"))))

(defn hae-oulun-alueurakan-lampotila-hk-2014-2015 []
  (ffirst (q (str "SELECT id, urakka, alkupvm, loppupvm, keskilampotila, keskilampotila_1981_2010
                   FROM   lampotilat
                   WHERE  urakka = " @oulun-alueurakan-2014-2019-id "
                   AND alkupvm = '2014-10-01' AND loppupvm = '2015-09-30'"))))

(defn hae-merivayla-hallintayksikon-id []
  (ffirst (q (str "SELECT id
                   FROM   organisaatio
                   WHERE  nimi = 'Meriväylät'"))))

(defn hae-pohjois-pohjanmaan-hallintayksikon-id []
  (ffirst (q (str "SELECT id
                   FROM   organisaatio
                   WHERE  nimi = 'Pohjois-Pohjanmaa'"))))

(defn hae-oulun-alueurakan-toimenpideinstanssien-idt []
  (into [] (flatten (q (str "SELECT tpi.id
                  FROM   urakka u
                    JOIN toimenpideinstanssi tpi ON u.id = tpi.urakka
                  WHERE  u.nimi = 'Oulun alueurakka 2005-2012';")))))

(defn hae-oulun-alueurakan-talvihoito-tpi-id []
  (ffirst (q (str "SELECT id
                  FROM   toimenpideinstanssi
                  WHERE  nimi = 'Oulu Talvihoito TP 2014-2019';"))))

(defn hae-oulun-alueurakan-liikenneympariston-hoito-tpi-id []
  (ffirst (q (str "SELECT id
                  FROM   toimenpideinstanssi
                  WHERE  nimi = 'Oulu Liikenneympäristön hoito TP 2014-2019';"))))

(defn hae-toimenpideinstanssi-id-nimella [nimi]
  (ffirst (q (format "SELECT id
                      FROM   toimenpideinstanssi
                      WHERE  nimi = '%s'"
                     nimi))))

(defn hae-kittila-mhu-talvihoito-tpi-id []
  (hae-toimenpideinstanssi-id-nimella "Kittilä MHU Talvihoito TP"))

(defn hae-kittila-mhu-hallinnolliset-toimenpiteet-tp-id []
  (hae-toimenpideinstanssi-id-nimella "Kittilä MHU Hallinnolliset toimenpiteet TP"))

(defn hae-liikenneympariston-hoidon-toimenpidekoodin-id []
  (ffirst (q (str "SELECT id
  FROM toimenpide
  WHERE nimi = 'Liikenneympäristön hoito laaja TPI' AND taso = 3\nAND
  emo = (select id from toimenpide where taso = 2 AND nimi = 'Liikenneympäristön hoito');"))))

(defn hae-muhoksen-paallystysurakan-tpi-id []
  (ffirst (q (str "SELECT id
                   FROM   toimenpideinstanssi
                   WHERE  urakka = (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka')"))))

(defn hae-muhoksen-paallystysurakan-testikohteen-id []
  (ffirst (q (str "SELECT id FROM yllapitokohde WHERE nimi = 'Kuusamontien testi'"))))

(defn hae-muhoksen-paallystysurakan-testipaikkauskohteen-id []
  (ffirst (q (str "SELECT id FROM paikkauskohde WHERE nimi = 'Testikohde Muhoksen paallystysurakassa'"))))

(defn hae-oulun-tiemerkintaurakan-paasopimuksen-id []
  (ffirst (q (str "SELECT id
                   FROM   sopimus
                   WHERE  nimi = 'Oulun tiemerkinnän palvelusopimuksen pääsopimus 2017-2024'"))))

(defn hae-pudasjarven-alueurakan-paasopimuksen-id []
  (ffirst (q (str "(SELECT id FROM sopimus WHERE urakka =
                           (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012') AND paasopimus IS null)"))))

(defn hae-oulun-alueurakan-2005-2010-paasopimuksen-id []
  (ffirst (q (str "(SELECT id FROM sopimus WHERE urakka =
                           (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null)"))))
(defn hae-oulun-alueurakan-2014-2019-paasopimuksen-id []
  (ffirst (q (str "(SELECT id FROM sopimus WHERE urakka =
                           (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)"))))

(defn hae-kajaanin-alueurakan-2014-2019-paasopimuksen-id []
  (ffirst (q (str "(SELECT id FROM sopimus WHERE urakka =
                           (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019') AND paasopimus IS null)"))))

(defn hae-muhoksen-paallystysurakan-paasopimuksen-id []
  (ffirst (q (str "(SELECT id FROM sopimus WHERE urakka =
                           (SELECT id FROM urakka WHERE nimi='Muhoksen päällystysurakka') AND paasopimus IS null)"))))

(defn hae-muhoksen-paikkausurakan-paasopimuksen-id []
  (ffirst (q (str "(SELECT id FROM sopimus WHERE urakka =
                           (SELECT id FROM urakka WHERE nimi='Muhoksen paikkausurakka') AND paasopimus IS null)"))))

(defn hae-utajarven-paallystysurakan-paasopimuksen-id []
  (ffirst (q (str "(SELECT id FROM sopimus WHERE urakka =
                           (SELECT id FROM urakka WHERE nimi='Utajärven päällystysurakka') AND paasopimus IS null)"))))

(defn hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   urakka = (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka')
                   AND NOT EXISTS(SELECT id FROM paallystysilmoitus WHERE paallystyskohde = ypk.id)"))))

(defn hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE nimi = 'Ouluntie' AND
                   urakka = (SELECT id FROM urakka WHERE nimi = 'Utajärven päällystysurakka')
                   AND EXISTS(SELECT id FROM paallystysilmoitus WHERE paallystyskohde = ypk.id)
                   AND poistettu IS FALSE"))))

(defn hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   urakka = (SELECT id FROM urakka WHERE nimi = 'Utajärven päällystysurakka')
                   AND NOT EXISTS(SELECT id FROM paallystysilmoitus WHERE paallystyskohde = ypk.id)
                   AND poistettu IS FALSE"))))

(defn hae-tiemerkintaurakkaan-osoitettu-yllapitokohde [urakka-id]
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   suorittava_tiemerkintaurakka = " urakka-id ";"))))

(defn hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   nimi = 'Leppäjärven ramppi'
                   AND EXISTS(SELECT id FROM paallystysilmoitus WHERE paallystyskohde = ypk.id);"))))

(defn hae-yllapitokohde-kuusamontien-testi-jolta-puuttuu-paallystysilmoitus []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   nimi = 'Kuusamontien testi'
                   AND NOT EXISTS(SELECT id FROM paallystysilmoitus WHERE paallystyskohde = ypk.id);"))))

(defn hae-yllapitokohde-joka-ei-kuulu-urakkaan [urakka-id]
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE urakka != " urakka-id ";"))))

(defn hae-yllapitokohde-jonka-tiemerkintaurakka-suorittaa [tiemerkintaurakka-id]
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE suorittava_tiemerkintaurakka = " tiemerkintaurakka-id ";"))))

(defn hae-yllapitokohteen-id-nimella
  [kohteen-nimi]
  (let [query (str "SELECT id FROM yllapitokohde ypk
                   WHERE nimi = '" kohteen-nimi "';")]
    (ffirst (q query))))

(defn hae-lupaus-vaihtoehdot [lupaus-id]
  (q (str "SELECT id FROM lupaus_vaihtoehto WHERE \"lupaus-id\"=" lupaus-id ";")))

(defn hae-pot2-testi-idt []
  (let [{kohde-id :id pot2-id :pot2-id} (first
                                          (q-map (str "SELECT k.id,
                                                              p.id as \"pot2-id\"
                                                         FROM yllapitokohde k
                                                         JOIN paallystysilmoitus p ON p.paallystyskohde = k.id
                                                        WHERE nimi = 'Tärkeä kohde mt20'")))
        urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")]
    [kohde-id pot2-id urakka-id]))

(defn anna-lukuoikeus [kayttaja]
  (u (format "update kayttaja set api_oikeudet = ARRAY['luku'::apioikeus] WHERE kayttajanimi = '%s'" kayttaja)))

(defn anna-kirjoitusoikeus [kayttaja]
  (u (format "update kayttaja set api_oikeudet = ARRAY['kirjoitus'::apioikeus] WHERE kayttajanimi = '%s'" kayttaja)))

(defn anna-analytiikkaoikeus [kayttaja]
  (u (format "update kayttaja set api_oikeudet = ARRAY['analytiikka'::apioikeus] WHERE kayttajanimi = '%s'" kayttaja)))

(defn anna-tielupaoikeus [kayttaja]
    (u (format "update kayttaja set api_oikeudet = ARRAY['tielupa'::apioikeus] WHERE kayttajanimi = '%s'" kayttaja)))

(defn poista-kayttajan-api-oikeudet [kayttaja]
  (u (format "update kayttaja set api_oikeudet = NULL WHERE kayttajanimi = '%s'" kayttaja)))

(defn asenna-pot-lahetyksen-tila [kohde-id pot2-id]
  (u (str "UPDATE paallystysilmoitus
              SET paatos_tekninen_osa = 'hyvaksytty',
                  tila = 'valmis'
            WHERE paallystyskohde = " kohde-id ";"))
  (u (str "UPDATE yllapitokohde
              SET velho_lahetyksen_aika = NULL,
                  velho_lahetyksen_tila = 'ei-lahetetty',
                  velho_lahetyksen_vastaus = NULL
              WHERE id = " kohde-id ";"))
  (u (str "UPDATE pot2_paallystekerros
              SET velho_lahetyksen_aika = NULL,
                  velho_rivi_lahetyksen_tila = 'ei-lahetetty',
                  velho_lahetyksen_vastaus = NULL
              WHERE jarjestysnro = 1 AND
                    pot2_id = " pot2-id ";"))
  (u (str "UPDATE pot2_alusta
              SET velho_lahetyksen_aika = NULL,
                  velho_rivi_lahetyksen_tila = 'ei-lahetetty',
                  velho_lahetyksen_vastaus = NULL
              WHERE pot2_id = " pot2-id ";")))

(defn poista-paallystysilmoitus-paallystyskohtella [paallystyskohde-id]
  (u (str "DELETE FROM pot2_paallystekerros
            WHERE pot2_id = (SELECT id FROM paallystysilmoitus
                              WHERE paallystyskohde = " paallystyskohde-id ");"))
  (u (str "DELETE FROM pot2_alusta
            WHERE pot2_id = (SELECT id FROM paallystysilmoitus
                              WHERE paallystyskohde = " paallystyskohde-id ");"))
  (u (str "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " paallystyskohde-id ";")))

(defn pura-tr-osoite [[numero aosa aet losa loppuet kaista ajorata]]
  {:numero numero
   :aosa aosa
   :aet aet
   :losa losa
   :loppuet loppuet
   :kaista kaista
   :ajorata ajorata})

(defn hae-yllapitokohteen-tr-osoite [kohde-id]
  (pura-tr-osoite (first (q (str "SELECT tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
                                         tr_kaista, tr_ajorata
                                  FROM yllapitokohde WHERE id = " kohde-id ";")))))

(defn hae-yllapitokohteen-kohdeosien-tr-osoitteet [kohde-id]
  (map
    pura-tr-osoite
    (q (str "SELECT tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_kaista, tr_ajorata
             FROM yllapitokohdeosa WHERE yllapitokohde = " kohde-id ";"))))

(defn pvm-vali-sql-tekstina
  [sarakkeen-nimi between-str]
  (str " AND " sarakkeen-nimi
       " BETWEEN " between-str))

;; Määritellään käyttäjiä, joita testeissä voi käyttää
;; HUOM: näiden pitää täsmätä siihen mitä testidata.sql tiedostossa luodaan.

(defn hae-testi-kayttajan-tiedot [{:keys [etunimi sukunimi roolit]}]
  (let [kayttajan-tiedot (zipmap [:id :etunimi :sukunimi :kayttajanimi :organisaatio :sahkoposti]
                                 (first (q (str "SELECT id, etunimi, sukunimi, kayttajanimi, organisaatio, sahkoposti FROM kayttaja WHERE etunimi='" etunimi "' AND sukunimi='" sukunimi "';"))))
        kayttajan-organisaation-tiedot (when (:organisaatio kayttajan-tiedot)
                                         (zipmap [:id :tyyppi :nimi]
                                                 (first (q (str "SELECT id, tyyppi, nimi FROM organisaatio WHERE id=" (:organisaatio kayttajan-tiedot) ";")))))
        kayttajan-urakkaroolit (when (:id kayttajan-tiedot)
                                 (reduce (fn [tulos [urakka rooli]]
                                           (update tulos urakka #(if %
                                                                   (conj % rooli)
                                                                   #{rooli})))
                                         {} (q (str "SELECT urakka, rooli FROM kayttaja_urakka_rooli WHERE kayttaja=" (:id kayttajan-tiedot) ";"))))
        kayttajan-organisaatioroolit (when (:id kayttajan-tiedot)
                                       (reduce (fn [tulos [organisaatio rooli]]
                                                 (update tulos organisaatio #(if %
                                                                               (conj % rooli)
                                                                               #{rooli})))
                                               {} (q (str "SELECT organisaatio, rooli FROM kayttaja_organisaatio_rooli WHERE kayttaja=" (:id kayttajan-tiedot) ";"))))
        organisaation-urakat (when (:organisaatio kayttajan-tiedot)
                               (into #{} (apply concat (q (str "SELECT id FROM urakka WHERE urakoitsija=" (:organisaatio kayttajan-tiedot))))))]
    (assoc kayttajan-tiedot :organisaatio (or kayttajan-organisaation-tiedot {})
                            :roolit (or roolit #{})
                            :urakkaroolit (or kayttajan-urakkaroolit {})
                            :organisaatioroolit (or kayttajan-organisaatioroolit {})
                            :organisaation-urakat (or organisaation-urakat #{}))))

(defn hae-paikkauskohde-tyomenetelmat []
  (q "select id, nimi, lyhenne from paikkauskohde_tyomenetelma;"))

;; id:1 Tero Toripolliisi, POP ELY aluevastaava

(def +kayttaja-tero+ (hae-testi-kayttajan-tiedot {:etunimi "Tero" :sukunimi "Toripolliisi" :roolit #{"ELY_Urakanvalvoja"}}))

;; id:2 Järjestelmävastuuhenkilö

(def +kayttaja-jvh+ (hae-testi-kayttajan-tiedot {:etunimi "Jalmari" :sukunimi "Järjestelmävastuuhenkilö" :roolit #{"Jarjestelmavastaava"}}))

;; Organisaation 14 = Destian urakoitsija
(def +kayttaja-uuno+ (hae-testi-kayttajan-tiedot {:etunimi "Uuno" :sukunimi "Urakoitsija"}))

(def +kayttaja-yit_uuvh+ (hae-testi-kayttajan-tiedot {:etunimi "Yitin" :sukunimi "Urakkavastaava"}))

(def +kayttaja-ulle+ (hae-testi-kayttajan-tiedot {:etunimi "Ulle" :sukunimi "Urakoitsija"}))

(def +kayttaja-vastuuhlo-muhos+ (hae-testi-kayttajan-tiedot {:etunimi "Antero" :sukunimi "Asfalttimies"}))
(def +kayttaja-vastuuhlo-porvoo+ (hae-testi-kayttajan-tiedot {:etunimi "Veeti" :sukunimi "Velmu"}))

(def +kayttaja-paakayttaja-skanska+ (hae-testi-kayttajan-tiedot {:etunimi "Pekka" :sukunimi "Pääjehu"}))

(def +kayttaja-laadunvalvoja-kemi+ (hae-testi-kayttajan-tiedot {:etunimi "Keppi" :sukunimi "Laatujärvi" :roolit #{"laadunvalvoja"}}))

;; Sepolla ei ole oikeutta mihinkään. :(

(def +kayttaja-seppo+ (hae-testi-kayttajan-tiedot {:etunimi "Seppo" :sukunimi "Taalasmalli"}))

(def +livi-jarjestelma-kayttaja+
  {:id 14
   :kayttajanimi "livi"
   :jarjestelma true})

(def +kayttaja-urakan-vastuuhenkilo+
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)]
    {:organisaation-urakat #{urakka-id}
     :sahkoposti nil
     :kayttajanimi "yit_uuvh"
     :puhelin nil
     :sukunimi "Vastuuhenkilö"
     :roolit #{}
     :organisaatioroolit {}
     :id 7
     :etunimi "Yitin"
     :organisaatio {:id 11
                    :nimi "YIT Rakennus Oy"
                    :tyyppi "urakoitsija"}
     :urakkaroolit {urakka-id #{"vastuuhenkilo"}}}))

(defn tietokanta-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (testit))

(defn urakkatieto-alustus! []
  (reset! testikayttajien-lkm (hae-testikayttajat))
  (reset! oulun-alueurakan-2005-2010-id (hae-urakan-id-nimella "Oulun alueurakka 2005-2012"))
  (reset! oulun-alueurakan-2014-2019-id (hae-oulun-alueurakan-2014-2019-id))
  (reset! tampereen-alueurakan-2017-2022-id (hae-tampereen-alueurakan-2017-2022-id))
  (reset! kemin-alueurakan-2019-2023-id (hae-kemin-paallystysurakan-2019-2023-id))
  (reset! oulun-maanteiden-hoitourakan-2019-2024-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id))
  (reset! oulun-maanteiden-hoitourakan-2019-2024-sopimus-id (hae-oulun-maanteiden-hoitourakan-2019-2024-sopimus-id))
  (reset! kajaanin-alueurakan-2014-2019-id (hae-kajaanin-alueurakan-2014-2019-id))
  (reset! vantaan-alueurakan-2014-2019-id (hae-vantaan-alueurakan-2014-2019-id))
  (reset! oulun-alueurakan-lampotila-hk-2014-2015 (hae-oulun-alueurakan-lampotila-hk-2014-2015))
  (reset! pohjois-pohjanmaan-hallintayksikon-id (hae-pohjois-pohjanmaan-hallintayksikon-id))
  (reset! muhoksen-paallystysurakan-id (hae-urakan-id-nimella "Muhoksen päällystysurakka"))
  (reset! muhoksen-paallystysurakan-paasopimuksen-id (hae-muhoksen-paallystysurakan-paasopimuksen-id))
  (reset! muhoksen-paikkausurakan-id (hae-urakan-id-nimella "Muhoksen paikkausurakka"))
  (reset! muhoksen-paikkausurakan-paasopimuksen-id (hae-muhoksen-paikkausurakan-paasopimuksen-id))
  (reset! oulun-alueurakan-2005-2010-paasopimuksen-id (hae-oulun-alueurakan-2005-2010-paasopimuksen-id))
  (reset! oulun-alueurakan-2014-2019-paasopimuksen-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id))
  (reset! kajaanin-alueurakan-2014-2019-paasopimuksen-id (hae-kajaanin-alueurakan-2014-2019-paasopimuksen-id))
  (reset! pudasjarven-alueurakan-id (hae-urakan-id-nimella "Pudasjärven alueurakka 2007-2012"))
  (reset! yit-rakennus-id (hae-yit-rakennus-id))
  (reset! destia-id (hae-destia-id))
  (reset! kemin-aluerakennus-id (hae-kemin-aluerakennus-id))
  (reset! paikkauskohde-tyomenetelmat (hae-paikkauskohde-tyomenetelmat))
  (reset! iin-maanteiden-hoitourakan-2021-2026-id (hae-iin-maanteiden-hoitourakan-2021-2026-id))
  (reset! iin-maanteiden-hoitourakan-lupaussitoutumisen-id (hae-iin-maanteiden-hoitourakan-lupaussitoutumisen-id))
  (reset! raahen-maanteiden-hoitourakan-2023-2028-id (hae-raahen-maanteiden-hoitourakan-2023-2028-id))
  (reset! raahen-maanteiden-hoitourakan-2023-2028-sopimus-id (hae-raahen-maanteiden-hoitourakan-2023-2028-sopimus-id)))

(defn urakkatieto-lopetus! []
  (reset! oulun-alueurakan-2005-2010-id nil)
  (reset! oulun-alueurakan-2005-2010-paasopimuksen-id nil)
  (reset! pudasjarven-alueurakan-id nil))

(defn urakkatieto-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (urakkatieto-alustus!)
  (testit)
  (urakkatieto-lopetus!))

(use-fixtures :once urakkatieto-fixture)

(defn oulun-2005-urakan-tilaajan-urakanvalvoja []
  {:sahkoposti "ely@example.org", :kayttajanimi "ely-oulun-urakanvalvoja",
   :roolit #{"ELY_Urakanvalvoja"}, :id 417,
   :organisaatio {:id 10, :nimi "Pohjois-Pohjanmaa", :tyyppi "hallintayksikko"},
   :organisaation-urakat #{@oulun-alueurakan-2005-2010-id}
   :urakkaroolit {@oulun-alueurakan-2005-2010-id, #{"ELY_Urakanvalvoja"}}})

(defn oulun-2014-urakan-tilaajan-urakanvalvoja []
  {:sahkoposti "ely@example.org", :kayttajanimi "ely-oulun-urakanvalvoja",
   :roolit #{"ELY_Urakanvalvoja"}, :id 417,
   :organisaatio {:id 10, :nimi "Pohjois-Pohjanmaa", :tyyppi "hallintayksikko"},
   :organisaation-urakat #{@oulun-alueurakan-2014-2019-id}
   :urakkaroolit {@oulun-alueurakan-2014-2019-id, #{"ELY_Urakanvalvoja"}}})

(defn oulun-2005-urakan-urakoitsijan-urakkavastaava []
  {:sahkoposti "yit_uuvh@example.org", :kayttajanimi "yit_uuvh", :puhelin 43363123, :sukunimi "Urakkavastaava",
   :roolit #{}, :id 17, :etunimi "Yitin",
   :organisaatio {:id 10, :nimi "YIT Rakennus Oy", :tyyppi "urakoitsija"},
   :organisaation-urakat #{@oulun-alueurakan-2005-2010-id}
   :urakkaroolit {@oulun-alueurakan-2005-2010-id #{"vastuuhenkilo"}}})

(defn oulun-2014-urakan-urakoitsijan-urakkavastaava []
  {:sahkoposti "yit_uuvh@example.org", :kayttajanimi "yit_uuvh", :puhelin 43363123, :sukunimi "Urakkavastaava",
   :roolit #{}, :id 17, :etunimi "Yitin",
   :organisaatio {:id @yit-rakennus-id, :nimi "YIT Rakennus Oy", :tyyppi "urakoitsija"},
   :organisaation-urakat #{@oulun-alueurakan-2014-2019-id}
   :organisaatioroolit {}
   :urakkaroolit {@oulun-alueurakan-2014-2019-id #{"vastuuhenkilo"}}})

(defn oulun-2019-urakan-urakoitsijan-urakkavastaava []
  {:sahkoposti "yit_uuvh@example.org", :kayttajanimi "yit_uuvh", :puhelin 43363123, :sukunimi "Urakkavastaava",
   :roolit #{}, :id 17, :etunimi "Yitin",
   :organisaatio {:id @yit-rakennus-id, :nimi "YIT Rakennus Oy", :tyyppi "urakoitsija"},
   :organisaation-urakat #{@oulun-maanteiden-hoitourakan-2019-2024-id}
   :organisaatioroolit {}
   :urakkaroolit {@oulun-maanteiden-hoitourakan-2019-2024-id #{"vastuuhenkilo"}}})

(defn oulun-2014-urakan-urakoitsijan-laadunvalvoja []
  {:sahkoposti "yit_uuvh@example.org", :kayttajanimi "yit_uuvh", :puhelin 43363123, :sukunimi "Urakkavastaava",
   :roolit #{}, :id 17, :etunimi "Yitin",
   :organisaatio {:id @yit-rakennus-id, :nimi "YIT Rakennus Oy", :tyyppi "urakoitsija"},
   :organisaation-urakat #{@oulun-alueurakan-2014-2019-id}
   :organisaatioroolit {}
   :urakkaroolit {@oulun-alueurakan-2014-2019-id #{"laadunvalvoja"}}})

(defn kemin-alueurakan-2019-2023-laadunvalvoja []
  {:sahkoposti "keppi.laatujarvih@example.com", :kayttajanimi "KeminLaatu", :puhelin 123123123, :sukunimi "Laatujärvi",
   :roolit #{"Laadunvalvoja"}, :id 18, :etunimi "Keppi",
   :organisaatio {:id @kemin-aluerakennus-id, :nimi "Kemin Aluerakennus Oy", :tyyppi "urakoitsija"},
   :organisaation-urakat #{@kemin-alueurakan-2019-2023-id}
   :organisaatioroolit {} #_{@kemin-aluerakennus-id #{"laadunvalvoja"}}
   :urakkaroolit {@kemin-alueurakan-2019-2023-id #{"Laadunvalvoja"}}})

(defn kemin-alueurakan-2019-2023-urakan-tilaajan-urakanvalvoja []
  {:sahkoposti "ely@example.org", :kayttajanimi "ely-kemin-urakanvalvoja",
   :roolit #{"ELY_Urakanvalvoja"}, :id 417,
   :organisaatio {:id 10, :nimi "Pohjois-Pohjanmaa", :tyyppi "hallintayksikko"},
   :organisaation-urakat #{@kemin-alueurakan-2019-2023-id}
   :organisaatioroolit {}
   :urakkaroolit {@kemin-alueurakan-2019-2023-id, #{"ELY_Urakanvalvoja"}}})

(defn kemin-alueurakan-2019-2023-paakayttaja []
  {:sahkoposti "keppi.paajarvi@example.com", :kayttajanimi "KeminPaa", :puhelin 123123123, :sukunimi "Pääjärvi",
   :roolit #{"Paakayttaja"}, :id 18, :etunimi "Keppi",
   :organisaatio {:id @kemin-aluerakennus-id :nimi "Kemin Aluerakennus Oy", :tyyppi "urakoitsija"},
   :organisaation-urakat #{@kemin-alueurakan-2019-2023-id}
   :organisaatioroolit {}
   :urakkaroolit {@kemin-alueurakan-2019-2023-id #{"Paakayttaja"}}})

(defn iin-2021-urakan-urakoitsijan-urakkavastaava []
  {:sahkoposti "seppo.sankarih@example.org", :kayttajanimi "yit_uuvh", :puhelin 43363123, :sukunimi "Urakkavastaava",
   :roolit #{}, :id 17, :etunimi "Yitin",
   :organisaatio {:id @yit-rakennus-id, :nimi "YIT Rakennus Oy", :tyyppi "urakoitsija"},
   :organisaation-urakat #{@iin-maanteiden-hoitourakan-2021-2026-id}
   :organisaatioroolit {}
   :urakkaroolit {(hae-urakan-id-nimella "Iin MHU 2021-2026") #{"vastuuhenkilo"}}})

(defn iin-2021-urakan-urakoitsijan-paakayttaja []
  {:sahkoposti "ismo.isokenkainen@example.com", :kayttajanimi "IinPaa", :puhelin 123125123, :sukunimi "Isokenkäinen",
   :roolit #{"Paakayttaja"}, :id 85, :etunimi "Ismo",
   :organisaatio {:id @yit-rakennus-id :nimi "YIT Rakennus Oy", :tyyppi "urakoitsija"},
   :organisaation-urakat #{@iin-maanteiden-hoitourakan-2021-2026-id @oulun-maanteiden-hoitourakan-2019-2024-id}
   :organisaatioroolit {@yit-rakennus-id #{"Paakayttaja"}}
   :urakkaroolit {}})

(defn raahen-2023-urakan-urakoitsijan-urakkavastaava []
  {:sahkoposti "ulle.urakoitisja@example.org", :kayttajanimi "ulle", :puhelin 4324214, :sukunimi "Urakkavastaava",
   :roolit #{}, :id 17, :etunimi "Destian",
   :organisaatio {:id @destia-id, :nimi "Destia Oy", :tyyppi "urakoitsija"},
   :organisaation-urakat #{@raahen-maanteiden-hoitourakan-2023-2028-id}
   :organisaatioroolit {}
   :urakkaroolit {(hae-urakan-id-nimella "Raahen MHU 2023-2028") #{"vastuuhenkilo"}}})

(defn raahen-2023-urakan-rakennuttajakonsultti []
  {:sahkoposti "Kimmo.Konsultti@example.org", :kayttajanimi "kipe", :puhelin 2144234, :sukunimi "Konsultti",
   :roolit #{}, :id 117, :etunimi "Kimmo",
   :organisaatio {:id 14, :nimi "Tiekyylät Oy", :tyyppi "tilaajan-konsultti"}
   :organisaation-urakat #{}
   :organisaatioroolit {}
   :urakkaroolit {(hae-urakan-id-nimella "Raahen MHU 2023-2028") #{"Rakennuttajakonsultti"}}})

(defn raahen-2023-urakan-tilaajan-urakanvalvoja []
  {:sahkoposti "Usko.urakanvalvoja@example.org", :kayttajanimi "tero", :puhelin 2144234, :sukunimi "Valvoja",
   :roolit #{}, :id 17, :etunimi "Urakan",
   :organisaatio {:id 12, :nimi "Pohjois-Pohjanmaa", :tyyppi "hallintayksikko"}
   :organisaation-urakat #{@iin-maanteiden-hoitourakan-2021-2026-id @raahen-maanteiden-hoitourakan-2023-2028-id}
   :organisaatioroolit {}
   :urakkaroolit {(hae-urakan-id-nimella "Raahen MHU 2023-2028") #{"ELY_Urakanvalvoja"}}})

(defn lapin-paallystyskohteiden-tilaaja []
  {:sahkoposti "tilaaja@example.org", :kayttajanimi "tilaaja",
   :roolit #{"ELY_Urakanvalvoja"}, :id 20,
   :organisaatio {:id 13, :nimi "Lappi", :tyyppi "hallintayksikko"},
   :organisaation-urakat #{@kemin-alueurakan-2019-2023-id}
   :organisaatioroolit {}
   :urakkaroolit {@kemin-alueurakan-2019-2023-id, #{"ELY_Urakanvalvoja"}}})

(defn ei-ole-oulun-urakan-urakoitsijan-urakkavastaava []
  {:sahkoposti "yit_uuvh@example.org", :kayttajanimi "yit_uuvh", :puhelin 43363123, :sukunimi "Urakkavastaava",
   :roolit #{}, :id 17, :etunimi "Yitin",
   :organisaatio {:id 10, :nimi "YIT Rakennus Oy", :tyyppi "urakoitsija"},
   :organisaation-urakat #{234234324234}
   :urakkaroolit {234234324234 #{"vastuuhenkilo"}}})

;; Selkeä rajoite on, että tässä testataan usein vain ensimmäistä, tai tietyllä
;; indeksillä löytyvää. Yleensä pitäisi tieten oikeanlainenkin rivi löytyä, mutta
;; teoriassa on hyvinkin mahdollista, että huonolla tuurilla ei löydy.
(defn sisaltaa-ainakin-sarakkeet?
  ([tulos sarakkeet] (sisaltaa-ainakin-sarakkeet? tulos sarakkeet true))
  ([tulos sarakkeet assertoi-kaikki?]
   (let [tulos (if (map? tulos)
                 tulos
                 (first tulos))]
     (every?
       #(let [loytyi? (not= ::ei-loydy
                            (get-in tulos (if (vector? %)
                                            %
                                            [%]) ::ei-loydy))]
          (when assertoi-kaikki?
            (assert loytyi? (str "Polku " (pr-str %) " EI löydy tuloksesta! " (pr-str (first tulos)))))
          loytyi?)
       sarakkeet))))

(defn oikeat-sarakkeet-palvelussa?
  "Tarkastaa sisältääkö palvelun palauttama tietorakenne ainakin annetut avaimet.
  Parametrit:
  - Sarakkeet: vektori, joka sisältää keywordeja, tai vektoreita keywordeja ja indeksejä.
  - Palvelu: kutsuttava palvelu
  Vaihtoehtoiset parametrit:
  - Parametrit: osa palveluista tarvitsee parametreja, osa ei. Anna mapissa, kuten normaalisti.
  - Kayttaja: Defaulttina käytetään +kayttaja-jvh+. Jos haluat antaa käyttäjän mutta palvelu
    ei käytä parametreja, anna parametrien tilalla nil.

  Palvelimen palauttama tietorakenne voi olla joko map tai vektori - ylimmällä tasolla tällä ei
  ole väliä. Tietorakenteen sisällä erolla on merkitystä. Esimerkiksi seuraavia tietorakenteita:

  [{:id 1 :henkilo {:id 1} :toimenpiteet [{:id 1} {:id 2}]}]

  {:id 1 :henkilo {:id 1} :toimenpiteet [{:id 1} {:id 2}]}

  testattaisiin tällaisilla sarakkeet-vektorilla:
  [:id [:henkilo :id] [:toimenpiteet 0 :id]]
  "
  ([sarakkeet palvelu]
   (oikeat-sarakkeet-palvelussa? sarakkeet palvelu nil +kayttaja-jvh+))

  ([sarakkeet palvelu parametrit]
   (oikeat-sarakkeet-palvelussa? sarakkeet palvelu parametrit +kayttaja-jvh+))

  ([sarakkeet palvelu parametrit kayttaja]
   (oikeat-sarakkeet-palvelussa? sarakkeet palvelu parametrit kayttaja true))
  ([sarakkeet palvelu parametrit kayttaja assertoi-kaikki?]
   (let [vastaus (if parametrit
                   (kutsu-palvelua (:http-palvelin jarjestelma) palvelu (or kayttaja +kayttaja-jvh+) parametrit)
                   (kutsu-palvelua (:http-palvelin jarjestelma) palvelu (or kayttaja +kayttaja-jvh+)))]
     (log/debug "Tarkistetaan sarakkeet vastauksesta:" (pr-str vastaus))
     (if (sisaltaa-ainakin-sarakkeet? vastaus sarakkeet assertoi-kaikki?)
       true
       (let [tulos (if (map? vastaus)
                     vastaus
                     (first vastaus))]
         (log/error "Vastaus poikkeaa annetusta mallista. Vastaus: " (pr-str vastaus)
                    "\nPuuttuvat polut: " (pr-str
                                            (keep (fn [sarake]
                                                    (let [sarake (if (vector? sarake)
                                                                   sarake
                                                                   [sarake])]
                                                      (when (= ::ei-loydy
                                                               (get-in tulos sarake ::ei-loydy))
                                                        sarake))) sarakkeet)))
         false)))))

(def portti nil)
(def urakka nil)

(def harja-tarkkailija nil)
(def ^:dynamic *uudelleen-kaynnistaja-mukaan?* false)

(defn lopeta-harja-tarkkailija! []
  (alter-var-root #'harja-tarkkailija component/stop))

(defn pystyta-harja-tarkkailija! []
  (alter-var-root #'harja-tarkkailija
                  (fn [tarkkailija]
                    (when tarkkailija
                      (component/stop tarkkailija))
                    (component/start
                      (component/system-map
                        :db-event (event-tietokanta/luo-tietokanta testitietokanta)
                        :klusterin-tapahtumat (component/using
                                                (tapahtumat/luo-tapahtumat {:loop-odotus 100})
                                                {:db :db-event})
                        :tapahtuma (component/using
                                     (tapahtuma/->Tapahtuma)
                                     [:klusterin-tapahtumat :rajapinta])
                        :rajapinta (rajapinta/->Rajapintakasittelija)
                        :uudelleen-kaynnistaja (if *uudelleen-kaynnistaja-mukaan?*
                                                 (uudelleen-kaynnistaja/->UudelleenKaynnistaja {:itmf {:paivitystiheys-ms (* 1000 10)}}
                                                                                               (atom nil))
                                                 (reify component/Lifecycle
                                                   (start [this]
                                                     this)
                                                   (stop [this]
                                                     this))))))))

(defn jms-kasittely [kuuntelijoiden-lopettajat]
  (when *aloitettavat-jmst*
    (let [jms-kaynnistaminen! (fn []
                                (when (contains? *aloitettavat-jmst* "itmf")
                                  (<!! (jms/aloita-jms (:itmf jarjestelma))))
                                (when *jms-kaynnistetty-fn*
                                  (*jms-kaynnistetty-fn*)))]
      (if *lisattavia-kuuntelijoita?*
        (reset! sonja-aloitus-go
                (go (let [[jms-kuuntelijat _] (alts! [*lisattavat-kuuntelijat*
                                                      (timeout 5000)])
                          itmf-kuuntelijat (get jms-kuuntelijat "itmf")]
                      (when (and itmf-kuuntelijat (map? itmf-kuuntelijat))
                        (doseq [[kanava f] itmf-kuuntelijat]
                          (swap! kuuntelijoiden-lopettajat conj (jms/kuuntele! (:itmf jarjestelma) kanava f))))
                      (jms-kaynnistaminen!))))
        (jms-kaynnistaminen!)))))

(defn tietokantakomponentti-fixture [testit]
  #_(pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)))))
  (testit)
  (alter-var-root #'jarjestelma component/stop)
  #_(lopeta-harja-tarkkailija!))

(defmacro laajenna-integraatiojarjestelmafixturea
  "Integraatiotestifixturen rungon rakentava makro. :db, :http-palvelin ja :integraatioloki
  löytyy valmiina. Body menee suoraan system-mapin jatkoksi"
  [kayttaja & omat]
  `(fn [testit#]
     (pudota-ja-luo-testitietokanta-templatesta)
     (alter-var-root #'portti (fn [_#] (arvo-vapaa-portti)))
     (pystyta-harja-tarkkailija!)
     (swap! a/pois-kytketyt-ominaisuudet conj :sonja-sahkoposti :toteumatyokalu) ;; Pakota sonja-sahkoposti pois käytöstä
     (alter-var-root #'jarjestelma
                     (fn [_#]
                       (component/start
                         (component/system-map
                           :db (tietokanta/luo-tietokanta testitietokanta)
                           :db-replica (tietokanta/luo-tietokanta testitietokanta)

                           :todennus (component/using
                                       (todennus/http-todennus)
                                       [:db])
                           :http-palvelin (component/using
                                            (http/luo-http-palvelin portti true)
                                            [:db :todennus])
                           :integraatioloki (component/using
                                              (integraatioloki/->Integraatioloki nil)
                                              [:db])

                           :liitteiden-hallinta (component/using
                                                  (liitteet/->Liitteet nil)
                                                  [:db])

                           ~@omat))))
     (when *kaynnistyksen-jalkeen-hook*
       (*kaynnistyksen-jalkeen-hook*))
     (alter-var-root #'urakka
                     (fn [_#]
                       (ffirst (q (str "SELECT id FROM urakka WHERE urakoitsija=(SELECT organisaatio FROM kayttaja WHERE kayttajanimi='" ~kayttaja "') "
                                       " AND tyyppi='hoito'::urakkatyyppi ORDER BY id")))))
     ;; aloita-sonja palauttaa kanavan.
     (binding [*lisattavat-kuuntelijat* (chan)]
       (let [kuuntelijoiden-lopettajat# (atom [])]
         (jms-kasittely kuuntelijoiden-lopettajat#)
         (testit#)
         (when (not (empty? @kuuntelijoiden-lopettajat#))
           (doseq [lopetus-fn# @kuuntelijoiden-lopettajat#]
             (lopetus-fn#)))))
     (when *ennen-sulkemista-hook*
       (*ennen-sulkemista-hook*))
     (alter-var-root #'jarjestelma component/stop)
     (lopeta-harja-tarkkailija!)))

(defn =marginaalissa?
  "Palauttaa ovatko kaksi lukua samat virhemarginaalin sisällä. Voi käyttää esim. doublelaskennan
  tulosten vertailussa. Oletusmarginaali on 0.05"
  ([eka toka] (=marginaalissa? eka toka 0.05))
  ([eka toka marginaali]
   (< (Math/abs (double (- eka toka))) marginaali)))

(defn- =ts [d1 d2]
  (let [ts1 (and d1 (.getTime d1))
        ts2 (and d2 (.getTime d2))]
    (= ts1 ts2)))

(defn tarkista-map-arvot
  "Tarkistaa, että mäpissä on oikeat arvot. Numeroita vertaillaan =marginaalissa? avulla, muita
  = avulla. Tarkistaa myös, että kaikki arvot ovat olemassa. Odotetussa mäpissa saa olla
  ylimääräisiä avaimia."
  [odotetut saadut]
  (doseq [k (keys odotetut)
          :let [odotettu-arvo (get odotetut k)
                saatu-arvo (get saadut k ::ei-olemassa)]]
    (cond
      (= saatu-arvo ::ei-olemassa)
      (is false (str "Odotetussa mäpissä ei arvoa avaimelle: " k
                     ", odotettiin arvoa: " odotettu-arvo))

      (and (number? odotettu-arvo) (number? saatu-arvo))
      (is (=marginaalissa? odotettu-arvo saatu-arvo)
          (str "Saatu arvo avaimelle " k " ei marginaalissa, odotettu: "
               odotettu-arvo " (" (type odotettu-arvo) "), saatu: "
               saatu-arvo " (" (type saatu-arvo) ")"))

      (instance? java.util.Date odotettu-arvo)
      (is (=ts odotettu-arvo saatu-arvo)
          (str "Odotettu date arvo avaimelle " k " ei ole millisekunteina sama, odotettu: "
               odotettu-arvo " (" (type odotettu-arvo) "), saatu: "
               saatu-arvo " (" (type saatu-arvo) ")"))

      :default
      (is (= odotettu-arvo saatu-arvo)
          (str "Saatu arvo avaimelle " k " ei täsmää, odotettu: " odotettu-arvo
               " (" (type odotettu-arvo)
               "), saatu: " saatu-arvo " (" (type odotettu-arvo) ")")))))

(def suomen-aikavyohyke (t/time-zone-for-id "EET"))

(defn paikallinen-aika [dt]
  (-> dt
      tc/from-sql-date
      (t/to-time-zone suomen-aikavyohyke)))

(defn q-sanktio-leftjoin-laatupoikkeama [sanktio-id]
  (first (q-map
           "SELECT s.id, s.maara as summa, s.poistettu, s.perintapvm, s.sakkoryhma as laji,
                   lp.id as lp_id, lp.aika as lp_aika, lp.poistettu as lp_poistettu
              FROM sanktio s
                   LEFT JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id
             WHERE s.id = " sanktio-id ";")))

(defn luo-pdf-bytes [fo]
  (let [ff (#'pdf-vienti/luo-fop-factory)]
    (with-open [out (java.io.ByteArrayOutputStream.)]
      (#'pdf-vienti/hiccup->pdf ff fo out)
      (.toByteArray out))))

(defn- gatling-kutsu [kutsu]
  (go (let [tulos (<! (go (log/with-level :warn (kutsu))))]
        (if (and (some? tulos) (not-empty tulos))
          true
          false))))

(defn gatling-onnistuu-ajassa?
  "Ajaa nimetyn gatling-simulaation, ja kertoo, valmistuivatko skenaariot aikarajan sisällä.

  Kiinnostavat optiot ovat:
  - :timeout-in-ms  Kuinka pitkään jokainen pyyntö saa maksimissaan kestää.
  - :concurrency    Montako kyselyä ajetaan rinnakkain. Oletuksena skenaarioiden lukumäärä.
                    Käytännössä, jos kyselyn voi ajaa samoilla parametreilla monta kertaa,
                    anna tämä luku. Jos joudut antamaan monta kyselyä eri parametreilla,
                    tätä ei ole tarpeen antaa.
  - :aja-raportti?  Oletuksena vain jenkinsillä halutaan koostaa html-raportteja.

  Kutsuja voi antaa niin monta kuin haluaa. Jos kutsuja antaa monta, ne ajetaan rinnakkain.

  Esim:
  (is (gatling-onnistuu-ajassa?
        \"Hae jutut\"
        {:concurrency 10
        :timeout-in-ms 100}
        #(kutsu-palvelua :hae-jutut +jvh+ {:id 1})))"
  [simulaation-nimi {:keys [aja-raportti?] :as opts} & kutsut]
  ;; Tämä toteutus ohjaa tarkoituksella käyttöä tiettyyn suuntaan.
  ;; Jos tarvitaan hienojakoisempaa toiminnallisuutta, esim monivaiheisia skenaarioita,
  ;; joita ajetaan eri painoarvoilla, niin parempi kutsua gatlingia suoraan, tapauskohtaisesti.
  (let [simulaatio {:name simulaation-nimi
                    :scenarios
                    (keep-indexed
                      (fn [i kutsu]
                        {:name (str "Skenaario #" i)
                         :steps [{:name "Askel 1"
                                  :request (fn [ctx]
                                             ;; Ei tunnu toimivan lokitason laskeminen. Johtuukoha
                                             ;; async-koodista vai mistä.
                                             (log/with-level
                                               :warn
                                               (gatling-kutsu kutsu)))}]})
                      kutsut)}
        yhteenveto
        (gatling/run
          simulaatio
          (merge
            {:timeout-in-ms 10
             :concurrency (count kutsut)
             :requests (count kutsut)}
            (when (env/env "HARJA_AJA_GATLING_RAPORTTI" false)
              ;; Oletuksena ei haluta kirjoittaa levylle raportteja,
              ;; eli luodaan oma raportteri, joka ei tee mitään
              {:reporter {:writer (fn [_ _ _])
                          :generator (fn [simulation]
                                       (println "Ran" simulation "without report"))}})
            opts))]
    (log/debug (str "Simulaatio " simulaation-nimi " valmistui: " yhteenveto ". Aikaraja oli " (:timeout-in-ms opts)))
    (or (= 0 (:ko yhteenveto))
        (nil? (:ko yhteenveto)))))

(defmacro is->
  [testattava & fn-listat]
  (let [testattava_ (gensym "testattava")
        f_ (gensym "f")
        loput_ (gensym "loput")]
    `(let [~testattava_ ~testattava]
       ~@(loop [[f_ & loput_] fn-listat
                iss# []]
           (if (nil? f_)
             iss#
             (let [msg?# (string? (first loput_))
                   is-lause# (if msg?#
                               `(is (~f_ ~testattava_) ~(first loput_))
                               `(is (~f_ ~testattava_)))]
               (recur (if msg?#
                        (rest loput_)
                        loput_)
                      (conj iss# is-lause#))))))))

(defn onnistunut-sahkopostikuittaus [viesti-id]
  (str "<sahkoposti:kuittaus xmlns:sahkoposti=\"http://www.liikennevirasto.fi/xsd/harja/sahkoposti\">\n
  <viestiId>"viesti-id"</viestiId>\n
  <aika>2008-09-29T04:49:45</aika>\n
  <onnistunut>true</onnistunut>\n</sahkoposti:kuittaus>"))

(defn epaonnistunut-sahkopostikuittaus [viesti-id]
  (str "<sahkoposti:kuittaus xmlns:sahkoposti=\"http://www.liikennevirasto.fi/xsd/harja/sahkoposti\">\n
  <viestiId>"viesti-id"</viestiId>\n
  <aika>2008-09-29T04:49:45</aika>\n
  <onnistunut>false</onnistunut>\n</sahkoposti:kuittaus>"))

(defn hae-ulos-lahtevat-integraatiotapahtumat []
  (q-map (str "SELECT id, integraatiotapahtuma, suunta, sisaltotyyppi, siirtotyyppi, sisalto, otsikko, parametrit, osoite, kasitteleva_palvelin
                 FROM integraatioviesti
                WHERE suunta = 'ulos'
                  AND sisalto is not null and sisalto != ''
                ORDER BY integraatiotapahtuma ASC;")))

(defn hae-kaikki-integraatioviestit []
  (q-map (str "SELECT id, integraatiotapahtuma, suunta, sisaltotyyppi, siirtotyyppi, sisalto, otsikko, parametrit, osoite
                 FROM integraatioviesti ORDER BY integraatiotapahtuma ASC, id asc ;")))

(defn nykyhetki-iso8061-formaatissa-menneisyyteen
  "Anna määrä parametriin, että montako päivää siirretään menneisyyteen."
  [maara]
  (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date. (- (.getTime (Date.)) (* maara 86400 1000)))))

(defn nykyhetki-iso8061-formaatissa-menneisyyteen-minuutteja
  "Anna määrä parametriin, että montako minuuttia siirretään menneisyyteen."
  [maara]
  (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date. (- (.getTime (Date.)) (* maara 60000)))))

(defn nykyhetki-iso8061-formaatissa-tulevaisuuteen
  "Anna määrä parametriin, että montako päivää siirretään tulevaisuuteen."
  [maara]
  (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date. (+ (.getTime (Date.)) (* maara 86400 1000)))))

(defn nykyhetki-psql-timestamp-formaatissa-menneisyyteen-minuutteja
  "Anna määrä parametriin, että montako päivää siirretään menneisyyteen."
  [maara]
  (.format (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss.SSS") (Date. (- (.getTime (Date.)) (* maara 60000)))))

(defn nykyhetki-psql-timestamp-formaatissa-tulevaisuuteen-minuutteja
  "Anna määrä parametriin, että montako minuuttia siirretään tulevaisuuteen."
  [maara]
  (.format (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss.SSS") (Date. (+ (.getTime (Date.)) (* maara 60000)))))

(defn- seuraava-rivi
  [edellinen nykyinen loput-seq]
  (reduce
    (fn [rivi [diagonal ylapuolinen muut]]
      (let [paivitettava-arvo (if (= muut nykyinen)
                                diagonal
                                (inc (min diagonal ylapuolinen (peek rivi))))]
        (conj rivi paivitettava-arvo)))
    [(inc (first edellinen))]
    (map vector edellinen (next edellinen) loput-seq)))

(defn poista-kulut-aikavalilta [urakka-id hk_alkupvm hk_loppupvm]
  (let [kulut (flatten (q (format "SELECT id FROM kulu k WHERE k.urakka = %s and k.erapaiva BETWEEN '%s'::DATE AND '%s'::DATE;" urakka-id hk_alkupvm hk_loppupvm)))
        _ (u (format "DELETE FROM kulu_kohdistus WHERE kulu IN (%s)" (str/join "," kulut)))
        _ (u (format "DELETE FROM kulu_liite WHERE kulu IN (%s)" (str/join "," kulut)))
        _ (u (format "delete from kulu k where k.urakka = %s and k.erapaiva BETWEEN '%s'::DATE AND '%s'::DATE; " urakka-id hk_alkupvm hk_loppupvm))]))

(defn poista-bonukset-ja-sanktiot-aikavalilta [urakka-id hk_alkupvm hk_loppupvm]
  (let [toimenpideinstanssit (flatten (q (format "SELECT tpi.id as is
                                                    FROM toimenpideinstanssi tpi
                                                   WHERE tpi.urakka = %s;" urakka-id)))
        _ (u (format "DELETE FROM erilliskustannus WHERE urakka = %s AND pvm BETWEEN '%s'::DATE AND '%s'::DATE;" urakka-id hk_alkupvm hk_loppupvm))
        ;; Sanktioihin ei ole tallennettu urakkaa, niin se pitää niputtaa toimenpideinstanssien kautta
        _ (u (format "DELETE FROM sanktio WHERE toimenpideinstanssi IN (%s) AND perintapvm BETWEEN '%s'::DATE AND '%s'::DATE;" (str/join "," toimenpideinstanssit) hk_alkupvm hk_loppupvm))]))

(defn lahes-sama?
  "Laske Levenshtein Distance -arvon kahden tekstin välille ja kertoo, onko se sallitun thresholdin puitteissa.
  Nyt thresholdina on 0.4 mikä tarkoittaa, että 40% sanasta/tekstistä täytyy täsmätä. Tällä verrataan yksittäisiä sanoja
  tai pitkiä lauseita ja pituus näyttelee isoa roolia, joten thresholdi on nyt suuri. Koska yksittäisten sanojen
  pituus on lyhyt.

  Voit jatkokehittää tätä ottamaan vastaan thresholdin parametrina tai suhteessa vertailtavan sanan pituuteen."
  [s1 s2]
  (let [;; Hyväksyy osimoilleen samat
        threshold 0.4
        matka (cond
                (and (empty? s1) (empty? s2)) 0
                (empty? s1) (count s2)
                (empty? s2) (count s1)
                :else (peek
                        (reduce (fn [edellinen nykyinen] (seuraava-rivi edellinen nykyinen s2))
                          (map #(identity %2) (cons nil s2) (range))
                          s1)))
        ero (/ matka (float (count s1)))]
    (< ero threshold)))
