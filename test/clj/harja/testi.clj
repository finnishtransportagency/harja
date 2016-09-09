(ns harja.testi
  "Harjan testauksen apukoodia."
  (:require
    [clojure.test :refer :all]
    [taoensso.timbre :as log]
    [harja.kyselyt.urakat :as urk-q]
    [harja.palvelin.komponentit.todennus :as todennus]
    [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
    [harja.palvelin.komponentit.http-palvelin :as http]
    [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
    [harja.palvelin.komponentit.tietokanta :as tietokanta]
    [harja.palvelin.komponentit.liitteet :as liitteet]
    [com.stuartsierra.component :as component]
    [clj-time.core :as t]
    [clj-time.coerce :as tc])
  (:import (java.util Locale)))

(def jarjestelma nil)

(Locale/setDefault (Locale. "fi" "FI"))


(defn ollaanko-jenkinsissa? []
  (= "harja-jenkins.solitaservices.fi"
     (.getHostName (java.net.InetAddress/getLocalHost))))

(defn travis? []
  (= "true" (System/getenv "TRAVIS")))



;; Ei täytetä Jenkins-koneen levytilaa turhilla logituksilla
;; eikä tehdä traviksen logeista turhan pitkiä
(log/set-config! [:appenders :standard-out :min-level]
                 (cond
                   (or (ollaanko-jenkinsissa?)
                       (travis?)
                       (= "true" (System/getenv "NOLOG")))
                   :fatal

                   :default
                   :debug))

(def testitietokanta {:palvelin (if (ollaanko-jenkinsissa?)
                                  "172.17.238.100"
                                  "localhost")
                      :portti 5432
                      :tietokanta "harjatest"
                      :kayttaja "harjatest"
                      :salasana nil})

; temppitietokanta jonka omistaa harjatest. käytetään väliaikaisena tietokantana jotta templatekanta
; (harjatest_template) ja testikanta (harjatest) ovat vapaina droppausta ja templaten kopiointia varten.
(def temppitietokanta {:palvelin (if (ollaanko-jenkinsissa?)
                                   "172.17.238.100"
                                   "localhost")
                       :portti 5432
                       :tietokanta "temp"
                       :kayttaja "harjatest"
                       :salasana nil})

(defn odota-ehdon-tayttymista [ehto-fn viesti max-aika]
  (loop [max-ts (+ max-aika (System/currentTimeMillis))]
    (if (> (System/currentTimeMillis) max-ts)
      (assert false (str "Ehto '" viesti "' ei täyttynyt " max-aika " kuluessa"))
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
  (liitteet/->Liitteet))

(defonce db (:datasource (luo-testitietokanta)))
(defonce temppidb (:datasource (luo-temppitietokanta)))

(def ds {:datasource db})

(defn q
  "Kysele Harjan kannasta yksikkötestauksen yhteydessä"
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
                               (recur (conj row (.getObject rs i)) (inc i))
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

(defn pudota-ja-luo-testitietokanta-templatesta
  "Droppaa tietokannan ja luo sen templatesta uudelleen"
  []
  (with-open [c (.getConnection temppidb)
              ps (.createStatement c)]
    (tapa-backend-kannasta ps "harjatest_template")
    (tapa-backend-kannasta ps "harjatest")
    (.executeUpdate ps "DROP DATABASE IF EXISTS harjatest")
    (.executeUpdate ps "CREATE DATABASE harjatest TEMPLATE harjatest_template"))
  (luo-kannat-uudelleen))

(defprotocol FeikkiHttpPalveluKutsu
  (kutsu-palvelua
    ;; GET
    [this nimi kayttaja]

    ;; POST
    [this nimi kayttaja payload]
    "kutsu HTTP palvelufunktiota suoraan."))

(defn testi-http-palvelin
  "HTTP 'palvelin' joka vain ottaa talteen julkaistut palvelut."
  []
  (let [palvelut (atom {})]
    (reify
      http/HttpPalvelut
      (julkaise-palvelu [_ nimi palvelu-fn]
        (swap! palvelut assoc nimi palvelu-fn))
      (julkaise-palvelu [_ nimi palvelu-fn optiot]
        (swap! palvelut assoc nimi palvelu-fn))
      (poista-palvelu [_ nimi]
        (swap! palvelut dissoc nimi))

      FeikkiHttpPalveluKutsu
      (kutsu-palvelua [_ nimi kayttaja]
        ((get @palvelut nimi) kayttaja))
      (kutsu-palvelua [_ nimi kayttaja payload]
        ((get @palvelut nimi) kayttaja payload)))))

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

;; Määritellään käyttäjiä, joita testeissä voi käyttää
;; HUOM: näiden pitää täsmätä siihen mitä testidata.sql tiedostossa luodaan.

;; id:1 Tero Toripolliisi, POP ELY aluevastaava
(def +kayttaja-tero+ {:id 1 :etunimi "Tero" :sukunimi "Toripolliisi" :kayttajanimi "LX123456789" :organisaatio 9
                      :roolit #{"ELY_Urakanvalvoja"}})

;; id:2 Järjestelmävastuuhenkilö
(def +kayttaja-jvh+ {:sahkoposti "jalmari@example.com" :kayttajanimi "jvh"
                     :sukunimi "Järjestelmävastuuhenkilö" :roolit #{"Jarjestelmavastaava"}, :id 2
                     :etunimi "Jalmari" :urakka-roolit []
                     :organisaatio {:id 1 :nimi "Liikennevirasto",
                                    :tyyppi :liikennevirasto :lyhenne nil :ytunnus nil}
                     :organisaation-urakat #{}
                     :urakkaroolit {}})

;; id:1 Tero Toripolliisi, POP ELY aluevastaava
(def +kayttaja-yit_uuvh+ {:id 7 :etunimi "Yitin" :sukunimi "Urakkavastaava" :kayttajanimi "yit_uuvh" :organisaatio 11})

(def testikayttajien-lkm (atom nil))
(def pohjois-pohjanmaan-hallintayksikon-id (atom nil))
(def oulun-alueurakan-2005-2010-id (atom nil))
(def oulun-alueurakan-2014-2019-id (atom nil))
(def kajaanin-alueurakan-2014-2019-id (atom nil))
(def oulun-alueurakan-lampotila-hk-2014-2015 (atom nil))
(def oulun-alueurakan-2005-2010-paasopimuksen-id (atom nil))
(def oulun-alueurakan-2014-2019-paasopimuksen-id (atom nil))
(def kajaanin-alueurakan-2014-2019-paasopimuksen-id (atom nil))
(def pudasjarven-alueurakan-id (atom nil))
(def muhoksen-paallystysurakan-id (atom nil))
(def muhoksen-paallystysurakan-paasopimuksen-id (atom nil))
(def muhoksen-paikkausurakan-id (atom nil))
(def muhoksen-paikkausurakan-paasopimuksen-id (atom nil))

(defn hae-testikayttajat []
  (ffirst (q (str "SELECT count(*) FROM kayttaja;"))))

(defn hae-oulun-alueurakan-2005-2012-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Oulun alueurakka 2005-2012'"))))

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

(defn hae-kajaanin-alueurakan-2014-2019-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Kajaanin alueurakka 2014-2019'"))))

(defn hae-oulun-alueurakan-lampotila-hk-2014-2015 []
  (ffirst (q (str "SELECT id, urakka, alkupvm, loppupvm, keskilampotila, pitka_keskilampotila
                   FROM   lampotilat
                   WHERE  urakka = " @oulun-alueurakan-2014-2019-id "
                   AND alkupvm = '2014-10-01' AND loppupvm = '2015-09-30'"))))

(defn hae-pohjois-pohjanmaan-hallintayksikon-id []
  (ffirst (q (str "SELECT id
                   FROM   organisaatio
                   WHERE  nimi = 'Pohjois-Pohjanmaa ja Kainuu'"))))

(defn hae-oulun-alueurakan-toimenpideinstanssien-idt []
  (into [] (flatten (q (str "SELECT tpi.id
                  FROM   urakka u
                    JOIN toimenpideinstanssi tpi ON u.id = tpi.urakka
                  WHERE  u.nimi = 'Oulun alueurakka 2005-2012';")))))

(defn hae-muhoksen-paallystysurakan-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Muhoksen päällystysurakka'"))))

(defn hae-oulun-tiemerkintaurakan-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2018'"))))

(defn hae-muhoksen-paikkausurakan-id []
  (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Muhoksen paikkausurakka'"))))

(defn hae-pudasjarven-alueurakan-id []
  (ffirst (q (str "SELECT id        x
                   FROM   urakka
                   WHERE  nimi = 'Pudasjärven alueurakka 2007-2012'"))))

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

(defn hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   urakka = (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka')
                   AND NOT EXISTS(SELECT id FROM paallystysilmoitus WHERE paallystyskohde = ypk.id)"))))

(defn hae-muhoksen-yllapitokohde-jolla-paallystysilmoitusta []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   urakka = (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka')\n
                   AND EXISTS(SELECT id FROM paallystysilmoitus WHERE paallystyskohde = ypk.id)"))))

(defn hae-yllapitokohde-tielta-20-jolla-paallystysilmoitus []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   tr_numero = 20
                   AND EXISTS(SELECT id FROM paallystysilmoitus WHERE paallystyskohde = ypk.id);"))))

(defn hae-yllapitokohde-tielta-20-jolla-ei-paallystysilmoitusta []
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE
                   tr_numero = 20
                   AND NOT EXISTS(SELECT id FROM paallystysilmoitus WHERE paallystyskohde = ypk.id);"))))

(defn hae-yllapitokohde-joka-ei-kuulu-urakkaan [urakka-id]
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE urakka != " urakka-id ";"))))

(defn hae-yllapitokohde-jonka-tiemerkintaurakka-suorittaa [tiemerkintaurakka-id]
  (ffirst (q (str "SELECT id FROM yllapitokohde ypk
                   WHERE suorittava_tiemerkintaurakka = " tiemerkintaurakka-id ";"))))

(defn tietokanta-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (luo-kannat-uudelleen)
  (testit))

(defn urakkatieto-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (luo-kannat-uudelleen)
  (reset! testikayttajien-lkm (hae-testikayttajat))
  (reset! oulun-alueurakan-2005-2010-id (hae-oulun-alueurakan-2005-2012-id))
  (reset! oulun-alueurakan-2014-2019-id (hae-oulun-alueurakan-2014-2019-id))
  (reset! kajaanin-alueurakan-2014-2019-id (hae-kajaanin-alueurakan-2014-2019-id))
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
  (testit)
  (reset! oulun-alueurakan-2005-2010-id nil)
  (reset! oulun-alueurakan-2005-2010-paasopimuksen-id nil)
  (reset! pudasjarven-alueurakan-id nil))

(use-fixtures :once urakkatieto-fixture)

(defn oulun-urakan-tilaajan-urakanvalvoja []
  {:sahkoposti "ely@example.org", :kayttajanimi "ely-oulun-urakanvalvoja",
   :roolit #{"ELY_Urakanvalvoja"}, :id 417,
   :organisaatio {:id 10, :nimi "Pohjois-Pohjanmaa ja Kainuu", :tyyppi "hallintayksikko"},
   :organisaation-urakat #{@oulun-alueurakan-2005-2010-id}
   :urakkaroolit {@oulun-alueurakan-2005-2010-id, #{"ELY_Urakanvalvoja"}}})

(defn oulun-urakan-urakoitsijan-urakkavastaava []
  {:sahkoposti "yit_uuvh@example.org", :kayttajanimi "yit_uuvh", :puhelin 43363123, :sukunimi "Urakkavastaava",
   :roolit #{}, :id 17, :etunimi "Yitin",
   :organisaatio {:id 10, :nimi "YIT Rakennus Oy", :tyyppi "urakoitsija"},
   :organisaation-urakat #{@oulun-alueurakan-2005-2010-id}
   :urakkaroolit {@oulun-alueurakan-2005-2010-id #{"vastuuhenkilo"}}})

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
                                               (liitteet/->Liitteet)
                                               [:db])

                         ~@omat))))

     (alter-var-root #'urakka
                     (fn [_#]
                       (ffirst (q (str "SELECT id FROM urakka WHERE urakoitsija=(SELECT organisaatio FROM kayttaja WHERE kayttajanimi='" ~kayttaja "') "
                                       " AND tyyppi='hoito'::urakkatyyppi")))))
     (testit#)
     (alter-var-root #'jarjestelma component/stop)))

(defn =marginaalissa?
  "Palauttaa ovatko kaksi lukua samat virhemarginaalin sisällä. Voi käyttää esim. doublelaskennan
  tulosten vertailussa. Oletusmarginaali on 0.05"
  ([eka toka] (=marginaalissa? eka toka 0.05))
  ([eka toka marginaali]
   (< (Math/abs (double (- eka toka))) marginaali)))

(defn tarkista-map-arvot
  "Tarkistaa, että mäpissä on oikeat arvot. Numeroita vertaillaan =marginaalissa? avulla, muita
  = avulla. Tarkistaa myös, että kaikki arvot ovat olemassa. Odotetussa mäpissa saa olla
  ylimääräisiä avaimia."
  [odotetut saadut]
  (doseq [k (keys odotetut)
          :let [odotettu-arvo (get odotetut k)
                saatu-arvo (get saadut k ::ei-olemassa)]]
    (if (= saatu-arvo ::ei-olemassa)
      (is false (str "Odotetussa mäpissä ei arvoa avaimelle: " k
                     ", odotettiin arvoa: " odotettu-arvo))

      (if (and (number? odotettu-arvo) (number? saatu-arvo))
        (is (=marginaalissa? odotettu-arvo saatu-arvo)
            (str "Saatu arvo avaimelle " k " ei marginaalissa, odotettu: "
                 odotettu-arvo ", saatu: " saatu-arvo))
        (is (= odotettu-arvo saatu-arvo)
            (str "Saatu arvo avaimelle " k " ei täsmää, odotettu: " odotettu-arvo
                 ", saatu: " saatu-arvo))))))

(def suomen-aikavyohyke (t/time-zone-for-id "EET"))

(defn paikallinen-aika [dt]
  (-> dt
      tc/from-sql-date
      (t/to-time-zone suomen-aikavyohyke)))
