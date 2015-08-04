(ns harja.testi
  "Harjan testauksen apukoodia."
  (:require
    [clojure.test :refer :all]
    [taoensso.timbre :as log]
    [harja.kyselyt.urakat :as urk-q]
    [harja.palvelin.komponentit.http-palvelin :as http]
    [harja.palvelin.komponentit.tietokanta :as tietokanta]))

(def jarjestelma nil)


(def testitietokanta [(if (= "harja-jenkins.solitaservices.fi"
                             (.getHostName (java.net.InetAddress/getLocalHost)))
                        "172.17.238.100"
                        "localhost")
                      5432
                      "harjatest"
                      "harjatest"
                      nil])

(defn odota [ehto-fn viesti max-aika]
  (loop [max-ts (+ max-aika (System/currentTimeMillis))]
    (if (> (System/currentTimeMillis) max-ts)
      (assert false (str "Ehto '" viesti "' ei täyttynyt " max-aika " kuluessa"))
      (when-not (ehto-fn)
        (recur max-ts)))))

(defn luo-testitietokanta []
  (apply tietokanta/luo-tietokanta testitietokanta))

(defonce db (:datasource (luo-testitietokanta)))

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
(def +kayttaja-tero+ {:id 1 :etunimi "Tero" :sukunimi "Toripolliisi" :kayttajanimi "LX123456789"})

;; id:2 Järjestelmävastuuhenkilö
(def +kayttaja-jvh+ {:sahkoposti   "jalmari@example.com" :kayttajanimi "jvh"
                     :sukunimi     "Järjestelmävastuuhenkilö" :roolit #{"jarjestelmavastuuhenkilo"}, :id 2
                     :etunimi      "Jalmari" :urakka-roolit []
                     :organisaatio {:id     1 :nimi "Liikennevirasto",
                                    :tyyppi :liikennevirasto :lyhenne nil :ytunnus nil}
                     :urakkaroolit ()})


(def testikayttajien-lkm (atom nil))
(def oulun-alueurakan-id (atom nil))
(def oulun-alueurakan-paasopimuksen-id (atom nil))
(def pudasjarven-alueurakan-id (atom nil))
(def muhoksen-paallystysurakan-id (atom nil))
(def muhoksen-paallystysurakan-paasopimuksen-id (atom nil))

(defn hae-testikayttajat []
  (ffirst (q (str "SELECT count(*) FROM kayttaja;"))))

