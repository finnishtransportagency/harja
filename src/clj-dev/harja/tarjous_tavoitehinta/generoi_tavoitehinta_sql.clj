(ns harja.tarjous-tavoitehinta.generoi-tavoitehinta-sql
  "Ottaa sisään CSV:n, ja generoi SQL:n, joka päivittää urakoiden tavoitehinnat tietokantaan."
  (:require [clojure.string :as str]))

;; --- Käyttöohje

(def test-csv
  ";;
;;
;MHU;MHU
;Oulu 19-24;Ii 19-24
;Oulu MHU 2019-2024, P;Ii MHU 2019-2024, P
Urakan tunnus;PR00012345;PR00011111
1. hoitovuosi (€);1 234 567,12;1 111 111,11
2. hoitovuosi (€);1 234 567,12;1 111 111,11
3. hoitovuosi (€);1 234 567,12;1 111 111,11
4. hoitovuosi (€);1 234 567,12;1 111 111,11
5. hoitovuosi (€);1 234 567,12;1 111 111,11")

(comment
  (generoi-sql test-csv)
  =>
  "UPDATE urakka_tavoite
   SET tarjous_tavoitehinta = tavoitehinnat.tavoitehinta
   FROM (
   VALUES
   ('PR00012345', 1, 1234567.12),
   ('PR00012345', 2, 1234567.12),
   ('PR00012345', 3, 1234567.12),
   ('PR00012345', 4, 1234567.12),
   ('PR00012345', 5, 1234567.12),
   ('PR00011111', 1, 1111111.11),
   ('PR00011111', 2, 1111111.11),
   ('PR00011111', 3, 1111111.11),
   ('PR00011111', 4, 1111111.11),
   ('PR00011111', 5, 1111111.11)
   ) AS tavoitehinnat (tunnus, hoitokausi, tavoitehinta)
   JOIN urakka u on u.sampoid = tavoitehinnat.tunnus
   WHERE urakka_tavoite.hoitokausi = tavoitehinnat.hoitokausi
   AND urakka_tavoite.urakka = u.id"

  (generoi-sql (slurp "tarjous-tavoitehinnat.csv")))

;; --- Toteutus

(def delimiter #";")

(defn rivi->sarakkeet [rivi]
  (vec (rest (str/split rivi delimiter))))

(defn hinta-str->number [hinta-str]
  (some-> hinta-str
          (str/replace #" " "")
          (str/replace #" " "")
          (str/replace #"," ".")
          bigdec))

(defn csv->clojure-data [csv-str]
  (let [rivit (str/split-lines csv-str)
        tunnus-rivi (get rivit 5)
        tunnukset (rivi->sarakkeet tunnus-rivi)
        hoitovuosi-rivit [(get rivit 6)
                          (get rivit 7)
                          (get rivit 8)
                          (get rivit 9)
                          (get rivit 10)]
        hoitovuosien-hinnat (mapv rivi->sarakkeet hoitovuosi-rivit)]
    (->> tunnukset
         (map-indexed (fn [i tunnus]
                        {:tunnus        tunnus
                         :tavoitehinnat (mapv
                                          (fn [hoitovuoden-hinnat]
                                            (assert (= (count hoitovuoden-hinnat) (count tunnukset)) "Rivien pituudet eivät täsmää")
                                            (-> (get hoitovuoden-hinnat i)
                                                hinta-str->number))
                                          hoitovuosien-hinnat)})))))

(def sql-format-string
"UPDATE urakka_tavoite
SET tarjous_tavoitehinta = tavoitehinnat.tavoitehinta
FROM (
VALUES
%s
) AS tavoitehinnat (tunnus, hoitokausi, tavoitehinta)
JOIN urakka u on u.sampoid = tavoitehinnat.tunnus
WHERE urakka_tavoite.hoitokausi = tavoitehinnat.hoitokausi
AND urakka_tavoite.urakka = u.id")

(defn clojure-data->sql
  [clojure-data]
  (let [values (->> clojure-data
                    (map (fn [{:keys [tunnus tavoitehinnat]}]
                           (map-indexed (fn [i tavoitehinta]
                                          (let [hoitokausi (inc i)]
                                            (format "('%s', %s, %s)"
                                                    tunnus
                                                    hoitokausi
                                                    (str tavoitehinta))))
                                        tavoitehinnat)))
                    flatten
                    (str/join ",\n"))]
    (format
      sql-format-string
      values)))

(defn generoi-sql [csv-str]
  (-> csv-str
      csv->clojure-data
      clojure-data->sql))
