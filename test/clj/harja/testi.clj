(ns harja.testi
  "Harjan testauksen apukoodia."
  (:require
    [clojure.test :refer :all]
    [clojure.core.async :as async :refer [alts! >! <! go timeout chan <!!]]
    [taoensso.timbre :as log]
    [harja.kyselyt.urakat :as urk-q]
    [harja.palvelin.komponentit.todennus :as todennus]
    [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
    [harja.palvelin.komponentit.http-palvelin :as http]
    [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
    [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :as pois-kytketyt-ominaisuudet]
    [harja.palvelin.komponentit.tietokanta :as tietokanta]
    [harja.palvelin.komponentit.liitteet :as liitteet]
    [com.stuartsierra.component :as component]
    [clj-time.core :as t]
    [clj-time.coerce :as tc]
    [clojure.core.async :as async]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
    [harja.kyselyt.konversio :as konv]
    [harja.pvm :as pvm]
    [clj-gatling.core :as gatling]
    [harja.tyokalut.env :as env])
  (:import (java.util Locale))
  (:import (org.postgresql.util PSQLException)))

(def jarjestelma nil)

(Locale/setDefault (Locale. "fi" "FI"))

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
       :debug)}}})

(def testitietokanta {:palvelin (env/env "HARJA_TIETOKANTA_HOST" "localhost")
                      :portti (env/env "HARJA_TIETOKANTA_PORTTI" 5432)
                      :tietokanta "harjatest"
                      :kayttaja "harjatest"
                      :salasana nil})

; temppitietokanta jonka omistaa harjatest. käytetään väliaikaisena tietokantana jotta templatekanta
; (harjatest_template) ja testikanta (harjatest) ovat vapaina droppausta ja templaten kopiointia varten.
(def temppitietokanta {:palvelin (env/env "HARJA_TIETOKANTA_HOST" "localhost")
                       :portti (env/env "HARJA_TIETOKANTA_PORTTI" 5432)
                       :tietokanta "temp"
                       :kayttaja "harjatest"
                       :salasana nil})

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
  (tietokanta/luo-tietokanta testitietokanta))

(defn luo-temppitietokanta []
  (tietokanta/luo-tietokanta temppitietokanta))

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

(defn- tapa-backend-kannasta [ps kanta]
  (.executeQuery ps (str "SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = '" kanta "' AND pid <> pg_backend_pid()")))

(defn- luo-kannat-uudelleen []
  (alter-var-root #'db (fn [_]
                         (com.mchange.v2.c3p0.DataSources/destroy db)
                         (:datasource (luo-testitietokanta))))
  (alter-var-root #'ds (fn [_]
                         {:datasource db}))
  (alter-var-root #'temppidb (fn [_]
                               (com.mchange.v2.c3p0.DataSources/destroy temppidb)
                               (:datasource (luo-temppitietokanta)))))

(defn yrita-querya [f n]
  (dotimes [n n]
    (try
      (f)
      (catch PSQLException e
        (Thread/sleep 500)
        (log/warn e "- yritetään uudelleen, yritys" n)))))

(defn pudota-ja-luo-testitietokanta-templatesta
  "Droppaa tietokannan ja luo sen templatesta uudelleen"
  []
  (with-open [c (.getConnection temppidb)
              ps (.createStatement c)]

    (yrita-querya (fn [] (tapa-backend-kannasta ps "harjatest_template")) 5)
    (yrita-querya (fn [] (tapa-backend-kannasta ps "harjatest")) 5)
    (yrita-querya (fn [] (.executeUpdate ps "DROP DATABASE IF EXISTS harjatest")) 5)
    (.executeUpdate ps "CREATE DATABASE harjatest TEMPLATE harjatest_template"))
  (luo-kannat-uudelleen))

(defprotocol FeikkiHttpPalveluKutsu
  (kutsu-palvelua
    ;; GET
    [this nimi kayttaja]

    ;; POST
    [this nimi kayttaja payload]
    "kutsu HTTP palvelufunktiota suoraan.")

  (kutsu-karttakuvapalvelua
    ;; POST
    [this nimi kayttaja payload koordinaatti extent]))

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
                       [-550093.049087613 6372322.595126259 1527526.529326106 7870243.751025201])})))))

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


(defn kutsu-http-palvelua
  "Lyhyt muoto testijärjestelmän HTTP palveluiden kutsumiseen."
  ([nimi kayttaja]
   (kutsu-palvelua (:http-palvelin jarjestelma) nimi kayttaja))
  ([nimi kayttaja payload]
   (kutsu-palvelua (:http-palvelin jarjestelma) nimi kayttaja payload)))

