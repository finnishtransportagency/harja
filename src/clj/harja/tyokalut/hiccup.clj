(ns harja.tyokalut.hiccup)

(declare map->hiccup)

(defn vector->hiccup
  "Muuntaa Clojure-vectorin Hiccup-muotoon, josta se voidaan edelleen muuntaa esim. HTML- tai XML-muotoon.
  Vectorin jokaisen itemin täytyy olla map, jonka ensimmäinen item on avain. Tämä avain määrittelee
  isäntä-tagin avaimen arvolle, joka on joko map tai vector.

  Esim 1: (tekstit ovat stringejä, lainausmerkit poistettu luettavuuden helpottamiseksi)
  Muodosta: [{:hedelma {:nimi Ananas}}
             {:hedelma {:nimi Banaani}}]
  Muotoon: [:hedelma
            [:nimi Ananas]]
           [:hedelma
            [:nimi Banaani]]

  Esim 2. (tekstit ovat stringejä, lainausmerkit poistettu luettavuuden helpottamiseksi)
  Muodosta: [{:hedelmat [{:hedelma {:nimi Ananas
                                    :vari Keltainen}}
                         {:hedelma {:nimi Tomatti
                                    :vari Punainen}}]}
             {:vihannes {:nimi Pinaatti
                         :maku Keskiverto}}]
  Muotoon: [[:hedelmat
             [:hedelma
              [:nimi Ananas]
              [:vari Keltainen]]
             [:hedelma
              [:nimi Tomatti]
              [:vari Punainen]]]
            [:vihannes
             [:nimi Pinaatti]
             [:maku Keskiverto]]]"
  [data]
  (mapv
    (fn [item]
      (let [isanta-tagi (first (keys item))
            arvo (isanta-tagi item)]
        (reduce
          conj
          [isanta-tagi]
          (if (map? arvo)
            (map->hiccup arvo)
            (vector->hiccup arvo)))))
    data))

(defn map->hiccup
  "Muuntaa Clojure-mapin Hiccup-muotoon, josta se voidaan edelleen muuntaa esim. HTML- tai XML-muotoon.
  HUOM. Avaimet eivät välttämättä tule samassa järjestyksessä.

  Esim. (tekstit ja numerot ovat stringejä, lainausmerkit poistettu luettavuuden helpottamiseksi)
  Muodosta: {:nimi  {:etunimi  Harri
                     :sukunimi Harjaaja}
            :tykkaa [{:hedelma {:nimi Ananas
                                :maku Hyvä}}
                     {:hedelma {:nimi Banaani
                                :maku Keskiverto}}]
            :yhteys {:kotipuhelin  123
                     :matkapuhelin 1234
            :email  {:tyo  tyo@asd.com
                     :koti koti@asd.com}}}
  Muotoon: [:nimi
            [:etunimi Harri]
            [:sukunimi Harjaaja]]
           [:tykkaa
            [:hedelma
             [:nimi Ananas]
             [:maku Hyvä]]
            [:hedelma
             [:nimi Banaani]
             [:maku Keskiverto]]]
           [:yhteys
            [:kotipuhelin 123]
            [:matkapuhelin 1234]
            [:email
             [:tyo tyo@asd.com]
             [:koti koti@asd.com]]]"
  [data]
  (mapv (fn [avain]
          (if (map? (avain data))
            (reduce
              conj
              [avain]
              (map->hiccup (avain data)))
            (if (vector? (avain data))
              (reduce
                conj
                [avain]
                (vector->hiccup (avain data)))
              [avain (avain data)])))
        (keys data)))