(ns harja.kyselyt.konversio
  "Usein resultsettien dataa joudutaan jotenkin muuntamaan sopivampaan muotoon Clojure maailmaa varten.
  Tähän nimiavaruuteen voi kerätä yleisiä. Yleisesti konversioiden tulee olla funktioita, jotka prosessoivat
  yhden rivin resultsetistä, mutta myös koko resultsetin konversiot ovat mahdollisia."
  (:require [cheshire.core :as cheshire]
            [clj-time.coerce :as coerce]
            [taoensso.timbre :as log]
            [clj-time.format :as format]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [harja.pvm :as pvm])
  (:import (clojure.lang Keyword)
           (java.io ByteArrayOutputStream ObjectOutputStream)))


(defn yksi
  "Ottaa resultsetin ja palauttaa ensimmäisen rivin ensimmäisen arvon,
  tämä on tarkoitettu single-value queryille, kuten vaikka yhden COUNT arvon hakeminen."
  [rs]
  (second (ffirst rs)))

(defn organisaatio
  "Muuntaa (ja poistaa) :org_* kentät muotoon :organisaatio {:id ..., :nimi ..., ...}."
  [rivi]
  (-> rivi
      (assoc :organisaatio {:id (:org_id rivi)
                            :nimi (:org_nimi rivi)
                            :tyyppi (some-> rivi :org_tyyppi keyword)
                            :lyhenne (:org_lyhenne rivi)
                            :ytunnus (:org_ytunnus rivi)})
      (dissoc :org_id :org_nimi :org_tyyppi :org_lyhenne :org_ytunnus)))

(defn sarakkeet-vektoriin
  "Muuntaa muodon: [{:id 1 :juttu {:id 1}} {:id 1 :juttu {:id 2}}]
  muotoon:         [{:id 1 :jutut [{:id 1} {:id 2}]}]

  Usein tietokantahakuja tehdessä on tilanne, jolloin 'emorivi' sisältää useamman 'lapsirivin',
  esimerkiksi ilmoitus sisältää 0-n kuittausta. Suoraan tietokantahaussa näitä on kuitenkin
  vaikea yhdistää yhteen tietorakenteeseen. Tämän funktion avulla yhdistäminen onnistuu.
  Muunna aluksi rivien rakenne nested mapiksi alaviiva->rakenne funktiolla, ja syötä
  tulos tälle funktiolle.

  Rivien yhdistäminen tehdän oletusarvoisesti id:n perusteella, mutta 'emorivin' group-by funktion voi
  myös syöttää itse. Esim [{:toteuma {:id 1} :reittipiste {..}} ..] voidaan haluta groupata funktiolla
  #(get-in % [:toteuma :id])

  Parametrit:
  * kaikki-rivit: Vektori mäppejä, jossa yksi 'rivi' sisältää avaimen 'lapselle', joka on mäppi.
    * [{:ilmoitus-id 1 :kuittaus {:kuittaus-id 1}} {:ilmoitus-id 1 :kuittaus {:kuittaus-id 2}}]
  * sarake-vektori: Mäppi joka kertoo, mihin muotoon rivit muutetaan. Avain on
    yhden lapsirivin nimi, arvo on vektorin nimi, johon lapset tallennetaan.
      * (sarakkeet-vektoriin ilmoitukset {:kuittaus :kuittaukset})
  * group-fn: Funktio, joka ryhmittelee rivit ennen sarakkeiden yhdistämistä. Voidaan käyttää esim.
    saman id:n sisältävien rivien yhdistämiseen, koska kuvaavat loogisesti samaa asiaa.
  * lapsi-ok-fn: funktio, joka tarkistaa onko lapsi kelpo, oletuksena :id. Jos funktio palautta
    ei-truthy arvon, lasta ei lisätä vektoriin

  Funktio osaa käsitellä useamman 'lapsirivin' kerralla, tämä onnistuu yksinkertaisesti syöttämällä
  sarake-vektoriin useamman avain-arvo -parin.

  TÄRKEÄÄ! Jos lapsiriviä on useampia, konversio PITÄÄ tehdä yhdellä kutsulla (eli antamalla useampi
  avain-arvo -pari mäppiin). Funktio tunnistaa uniikit rivit kaikista-riveistä poistamalla lapsirivit,
  joten jos kaikkia lapsirivejä ei määrittele, ei konversio toimi oikein."
  ([kaikki-rivit sarake-vektori]
   (sarakkeet-vektoriin kaikki-rivit sarake-vektori :id :id))
  ([kaikki-rivit sarake-vektori group-fn]
   (sarakkeet-vektoriin kaikki-rivit sarake-vektori group-fn :id))
  ([kaikki-rivit sarake-vektori group-fn lapsi-ok-fn]
   (vec
     (for [[id rivit] (group-by group-fn kaikki-rivit)]
       (loop [rivi (first rivit)
              [[sarake vektori] & sarakkeet] (seq sarake-vektori)]
         (if-not sarake
           rivi
           (recur (-> rivi
                      (dissoc sarake)
                      (assoc vektori (vec (into #{}
                                                (keep #(when-let [lapsi (get % sarake)]
                                                        (when (lapsi-ok-fn lapsi)
                                                          lapsi))
                                                      rivit)))))
                  sarakkeet)))))))