(defn arvo-vapaa-portti
  "Arpoo vapaan portinnumeron ja palauttaa sen"
  []
  (let [s (doto (java.net.ServerSocket. 0)
            (.setReuseAddress true))]
    (try
      (.getLocalPort s)
      (finally (.close s)))))

(def testikayttajien-lkm (atom nil))
(def pohjois-pohjanmaan-hallintayksikon-id (atom nil))
(def oulun-alueurakan-2005-2010-id (atom nil))
(def oulun-alueurakan-2014-2019-id (atom nil))
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

(def yit-rakennus-id (atom nil))

(defn hae-testikayttajat []
  (ffirst (q (str "SELECT count(*) FROM kayttaja;"))))

(defn hae-oulun-alueurakan-2005-2012-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Oulun alueurakka 2005-2012'"))))


(defn hae-tampereen-alueurakan-2017-2022-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Tampereen alueurakka 2017-2022'"))))

(defn hae-helsingin-vesivaylaurakan-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL';"))))

(defn hae-pyhaselan-vesivaylaurakan-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Pyhäselän urakka';"))))

(defn hae-merivaylien-hallintayksikon-id []
  (ffirst (q (str "SELECT id
                   FROM   organisaatio
                   WHERE  nimi = 'Meriväylät';"))))

(defn hae-sisavesivaylien-hallintayksikon-id []
  (ffirst (q (str "SELECT id
                   FROM   organisaatio
                   WHERE  nimi = 'Sisävesiväylät';"))))

(defn hae-saimaan-kanavaurakan-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Saimaan kanava';"))))

(defn hae-joensuun-kanavaurakan-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Joensuun kanava';"))))

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
                     FROM toimenpidekoodi t4
                    WHERE t4.nimi = 'Ei yksilöity' and t4.taso = 4 AND t4.emo = (select id from toimenpidekoodi t3 WHERE t3.nimi = 'Laaja toimenpide' and t3.taso = 3
                          AND t3.emo = (select id from toimenpidekoodi t2 WHERE t2.nimi = 'Vesiliikenteen käyttöpalvelut' and t2.taso = 2
                          AND t2.emo = (SELECT id FROM toimenpidekoodi t1 WHERE t1.nimi = 'Käyttö, meri' AND t1.taso = 1)));"))))


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
           WHERE urakka=" (hae-saimaan-kanavaurakan-id))))))

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

(defn hae-oulun-maanteiden-hoitourakan-2019-2024-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Oulun MHU 2019-2024'"))))

(defn hae-oulun-maanteiden-hoitourakan-2019-2024-sopimus-id []
  (ffirst (q (str "SELECT id FROM sopimus where urakka = (SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Oulun MHU 2019-2024')"))))

(defn hae-rovaniemen-maanteiden-hoitourakan-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)'"))))
(defn hae-ivalon-maanteiden-hoitourakan-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Ivalon MHU testiurakka (uusi)'"))))

(defn hae-pellon-maanteiden-hoitourakan-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Pellon MHU testiurakka (3. hoitovuosi)'"))))

(defn hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi [toimenpidekoodi]
  (ffirst (q (str "SELECT id from toimenpideinstanssi where urakka = (select id FROM urakka WHERE  nimi = 'Oulun MHU 2019-2024') AND
                   toimenpide = (select id from toimenpidekoodi where koodi = '" toimenpidekoodi "');"))))

(defn hae-toimenpidekoodin-id [nimi koodi]
  (ffirst (q (str "SELECT id from toimenpidekoodi where nimi = '" nimi "' AND emo = (select id from toimenpidekoodi WHERE koodi = '" koodi "');"))))

(defn hae-tehtavaryhman-id [nimi]
  (ffirst (q (str "SELECT id from tehtavaryhma where nimi = '" nimi "';"))))

(defn hae-yit-rakennus-id []
  (ffirst (q (str "SELECT id FROM organisaatio WHERE nimi = 'YIT Rakennus Oy'"))))

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
  (ffirst (q (str "SELECT id, urakka, alkupvm, loppupvm, keskilampotila, pitka_keskilampotila
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

(defn hae-liikenneympariston-hoidon-toimenpidekoodin-id []
  (ffirst (q (str "SELECT id
  FROM toimenpidekoodi
  WHERE nimi = 'Liikenneympäristön hoito laaja TPI' AND taso = 3\nAND
  emo = (select id FROM toimenpidekoodi WHERE taso = 2 AND nimi = 'Liikenneympäristön hoito');"))))

(defn hae-yha-paallystysurakan-id []
  (ffirst (q "SELECT id FROM urakka WHERE nimi = 'YHA-päällystysurakka'")))

(defn hae-muhoksen-paallystysurakan-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Muhoksen päällystysurakka'"))))

(defn hae-muhoksen-paallystysurakan-tpi-id []
  (ffirst (q (str "SELECT id
                   FROM   toimenpideinstanssi
                   WHERE  urakka = (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka')"))))

(defn hae-muhoksen-paallystysurakan-testikohteen-id []
  (ffirst (q (str "SELECT id FROM yllapitokohde WHERE nimi = 'Kuusamontien testi'"))))

(defn hae-muhoksen-paallystysurakan-testipaikkauskohteen-id []
  (ffirst (q (str "SELECT id FROM paikkauskohde WHERE nimi = 'Testikohde Muhoksen paallystysurakassa'"))))

(defn hae-utajarven-paallystysurakan-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Utajärven päällystysurakka'"))))

(defn hae-oulun-tiemerkintaurakan-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2022'"))))

(defn hae-lapin-tiemerkintaurakan-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Lapin tiemerkinnän palvelusopimus 2013-2018'"))))

(defn hae-oulun-tiemerkintaurakan-paasopimuksen-id []
  (ffirst (q (str "SELECT id
                   FROM   sopimus
                   WHERE  nimi = 'Oulun tiemerkinnän palvelusopimuksen pääsopimus 2013-2022'"))))

(defn hae-muhoksen-paikkausurakan-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Muhoksen paikkausurakka'"))))

(defn hae-pudasjarven-alueurakan-id []
  (ffirst (q (str "SELECT id        x
                   FROM   urakka
                   WHERE  nimi = 'Pudasjärven alueurakka 2007-2012'"))))
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

(defn hae-muhoksen-yllapitokohde-jolla-paallystysilmoitusta []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   urakka = (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka')
                   AND EXISTS(SELECT id FROM paallystysilmoitus WHERE paallystyskohde = ypk.id)"))))

(defn hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
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

(defn hae-yllapitokohde-nakkilan-ramppi []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   nimi = 'Nakkilan ramppi';"))))

(defn hae-yllapitokohde-kirkkotie []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   nimi = 'Kirkkotie';"))))

(defn hae-yllapitokohde-oulaisten-ohitusramppi []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   nimi = 'Oulaisten ohitusramppi';"))))

(defn hae-yllapitokohde-oulun-ohitusramppi []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   nimi = 'Oulun ohitusramppi';"))))

(defn hae-yllapitokohde-kuusamontien-testi-jolta-puuttuu-paallystysilmoitus []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   nimi = 'Kuusamontien testi'
                   AND NOT EXISTS(SELECT id FROM paallystysilmoitus WHERE paallystyskohde = ypk.id);"))))

(defn hae-yllapitokohde-tielta-20-jolla-paallystysilmoitus []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   tr_numero = 20
                   AND EXISTS(SELECT id FROM paallystysilmoitus WHERE paallystyskohde = ypk.id);"))))

(defn hae-yllapitokohde-joka-ei-kuulu-urakkaan [urakka-id]
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE urakka != " urakka-id ";"))))

(defn hae-yllapitokohde-jonka-tiemerkintaurakka-suorittaa [tiemerkintaurakka-id]
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE suorittava_tiemerkintaurakka = " tiemerkintaurakka-id ";"))))

(defn hae-yllapitokohde-tarkea-kohde-pot2 []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE nimi = 'Tärkeä kohde mt20';"))))

(defn poista-paallystysilmoitus-paallystyskohtella [paallystyskohde-id]
  (u (str "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " paallystyskohde-id ";"))) ; petar pot2:ssa tulee paljon muita tauluja

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

;; id:1 Tero Toripolliisi, POP ELY aluevastaava

(def +kayttaja-tero+ (hae-testi-kayttajan-tiedot {:etunimi "Tero" :sukunimi "Toripolliisi" :roolit #{"ELY_Urakanvalvoja"}}))

;; id:2 Järjestelmävastuuhenkilö

(def +kayttaja-jvh+ (hae-testi-kayttajan-tiedot {:etunimi "Jalmari" :sukunimi "Järjestelmävastuuhenkilö" :roolit #{"Jarjestelmavastaava"}}))

(def +kayttaja-yit_uuvh+ (hae-testi-kayttajan-tiedot {:etunimi "Yitin" :sukunimi "Urakkavastaava"}))

(def +kayttaja-ulle+ (hae-testi-kayttajan-tiedot {:etunimi "Ulle" :sukunimi "Urakoitsija"}))

(def +kayttaja-vastuuhlo-muhos+ (hae-testi-kayttajan-tiedot {:etunimi "Antero" :sukunimi "Asfalttimies"}))

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

(defn urakkatieto-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (reset! testikayttajien-lkm (hae-testikayttajat))
  (reset! oulun-alueurakan-2005-2010-id (hae-oulun-alueurakan-2005-2012-id))
  (reset! oulun-alueurakan-2014-2019-id (hae-oulun-alueurakan-2014-2019-id))
  (reset! oulun-maanteiden-hoitourakan-2019-2024-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id))
  (reset! oulun-maanteiden-hoitourakan-2019-2024-sopimus-id (hae-oulun-maanteiden-hoitourakan-2019-2024-sopimus-id))
  (reset! kajaanin-alueurakan-2014-2019-id (hae-kajaanin-alueurakan-2014-2019-id))
  (reset! vantaan-alueurakan-2014-2019-id (hae-vantaan-alueurakan-2014-2019-id))
  (reset! oulun-alueurakan-lampotila-hk-2014-2015 (hae-oulun-alueurakan-lampotila-hk-2014-2015))
  (reset! pohjois-pohjanmaan-hallintayksikon-id (hae-pohjois-pohjanmaan-hallintayksikon-id))
  (reset! muhoksen-paallystysurakan-id (hae-muhoksen-paallystysurakan-id))
  (reset! muhoksen-paallystysurakan-paasopimuksen-id (hae-muhoksen-paallystysurakan-paasopimuksen-id))
  (reset! muhoksen-paikkausurakan-id (hae-muhoksen-paikkausurakan-id))
  (reset! muhoksen-paikkausurakan-paasopimuksen-id (hae-muhoksen-paikkausurakan-paasopimuksen-id))
  (reset! oulun-alueurakan-2005-2010-paasopimuksen-id (hae-oulun-alueurakan-2005-2010-paasopimuksen-id))
  (reset! oulun-alueurakan-2014-2019-paasopimuksen-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id))
  (reset! kajaanin-alueurakan-2014-2019-paasopimuksen-id (hae-kajaanin-alueurakan-2014-2019-paasopimuksen-id))
  (reset! pudasjarven-alueurakan-id (hae-pudasjarven-alueurakan-id))
  (reset! yit-rakennus-id (hae-yit-rakennus-id))
  (testit)
  (reset! oulun-alueurakan-2005-2010-id nil)
  (reset! oulun-alueurakan-2005-2010-paasopimuksen-id nil)
  (reset! pudasjarven-alueurakan-id nil))

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

(def testi-pois-kytketyt-ominaisuudet (component/using
                                        (pois-kytketyt-ominaisuudet/->PoisKytketytOminaisuudet #{})
                                        [:http-palvelin]))

(defmacro laajenna-integraatiojarjestelmafixturea
  "Integraatiotestifixturen rungon rakentava makro. :db, :http-palvelin ja :integraatioloki
  löytyy valmiina. Body menee suoraan system-mapin jatkoksi"
  [kayttaja & omat]
  `(fn [testit#]
     (pudota-ja-luo-testitietokanta-templatesta)
     (alter-var-root #'portti (fn [_#] (arvo-vapaa-portti)))
     (alter-var-root #'jarjestelma
                     (fn [_#]
                       (component/start
                         (component/system-map
                           :db (tietokanta/luo-tietokanta testitietokanta)
                           :db-replica (tietokanta/luo-tietokanta testitietokanta)
                           :klusterin-tapahtumat (component/using
                                                   (tapahtumat/luo-tapahtumat)
                                                   [:db])

                           :todennus (component/using
                                       (todennus/http-todennus)
                                       [:db :klusterin-tapahtumat])
                           :http-palvelin (component/using
                                            (http/luo-http-palvelin portti true)
                                            [:todennus])
                           :integraatioloki (component/using
                                              (integraatioloki/->Integraatioloki nil)
                                              [:db])

                           :liitteiden-hallinta (component/using
                                                  (liitteet/->Liitteet nil)
                                                  [:db])
                           :pois-kytketyt-ominaisuudet (component/using
                                                         (pois-kytketyt-ominaisuudet/->PoisKytketytOminaisuudet #{})
                                                         [:http-palvelin])

                           ~@omat))))

     (alter-var-root #'urakka
                     (fn [_#]
                       (ffirst (q (str "SELECT id FROM urakka WHERE urakoitsija=(SELECT organisaatio FROM kayttaja WHERE kayttajanimi='" ~kayttaja "') "
                                       " AND tyyppi='hoito'::urakkatyyppi ORDER BY id")))))
     (testit#)
     (alter-var-root #'jarjestelma component/stop)))

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
     `(list
       ~@(let [testattava_ testattava]
          (loop [[f_ & loput_] fn-listat
                 iss# []]
            (if (nil? f_)
              iss#
              (let [msg?# (string? (first loput_))
                    is-lause# (if msg?#
                                `(is (list ~f_ ~testattava_) (first '~loput_))
                                `(is (list ~f_ ~testattava_)))]
                (recur (if msg?#
                         (rest loput_)
                         loput_)
                       (conj iss# is-lause#)))))))))
