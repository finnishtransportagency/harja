(ns harja.palvelin.raportointi.raportit.sanktioraportti-yhteiset
  (:require
    [harja.domain.laadunseuranta.sanktio :as sanktiot-domain]
    [jeesql.core :refer [defqueries]]
    [harja.tyokalut.functor :refer [fmap]]
    [clojure.string :as str]
    [harja.kyselyt.konversio :as konv]))

(defn- rivi-kuuluu-talvihoitoon? [rivi]
  (if (:toimenpidekoodi_taso2 rivi)
    (= (str/lower-case (:toimenpidekoodi_taso2 rivi)) "talvihoito")
    false))

(defn- suodata-sakot [rivit {:keys [urakka-id hallintayksikko-id sakkoryhma talvihoito? sanktiotyyppi] :as suodattimet}]
  (filter
    (fn [rivi]
      (and
        (sanktiot-domain/sakkoryhmasta-sakko? rivi)
        (or (nil? sakkoryhma) (if (set? sakkoryhma)
                                (sakkoryhma (:sakkoryhma rivi))
                                (= sakkoryhma (:sakkoryhma rivi))))
        (or (nil? urakka-id) (= urakka-id (:urakka-id rivi)))
        (or (nil? hallintayksikko-id) (= hallintayksikko-id (:hallintayksikko_id rivi)))
        (or (nil? sanktiotyyppi) (str/includes? (str/lower-case (:sanktiotyyppi_nimi rivi)) (str/lower-case sanktiotyyppi)))
        (or (nil? talvihoito?) (= talvihoito? (rivi-kuuluu-talvihoitoon? rivi)))))
    rivit))

(defn- suodata-muistutukset [rivit {:keys [urakka-id hallintayksikko-id talvihoito?] :as suodattimet}]
  (filter
    (fn [rivi]
      (and
        (not (sanktiot-domain/sakkoryhmasta-sakko? rivi))
        (or (nil? urakka-id) (= urakka-id (:urakka-id rivi)))
        (or (nil? hallintayksikko-id) (= hallintayksikko-id (:hallintayksikko_id rivi)))
        (or (nil? talvihoito?) (= talvihoito? (rivi-kuuluu-talvihoitoon? rivi)))))
    rivit))

(defn muistutusten-maara
  ([rivit] (muistutusten-maara rivit {}))
  ([rivit suodattimet]
   [:arvo-ja-yksikko {:arvo (count (suodata-muistutukset rivit suodattimet))
                      :yksikko " kpl"
                      :fmt? false}]))


(defn sakkojen-summa
  ([rivit] (sakkojen-summa rivit {}))
  ([rivit suodattimet]
   (let [laskettavat (suodata-sakot rivit suodattimet)]
     (reduce + (map
                 #(or (:summa %) 0)
                 laskettavat)))))

(defn- indeksien-summa
  ([rivit] (indeksien-summa rivit {}))
  ([rivit suodattimet]
   (let [laskettavat (suodata-sakot rivit suodattimet)]
     (reduce + (map
                 #(or (:indeksikorotus %) 0)
                 laskettavat)))))


(defn luo-rivi-sakkojen-summa
  ([otsikko rivit alueet]
   (luo-rivi-sakkojen-summa otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [otsikko] (mapv (fn [alue]
                                            (sakkojen-summa rivit (merge optiot alue)))
                                          alueet))]
     (if yhteensa-sarake?
       (conj rivi (sakkojen-summa rivit optiot))
       rivi))))

(defn luo-rivi-muistutusten-maara
  ([otsikko rivit alueet]
   (luo-rivi-muistutusten-maara otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [otsikko] (mapv (fn [alue]
                                            (muistutusten-maara rivit (merge optiot alue)))
                                          alueet))]
     (if yhteensa-sarake?
       (conj rivi (muistutusten-maara rivit optiot))
       rivi))))

(defn luo-rivi-indeksien-summa
  ([otsikko rivit alueet]
   (luo-rivi-indeksien-summa otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [otsikko] (mapv (fn [alue]
                                            (indeksien-summa rivit (merge optiot alue)))
                                          alueet))]
     (if yhteensa-sarake?
       (conj rivi (indeksien-summa rivit optiot))
       rivi))))

(defn luo-rivi-kaikki-yht
  ([otsikko rivit alueet] (luo-rivi-kaikki-yht otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [otsikko] (mapv (fn [alue]
                                            (+ (sakkojen-summa rivit alue)
                                               (indeksien-summa rivit alue)))
                                          alueet))]
     (if yhteensa-sarake?
       (conj rivi (+ (sakkojen-summa rivit)
                     (indeksien-summa rivit)))
       rivi))))

(def +muistutusrivin-nimi-hoito+ "Muistutukset yht.")
(def +muistutusrivin-nimi-yllapito+ "Muistutukset")

(def +sakkorivin-nimi-hoito+ "Kaikki sakot yht.")
(def +sakkorivin-nimi-yllapito+ "Sakot (-) ja bonukset (+)")

(defn raporttirivit-yhteensa [rivit alueet {:keys [yhteensa-sarake? urakkatyyppi] :as optiot}]
  (let [yllapito? (or (= urakkatyyppi :paallystys)
                      (= urakkatyyppi :paikkaus)
                      (= urakkatyyppi :tiemerkinta))
        sakkorivin-nimi (if yllapito?
                          +sakkorivin-nimi-yllapito+
                          +sakkorivin-nimi-hoito+)
        muistutusrivin-nimi (if yllapito?
                              +muistutusrivin-nimi-yllapito+
                              +muistutusrivin-nimi-hoito+)]
    (keep identity
          [{:otsikko "Yhteensä"}
           (luo-rivi-muistutusten-maara muistutusrivin-nimi rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
           (when-not yllapito?
             (luo-rivi-indeksien-summa "Indeksit yht." rivit alueet {:yhteensa-sarake? yhteensa-sarake?}))
           (luo-rivi-sakkojen-summa sakkorivin-nimi rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
           (when-not yllapito?
             (luo-rivi-kaikki-yht "Kaikki yht." rivit alueet {:yhteensa-sarake? yhteensa-sarake?}))])))