(ns harja.palvelin.raportointi.testiapurit
  (:require  [clojure.test :as t :refer [is]]
             [taoensso.timbre :as log]
             [clojure.core.match :refer [match]]))

(def raporttisolu? harja.domain.raportointi/raporttielementti?)

(defn raporttisolun-arvo [solu]
  (if (raporttisolu? solu)
    (let [solun-asetukset (second solu)]
      (or (:arvo solun-asetukset) solun-asetukset))

    solu))

(defn tyhja-raporttisolu? [solu]
  (and (raporttisolu? solu)
       (empty? (raporttisolun-arvo solu))
       (string? (raporttisolun-arvo solu))))

(defn tarkista-raportti [vastaus nimi]
  (match vastaus
         ([:raportti {:nimi nimi}
           & elementit] :seq)
         elementit
         :else (is false (str "Raportti ei ole oikean muotoinen! saatiin: " (pr-str vastaus)))))

(defn tarkista-taulukko-otsikko [taulukko otsikko]
  (let [taulukon-otsikko (:otsikko (second taulukko))]
    (is (= taulukon-otsikko otsikko)
        (str "Taulukon otsikko ei täsmää! " taulukon-otsikko " != " otsikko))))

(defn tarkista-taulukko-yhteensa [taulukko numero-sarake]
  (let [rivit (nth taulukko 3)
        laskettu-summa (reduce + 0 (keep #(nth % numero-sarake)
                                         (filter vector? (butlast rivit))))
        raportoitu-summa (nth (last rivit) numero-sarake)]
    (is (== laskettu-summa raportoitu-summa)
        (str "Laskettu ja raportoitu yhteensä summa ei täsmää: "
             laskettu-summa " != " raportoitu-summa))))

(defn tarkista-taulukko-sarakkeet [taulukko & sarakkeet]
  (let [taulukon-sarakkeet (nth taulukko 2)]
    (is (= (count taulukon-sarakkeet) (count sarakkeet))
        (str "Taulukossa on eri määrä sarakkeita. "
             (count taulukon-sarakkeet) " != " (count sarakkeet)))
    (dorun
     (map (fn [taulukon-sarake sarake]
            (let [taulukon-sarake* (select-keys taulukon-sarake (keys sarake))]
                 (is (= taulukon-sarake* sarake)
                     (str "Taulukon sarake ei täsmää vaadittuun: "
                          (pr-str taulukon-sarake) " != "
                          (pr-str sarake)))))
          taulukon-sarakkeet sarakkeet))))

(defn tarkista-taulukko-rivit [taulukko & rivit]
  (let [taulukon-rivit (nth taulukko 3)]
    (is (= (count taulukon-rivit) (count rivit))
        (str "Taulukossa eri määrä rivejä. "
             (count taulukon-rivit) " != " (count rivit)))
    (dorun
     (map (fn [taulukon-rivi rivi]
            (if (fn? rivi)
              (is (rivi taulukon-rivi)
                  (str "Taulukon rivi ei tyydytä annettua predikaattia, rivi: "
                       (pr-str taulukon-rivi) ", predikaatti: " rivi))
              (is (= taulukon-rivi rivi)
                  (str "Taulukon rivi ei täsmää vaadittuun: "
                       (pr-str taulukon-rivi) " != "
                       (pr-str rivi)))))
          taulukon-rivit rivit))))

(defn tarkista-taulukko-kaikki-rivit [taulukko rivi-pred-fn]
  (dorun
   (map-indexed (fn [i taulukon-rivi]
                  (is (rivi-pred-fn taulukon-rivi)
                      (str "Taulukon rivi " i " ei täsmää predikaattiin: "
                           (pr-str taulukon-rivi))))
                (nth taulukko 3))))

(defn tarkista-taulukko-kaikki-rivit-ja-yhteenveto
  [taulukko rivi-pred-fn viimeinen-rivi-pred-fn]
  #_(tarkista-taulukko-kaikki-rivit
    (assoc taulukko 3 (butlast (nth taulukko 3)))
    rivi-pred-fn)
  (tarkista-taulukko-kaikki-rivit
    (assoc taulukko 3 [(last (nth taulukko 3))])
    viimeinen-rivi-pred-fn))

(defn pylvaat-otsikolla
  [vastaus otsikko]
  (some #(when
           (and
             (vector? %)
             (= :pylvaat (first %))
             (= otsikko (:otsikko (second %))))
           %) vastaus))

(defn tarkista-pylvaat-otsikko
  [pylvaat otsikko]
  (is (= (:otsikko (second pylvaat) otsikko))))

(defn tarkista-pylvaat-legend
  [pylvaat otsikko]
  (is (= (:legend (second pylvaat) otsikko))))

(defn tarkista-pylvaat-data
  [pylvaat data]
  (is (= (nth pylvaat 2) data)))

(defmacro elementti [raportti elementin-match]
  `(let [loytynyt-elementti#
         (some (fn [elementti#]
                 (match elementti#
                        ~elementin-match elementti#
                        :else nil))
               (drop 2 ~raportti))]
     (is (not (nil? loytynyt-elementti#))
         (str "Elementtiä matcherilla ei löytynyt: " ~(pr-str elementin-match)))
     loytynyt-elementti#))

(defmacro taulukko-otsikolla [raportti otsikko]
  `(elementti ~raportti [:taulukko {:otsikko ~otsikko} sarakkeet# rivit#]))