(defn alaviiva->rakenne
  "Muuntaa mäpin avaimet alaviivalla sisäiseksi rakenteeksi, esim.
  {:id 1 :urakka_hallintayksikko_nimi \"POP ELY\"} => {:id 1 :urakka {:hallintayksikko {:nimi \"POP ELY\"}}}"
  [m]
  (let [ks (into []
                 (comp (map name)
                       (filter #(when (not= -1 (.indexOf % "_")) %))
                       (map (fn [k]
                              [(keyword k)
                               (into []
                                     (map keyword)
                                     (.split k "_"))])))
                 (keys m))]
    (loop [m m
           [[vanha-key uusi-key] & ks] ks]
      (if-not vanha-key
        m
        (let [arvo (get m vanha-key)]
          (recur (assoc-in (dissoc m vanha-key)
                           uusi-key arvo)
                 ks))))))

(defn vector-mappien-alaviiva->rakenne
  "Muuntaa vectorissa olevien mäppien avaimet alaviivalla sisäiseksi rakenteeksi."
  [vector]
  (mapv #(alaviiva->rakenne %) vector))

(defn muunna
  "Muuntaa mäpin annetut keyt muunnos-fn funktiolla. Nil arvot menevät läpi sellaisenaan ilman muunnosta."
  [rivi kentat muunnos-fn]
  (loop [rivi rivi
         [k & kentat] kentat]
    (if-not k
      rivi
      (if (vector? k)
        (let [arvo (get-in rivi k)]
          (recur (if arvo
                   (assoc-in rivi k (muunnos-fn arvo))
                   rivi)
                 kentat))
        (let [arvo (get rivi k)]
          (recur (if arvo
                   (assoc rivi k (muunnos-fn arvo))
                   rivi)
                 kentat))))))

(defn string->keyword
  "Muuttaa annetut kentät keywordeiksi, jos ne eivät ole NULL."
  [rivi & kentat]
  (muunna rivi kentat keyword))

(defn string-polusta->keyword
  "Muuntaa annetussa polussa olevan stringin Clojure-keywordiksi"
  [data avainpolku]
  (-> data
      (assoc-in avainpolku (keyword (get-in data avainpolku)))))

(defn string-poluista->keyword
  "Muuntaa annetuissa poluissa olevan stringin Clojure-keywordiksi"
  [data avainpolut]
  (reduce (fn [data polku]
            (assoc-in data polku (keyword (get-in data polku))))
          data
          avainpolut))

(defn decimal->double
  "Muuntaa postgresin tarkan numerotyypin doubleksi."
  [rivi & kentat]
  (muunna rivi kentat double))

(defn seq->array
  "Muuntaa arvot Clojure-kokoelmasta JDBC arrayksi.
   Itemien tulisi olla joko tekstiä, numeroita tai keywordeja, sillä
   ne muunnetaan aina tekstiksi."
  [collection]
  (let [kasittele #(if (= Keyword (type %))
                    (name %)
                    (str %))]
    (str "{" (clojure.string/join "," (map kasittele collection)) "}")))

(defn string-vector->keyword-vector
  "Muuntaa mapin kentän vectorissa olevat stringit keywordeiksi."
  [rivi kentta]
  (assoc rivi kentta (mapv keyword (kentta rivi))))

(defn string-set->keyword-set
  "Muuntaa mapin kentän setissä olevat stringit keywordeiksi."
  [rivi kentta]
  (assoc rivi kentta (set (map keyword (kentta rivi)))))

(defn array->vec
  "Muuntaa rivin annetun kentän JDBC array tyypistä Clojure-vektoriksi."
  [rivi kentta]
  (assoc rivi
    kentta (if-let [a (get rivi kentta)]
             (vec (.getArray a))
             [])))

(defn array->set
  "Muuntaa rivin annetun kentän JDBC array tyypistä Clojure hash setiksi.
  Yhden arityn versio ottaa JDBC arrayn ja paluttaa setin ilman mäppiä."
  ([a]
   (into #{} (and a (.getArray a))))
  ([rivi kentta] (array->set rivi kentta identity))
  ([rivi kentta muunnos]
   (assoc rivi
     kentta (if-let [a (get rivi kentta)]
              (into #{} (map muunnos (.getArray a)))
              #{}))))

(defn array->keyword-set
  "Muuntaa rivin annentun kentän JDBC array tyypistä Clojure keyword hash setiksi."
  [rivi kentta]
  (array->set rivi kentta keyword))

(defn sql-date
  "Luo java.sql.Date objektin annetusta java.util.Date objektista."
  [^java.util.Date dt]
  (when dt
    (java.sql.Date. (.getTime dt))))

(defn sql-timestamp
  "Luo java.sql.Timestamp objektin annetusta java.util.Date objektista."
  [^java.util.Date dt]
  (when dt
    (java.sql.Timestamp. (.getTime dt))))

(defn java-date
  "Luo java.util.Date objektin annetusta java.sql.Date  objektista."
  [^java.sql.Date dt]
  (when dt
    (java.util.Date. (.getTime dt))))

(defn unix-date->java-date
  "Luo java.util.Date objektin annetusta unix-timestampista (sekunteja)."
  [unix-date]
  (java.util.Date. unix-date))

(defn jsonb->clojuremap
  "Muuntaa JSONin Clojuremapiksi"
  ([json]
   (some-> json
           .getValue
           (cheshire/decode true)))
  ([json avain]
   (-> json
       (assoc avain
              (some-> json
                      avain
                      .getValue
                      (cheshire/decode true))))))


(defn keraa-tr-kentat
  "Kuin alaviiva->rakenne, mutta vain TR kentille."
  [rivi]
  (-> rivi
      (assoc
       :tr {:numero (:tr_numero rivi)
            :alkuosa (:tr_alkuosa rivi)
            :alkuetaisyys (:tr_alkuetaisyys rivi)
            :loppuosa (:tr_loppuosa rivi)
            :loppuetaisyys (:tr_loppuetaisyys rivi)})
      (dissoc :tr_numero :tr_alkuosa :tr_alkuetaisyys :tr_loppuosa :tr_loppuetaisyys)))

(defn- lue-pgobject [pgobject]
  (let [string (str pgobject)]
    (assert (and (str/starts-with? string "(")
                 (str/ends-with? string ")"))
            "PGobject tulee alkaa '(' ja päättyä ')'")
    (let [string (subs string 1 (dec (count string)))
          len (count string)]
      (loop [acc []
             pos 0]
        (if (>= pos len)
          (if (= pos len)
            (conj acc "")
            acc)
          (cond
            (= \" (.charAt string pos))
            ;; Quotattu merkkijono, etsitään loppuquote
            (let [new-pos (.indexOf string (int \") (inc pos))]
              (assert (not= -1 new-pos)
                      "Päättymätön merkkijono, päättävää \" merkkiä ei löydy.")
              (recur (conj acc (subs string (inc pos) new-pos))
                     ;; Hypätään " ja , merkkien yli
                     (+ new-pos 2)))

            (= \, (.charAt string pos))
            ;; Tyhjä arvo
            (recur (conj acc "") (inc pos))

            ;; Ei quotattu arvo, etsitään pilkku
            :else
            (let [new-pos (.indexOf string (int \,) (inc pos))]
              (if (= -1 new-pos)
                ;; Ei löydy pilkkua, tämä on viimeinen arvo
                (conj acc (subs string pos len))

                ;; Pilkku löytyy, lisää arvoja
                (recur (conj acc (subs string pos new-pos))
                       (inc new-pos))))))))))

(def ^:private lue-pgobject-date
  (partial pvm/parsi pvm/pgobject-format))

(defn pgobject->map
  "Muuntaa resultsetissä olevan record tyyppisen arvon (PGobject)
  mäpiksi. Tulosmäpissä olevat avaimet ja arvojen tyypit annetaan
  siinä järjestyksessä kuin ne esiintyy objektissa."
  [pgobject & kenttien-nimet-ja-tyypit]
  (let [kentat (partition 2 kenttien-nimet-ja-tyypit)
        kentat-str (lue-pgobject pgobject)]
    (assert (= (count kentat) (count kentat-str))
            (str "Odotettu kenttien määrä: " (count kentat)
                 ", saatu kenttien määrä: " (count kentat-str)
                 ", data: " pgobject))

    (loop [m {}
           [[nimi tyyppi] & kentat] kentat
           [arvo & arvot] kentat-str]
      (if-not nimi
        m
        (recur
         (assoc m nimi
                (case tyyppi
                  :long (Long/parseLong arvo)
                  :double (Double/parseDouble arvo)
                  :string arvo
                  :date (lue-pgobject-date arvo)
                  (assert false (str "Ei tuettu tyyppi: " tyyppi ", arvo: " arvo))))
         kentat
         arvot)))))

(defn lue-tr-osoite
  "Lukee yrita_tierekisteriosoite_pisteelle2 sprocin palauttaman arvon tekstimuodosta
  Tierekisteriosoite mäpiksi. Esim. \"(20,1,0,5,100,102012010220)\"."
  [osoite]
  (and osoite
       (str/starts-with? osoite "(")
       (str/ends-with? osoite ")")
       (zipmap [:numero :alkuosa :alkuetaisyys :loppuosa :loppuetaisyys]
               (mapv (fn [arvo]
                       (when (not-empty arvo)
                         (Integer/parseInt arvo)))
                     (take 5
                           (str/split (str/replace osoite #"\(|\)" "") #","))))))

(defn pgarray->vector
  "Muuntaa annetun pg array vectoriksi"
  [array]
  (when (and (not (nil? array))
             (= org.postgresql.jdbc.PgArray (type array)))
    (vec(.getArray array))))

(defn lue-tr-piste
  [osoite]
  (select-keys (lue-tr-osoite osoite)
               [:numero :alkuosa :alkuetaisyys]))

(extend-protocol jdbc/ISQLValue
  java.util.Date
  (sql-value [v]
    (sql-timestamp v)))

(defn turvalaiteryhman-turvalaitteet->array
  [turvalaiteryhma]
  (assoc turvalaiteryhma :turvalaitteet (seq->array (map #(Integer. %) (:turvalaitteet turvalaiteryhma)))))

(defn str->hx [teksti]
  (apply str
         (map #(case %
                 \space "%20"
                 \! "%21"
                 \" "%22"
                 \# "%23"
                 \% "%25"
                 \& "%26"
                 \' "%27"
                 \( "%28"
                 \) "%29"
                 \* "%2A"
                 \+ "%2B"
                 \´ "%2C"
                 \- "%2D"
                 \. "%2E"
                 \/ "%2F"
                 \: "%3A"
                 \; "%3B"
                 \< "%3C"
                 \= "%3D"
                 \> "%3E"
                 \? "%3F"
                 \@ "%40"
                 \[ "%5B"
                 \\ "%5C"
                 \] "%5D"
                 \^ "%5E"
                 \_ "%5F"
                 \` "%60"
                 \{ "%7B"
                 \} "%7D"
                 \| "%7C"
                 \~ "%7E"
                 %)
              teksti)))

(defn to-byte-array [x]
  (with-open [out (ByteArrayOutputStream.)
              os (ObjectOutputStream. out)]
    (.writeObject os x)
    (.toByteArray out)))
