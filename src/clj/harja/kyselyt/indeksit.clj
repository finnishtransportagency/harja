(ns harja.kyselyt.indeksit
  (:require [jeesql.core :refer [defqueries]]
            [clojure.java.jdbc :as jdbc]
            [harja.tyokalut.yleiset :refer [round2]]
            [specql.core :refer [fetch]]
            [harja.pvm :as pvm]
            [harja.domain.urakka :as ur]))

(defqueries "harja/kyselyt/indeksit.sql"
  {:positional? true})

(declare hae-urakan-hoitovuoden-indeksit-kuukausinimilla hae-urakan-indeksin-perusluku hae-indeksi)

;; Osittain duplikaatti, kunnes vanha kustannussuunnitelma poistetaan
(defn hae-urakan-indeksikertoimet
  [db urakka-id]
  (jdbc/with-db-transaction [db db]
    (let [pyorista #(/ (Math/round (* %1 (Math/pow 10 %2))) (Math/pow 10 %2))
          {::ur/keys [alkupvm loppupvm indeksi]} (first (fetch db
                                                          ::ur/urakka
                                                          #{::ur/alkupvm ::ur/loppupvm ::ur/indeksi}
                                                          {::ur/id urakka-id}))
          urakan-alkuvuosi (-> alkupvm pvm/joda-timeksi pvm/suomen-aikavyohykkeeseen pvm/vuosi)
          urakan-loppuvuosi (-> loppupvm pvm/joda-timeksi pvm/suomen-aikavyohykkeeseen pvm/vuosi)
          vertailu-kk-mhu (fn [urakan-akuvuosi]
                            (cond
                              ;; HOX!!
                              ;; Jos tähän tulee sopimusmuutoksia, tee samat muutokset sql funktioon: indeksikorjaa
                              ;;
                              ;; 2023 ja jälkeen alkavilla urakoilla käytetään indeksin tarkastelukuukautena elokuuta
                              (>= urakan-akuvuosi 2023) 8
                              ;; Muihin aikoihin alkavilla urakoilla käytetään tarkastelukuukautena syyskuuta
                              :else 9))
          perusluku (:perusluku (first (hae-urakan-indeksin-perusluku db {:urakka-id urakka-id})))
          indeksiluvut-urakan-aikana (when perusluku
                                       (sequence
                                         (comp (filter (fn [{:keys [kuukausi vuosi]}]
                                                         (and (= (vertailu-kk-mhu urakan-alkuvuosi) kuukausi)
                                                           (>= vuosi urakan-alkuvuosi))))
                                           (remove (fn [{:keys [vuosi]}]
                                                     (>= vuosi urakan-loppuvuosi)))
                                           (map (fn [{:keys [arvo vuosi]}]
                                                  {:vuosi vuosi
                                                   ;; Halutaan numero kolmen desimaalin tarkkuudella. Pelataan sen varaan, että indeksikerroin
                                                   ;; Ei nouse yli kymmenen, jolloin with-precision 4 riittää.
                                                   ;; Ratkaisu pyöristää indeksikerrointa. Tämä on sovittu käytäntö ELYissä ja perustuu myös siihen,
                                                   ;; että tilastokeskus ilmaisee indeksikertoimen kolmella desimaalilla (prosentin kymmenyksen tarkkuudella).
                                                   :indeksikerroin (pyorista (with-precision 4 (/ arvo perusluku)) 3)})))
                                         (hae-indeksi db {:nimi indeksi})))
          urakan-indeksien-maara (count indeksiluvut-urakan-aikana)]
      (if (= 5 urakan-indeksien-maara)
        (vec indeksiluvut-urakan-aikana)
        (mapv (fn [index]
                (if (empty? indeksiluvut-urakan-aikana)
                  ;;Palautetaan nil indeksikertoimeksi urakoille, jotka eivät ole vielä alkaneet.
                  nil
                  ;; Palautetaan indeksit vain hoitovuosille, joilla on indeksejä.
                  ;; Lopuille hoitovuosille nil.
                  (nth indeksiluvut-urakan-aikana index nil)))
          (range 0 5))))))

(defn indeksikerroin
  "Palauttaa indeksikertoimen annetulle hoitovuoden järjestysnumerolle."
  [urakan-indeksit hoitovuosi-nro]
  (let [{:keys [indeksikerroin]} (get urakan-indeksit (dec hoitovuosi-nro))]
    indeksikerroin))

(defn indeksikorjaa
  ([indeksikerroin summa]
   (when (and indeksikerroin summa)
     ;; Laske indeksikorjaus ja pyöristä tulos kuuden desimaalin tarkkuuteen
     (round2 6 (* summa indeksikerroin)))))