(defn hae-oulun-alueurakan-id []
  (ffirst (q (str "SELECT id
                               FROM   urakka
                               WHERE  nimi = 'Oulun alueurakka 2005-2010'"))))

(defn hae-oulun-alueurakan-toimenpideinstanssien-idt []
  (into [] (flatten (q (str "SELECT tpi.id
                  FROM   urakka u
                    JOIN toimenpideinstanssi tpi ON u.id = tpi.urakka
                  WHERE  u.nimi = 'Oulun alueurakka 2005-2010';")))))

(defn hae-muhoksen-paallystysurakan-id []
  (ffirst (q (str "SELECT id
                               FROM   urakka
                               WHERE  nimi = 'Muhoksen päällystysurakka'"))))

(defn hae-pudasjarven-alueurakan-id []
  (ffirst (q (str "SELECT id        x
                               FROM   urakka
                               WHERE  nimi = 'Pudasjärven alueurakka 2007-2012'"))))

(defn hae-oulun-alueurakan-paasopimuksen-id []
  (ffirst (q (str "(SELECT id FROM sopimus WHERE urakka =
                           (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null)"))))

(defn hae-muhoksen-paallystysurakan-paasopimuksen-id []
  (ffirst (q (str "(SELECT id FROM sopimus WHERE urakka =
                           (SELECT id FROM urakka WHERE nimi='Muhoksen päällystysurakka') AND paasopimus IS null)"))))

(defn urakkatieto-fixture [testit]
  (reset! testikayttajien-lkm (hae-testikayttajat))
  (reset! oulun-alueurakan-id (hae-oulun-alueurakan-id))
  (reset! muhoksen-paallystysurakan-id (hae-muhoksen-paallystysurakan-id))
  (reset! muhoksen-paallystysurakan-paasopimuksen-id (hae-muhoksen-paallystysurakan-paasopimuksen-id))
  (reset! oulun-alueurakan-paasopimuksen-id (hae-oulun-alueurakan-paasopimuksen-id))
  (reset! pudasjarven-alueurakan-id (hae-pudasjarven-alueurakan-id))
  (testit)
  (reset! oulun-alueurakan-id nil)
  (reset! oulun-alueurakan-paasopimuksen-id nil)
  (reset! pudasjarven-alueurakan-id nil))

(use-fixtures :once urakkatieto-fixture)

(defn oulun-urakan-tilaajan-urakanvalvoja []
  {:sahkoposti   "ely@example.org", :kayttajanimi "ely-oulun-urakanvalvoja",
   :roolit       #{"urakanvalvoja"}, :id 417,
   :organisaatio {:id 10, :nimi "Pohjois-Pohjanmaa ja Kainuu", :tyyppi "hallintayksikko"},
   :urakkaroolit [{:urakka {:id              @oulun-alueurakan-id,
                            :nimi            "Oulun alueurakka 2005-2010",
                            :hallintayksikko {:nimi "Pohjois-Pohjanmaa ja Kainuu", :id 8}},
                   :rooli  "urakanvalvoja"}]})

(defn oulun-urakan-urakoitsijan-urakkavastaava []
  {:sahkoposti   "yit_uuvh@example.org", :kayttajanimi "yit_uuvh", :puhelin 43363123, :sukunimi "Urakkavastaava",
   :roolit       #{"urakoitsijan urakan vastuuhenkilo"}, :id 17, :etunimi "Yitin",
   :organisaatio {:id 10, :nimi "YIT Rakennus Oy", :tyyppi "urakoitsija"},
   :urakkaroolit [{:urakka {:id              @oulun-alueurakan-id,
                            :nimi            "Oulun alueurakka 2005-2010", :urakoitsija {:nimi "YIT Rakennus Oy", :id 10},
                            :hallintayksikko {:nimi "Pohjois-Pohjanmaa ja Kainuu", :id 8}},
                   :luotu  nil,
                   :rooli  "urakoitsijan urakan vastuuhenkilo"}]})

(defn ei-ole-oulun-urakan-urakoitsijan-urakkavastaava []
  {:sahkoposti   "yit_uuvh@example.org", :kayttajanimi "yit_uuvh", :puhelin 43363123, :sukunimi "Urakkavastaava",
   :roolit       #{"urakoitsijan urakan vastuuhenkilo"}, :id 17, :etunimi "Yitin",
   :organisaatio {:id 10, :nimi "YIT Rakennus Oy", :tyyppi "urakoitsija"},
   :urakkaroolit [{:urakka {:id              234234324234,  ;;eli ei ole oulun urakan ID
                            :nimi            "Oulun alueurakka 2005-2010", :urakoitsija {:nimi "YIT Rakennus Oy", :id 10},
                            :hallintayksikko {:nimi "Pohjois-Pohjanmaa ja Kainuu", :id 8}},
                   :luotu  nil,
                   :rooli  "urakoitsijan urakan vastuuhenkilo"}]})

;; Selkeä rajoite on, että tässä testataan usein vain ensimmäistä, tai tietyllä
;; indeksillä löytyvää. Yleensä pitäisi tieten oikeanlainenkin rivi löytyä, mutta
;; teoriassa on hyvinkin mahdollista, että huonolla tuurilla ei löydy.
(defn sisaltaa-ainakin-sarakkeet?
  [tulos sarakkeet]
  (nil?
    (some
      false?
      (map
        #(contains?
          (get-in
            (if (vector? tulos) (first tulos) tulos)
            (when (vector? %) (butlast %)))
          (if (vector? %) (last %) %)) sarakkeet))))

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
   (if parametrit
     (do
       (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                     palvelu (or kayttaja +kayttaja-jvh+) parametrit)]
         (log/debug "Tarkistetaan sarakkeet vastauksesta:" (pr-str vastaus))
         (sisaltaa-ainakin-sarakkeet? vastaus sarakkeet)))
     (do
       (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                     palvelu (or kayttaja +kayttaja-jvh+))]
         (log/debug "Tarkistetaan sarakkeet vastauksesta:" (pr-str vastaus))
         (sisaltaa-ainakin-sarakkeet? vastaus sarakkeet))))))
