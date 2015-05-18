(ns harja.testi
  "Harjan testauksen apukoodia."
  (:require
    [clojure.test :refer :all]
    [taoensso.timbre :as log]
    [harja.kyselyt.urakat :as urk-q]
    [harja.palvelin.komponentit.http-palvelin :as http]
    [harja.palvelin.komponentit.tietokanta :as tietokanta]))


(def testitietokanta [(if (= "harja-jenkins.solitaservices.fi"
                             (.getHostName (java.net.InetAddress/getLocalHost)))
                        "172.17.238.100"
                        "localhost")
                      5432
                      "harjatest"
                      "harjatest"
                      nil])

(defn luo-testitietokanta []
  (apply tietokanta/luo-tietokanta testitietokanta))

(defn q
  "Kysele Harjan kannasta yksikkötestauksen yhteydessä"
  [jarjestelma & sql]
  (with-open [c (.getConnection (:datasource (:db jarjestelma)))
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
  [jarjestelma & sql]
  (with-open [c (.getConnection (:datasource (:db jarjestelma)))
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

;; Määritellään käyttäjiä, joita testeissä voi käyttää
;; HUOM: näiden pitää täsmätä siihen mitä testidata.sql tiedostossa luodaan.

;; id:1 Tero Toripolliisi, POP ELY aluevastaava
(def +kayttaja-tero+ {:id 1 :etunimi "Tero" :sukunimi "Toripolliisi" :kayttajanimi "LX123456789"})

;; id:2 Järjestelmävastuuhenkilö
(def +kayttaja-jvh+ {:id 2 :etunimi "Jalmari" :sukunimi "Järjestelmävastuuhenkilö" 
                     :kayttajanimi "jvh" :roolit #{"jarjestelmavastuuhenkilo"}})

;; Tätä käytetään testikäyttäjien määrän tarkistamiseen. Tätä pitää kasvattaa jos testidataan lisätään uusia.
(def +testikayttajia+ 6)

;; Tätä käytetään testiindeksien määrän tarkistamiseen. Tätä pitää kasvattaa jos testidataan lisätään uusia.
;; Nämä ovat indeksivuosi-pareja, esim. MAKU 2005 vuonna 2014 olisi yksi entry, ja MAKU 2005 vuonna 2015 toinen.
(def +testiindekseja+ 4)

(def jarjestelma nil)

(def oulun-alueurakan-id (atom nil))
(def pudasjarven-alueurakan-id (atom nil))

(defn hae-oulun-alueurakan-id []
  (ffirst (q jarjestelma (str "SELECT id
                               FROM   urakka
                               WHERE  nimi = 'Oulun alueurakka 2005-2010'"))))

(defn hae-pudasjarven-alueurakan-id []
  (ffirst (q jarjestelma (str "SELECT id        x
                               FROM   urakka
                               WHERE  nimi = 'Pudasjärven alueurakka 2007-2012'"))))

(defn urakkatieto-fixture [testit]
  (reset! oulun-alueurakan-id (hae-oulun-alueurakan-id))
  (reset! pudasjarven-alueurakan-id (hae-pudasjarven-alueurakan-id))
  (testit)
  (reset! oulun-alueurakan-id nil)
  (reset! pudasjarven-alueurakan-id nil))

(use-fixtures :once urakkatieto-fixture)

(defn oulun-urakan-tilaajan-urakanvalvoja []
  {:sahkoposti "ely@example.org", :kayttajanimi "ely-oulun-urakanvalvoja",
   :roolit #{"urakanvalvoja"}, :id 417,
   :organisaatio {:id 10, :nimi "Pohjois-Pohjanmaa ja Kainuu", :tyyppi "hallintayksikko"},
   :urakkaroolit [{:urakka {:id @oulun-alueurakan-id,
                            :nimi "Oulun alueurakka 2005-2010",
                            :hallintayksikko {:nimi "Pohjois-Pohjanmaa ja Kainuu", :id 8}},
                   :rooli "urakanvalvoja"}]})

(defn oulun-urakan-urakoitsijan-urakkavastaava []
  {:sahkoposti "yit_uuvh@example.org", :kayttajanimi "yit_uuvh", :puhelin 43363123, :sukunimi "Urakkavastaava",
   :roolit #{"urakoitsijan urakan vastuuhenkilo"}, :id 17, :etunimi "Yitin",
   :organisaatio {:id 10, :nimi "YIT Rakennus Oy", :tyyppi "urakoitsija"},
   :urakkaroolit [{:urakka {:id @oulun-alueurakan-id,
                            :nimi "Oulun alueurakka 2005-2010", :urakoitsija {:nimi "YIT Rakennus Oy", :id 10},
                            :hallintayksikko {:nimi "Pohjois-Pohjanmaa ja Kainuu", :id 8}},
                   :luotu nil,
                   :rooli "urakoitsijan urakan vastuuhenkilo"}]})

(defn ei-ole-oulun-urakan-urakoitsijan-urakkavastaava []
    {:sahkoposti "yit_uuvh@example.org", :kayttajanimi "yit_uuvh", :puhelin 43363123, :sukunimi "Urakkavastaava",
     :roolit #{"urakoitsijan urakan vastuuhenkilo"}, :id 17, :etunimi "Yitin",
     :organisaatio {:id 10, :nimi "YIT Rakennus Oy", :tyyppi "urakoitsija"},
     :urakkaroolit [{:urakka {:id 234234324234, ;;eli ei ole oulun urakan ID
                              :nimi "Oulun alueurakka 2005-2010", :urakoitsija {:nimi "YIT Rakennus Oy", :id 10},
                              :hallintayksikko {:nimi "Pohjois-Pohjanmaa ja Kainuu", :id 8}},
                     :luotu nil,
                     :rooli "urakoitsijan urakan vastuuhenkilo"}]})
