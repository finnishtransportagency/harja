(ns harja.tyokalut.yleiset
  #?@(:clj  [(:require [clojure.pprint :refer [pprint]])]
      :cljs [(:require [cljs.pprint :refer [pprint]])]))

(defn round2
  "Round a double to the given precision (number of significant digits)"
  [precision d]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* d factor)) factor)))

(defn printdenty
  "Auttaa tulostamaan ja palauttamaan arvoja thredingissa:
    (-> dataa
      (map jotain)
      printdenty
      (jotain-muuta nonnoo)
      (printdenty \"debuggia: \"))

  tai
    (->> dataa
        (jotain boo)
        printdenty
        (jotain-muuta nonnoo)
        (printdenty \"debuggia: \"))"
  ([x]
   (printdenty x ""))
  ([x y]
   (let [f (fn [prefix object]
             (println prefix object)
             object)]
     (if (string? x)
       (f x y)
       (f y x)))))

(defn prettydenty
  "Auttaa tulostamaan ja palauttamaan arvoja thredingissa.

  (-> dataa
      (map jotain)
      prettydentity
      (jotain-muuta noo)
      (prettydentity \"debuggia: \"))

  tai:
  (->> dataa
       (jotain blaa)
       prettydentity
       (jotain-muuta boo)
       (prettydentity \"debuggia: \"))"
  ([x]
   (prettydenty x ""))
  ([x y]
   (let [f (fn [prefix object]
             (print prefix)
             (pprint object)
             object)]
     (if (string? x)
       (f x y)
       (f y x)))))

(defn vuosi-hoitokauden-numerosta-ja-kuukaudesta [hoitokauden-numero kuukausi urakan-aloitusvuosi]
  (cond
    (and (>= kuukausi 10) (<= kuukausi 12)
      (= 1 hoitokauden-numero)) urakan-aloitusvuosi
    (and (>= kuukausi 1) (<= kuukausi 9)
      (= 1 hoitokauden-numero)) (+ hoitokauden-numero urakan-aloitusvuosi)
    (and (>= kuukausi 10) (<= kuukausi 12)
      (> hoitokauden-numero 1)) (dec (+ hoitokauden-numero urakan-aloitusvuosi))
    (and (>= kuukausi 1) (<= kuukausi 9)
      (> hoitokauden-numero 1)) (+ hoitokauden-numero urakan-aloitusvuosi)))


(defn liita-yhteen-mapit-ja-korvaa-avain 
  "Tekee join-liitokseksen kahden kokoelman valilla jotka sisaltavat mappeja
   Parametrit:
  * kokoelma1: Kokoelma mappeja johon liitos tehdään
  * kokoelma2: Kokoelma mappeja josta löytyy liitettävä arvo
  * yhteinen-key1: Avain kokoelma1:ssa jota kaytetaan liitokseen
  * yhteinen-key2: Avain jolla poimitaan arvo kokoelma2:sta
  * etsittava-avain: Kokoelma2:sta etsitaan kaikki taman avaimen arvot
  * asetettava-avain: Etsityt arvot asetetaan asetettava-avain nimelle liitettyna kokoelma1:een"
  [{:keys [kokoelma1 kokoelma2 yhteinen-key1 yhteinen-key2 etsittava-avain asetettava-avain] :as m}]
  (map (fn [item1]
         (let [yhteinen-arvo (get-in item1 yhteinen-key1)]
           (merge
             item1
             {asetettava-avain (vec (->> kokoelma2
                                      (filter #(= yhteinen-arvo (get-in % yhteinen-key2)))
                                      (map #(get-in % etsittava-avain))))}))) kokoelma1))