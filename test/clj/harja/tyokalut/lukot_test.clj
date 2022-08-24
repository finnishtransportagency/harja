(ns harja.tyokalut.lukot-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [harja.testi :refer :all]
    [harja.palvelin.tyokalut.lukot :as lukot]
    [harja.kyselyt.lukot :as qk]))

(def +testilukko+ "TESTI")
(def +testilukko2+ "testilukko2")
(def +testilukko3+ "testilukko3")
(def +testilukko4+ "testilukko4")

(defn tarkista-lukon-avaus [tunniste]
  (let [lukko (first (q (str "SELECT tunniste, lukko, lukittu FROM lukko WHERE tunniste = '" tunniste "'")))]
    (is (= tunniste (first lukko)))
    (is (some? (nth lukko 2)) "Lukkoa ei avata, vaan jätetään voimaan aikaleimalla")))

(deftest tarkista-lukon-kanssa-ajaminen
  (let [db (luo-testitietokanta)
        muuttuja (atom nil)
        toiminto-fn (fn [] (reset! muuttuja true))]
    (is (lukot/yrita-ajaa-lukon-kanssa db +testilukko+ toiminto-fn 1) "Lukko saatiin asetettua oikein")
    (is @muuttuja "Toiminto ajettiin oikein")
    (tarkista-lukon-avaus +testilukko+)))

(deftest tarkista-lukittu-ajo
  (let [db (luo-testitietokanta)
        muuttuja (atom nil)
        toiminto-fn (fn [] (reset! muuttuja true))]
    (is (qk/aseta-lukko? db +testilukko2+ nil) "Lukon asettaminen onnistui")
    (is (false? (lukot/yrita-ajaa-lukon-kanssa db +testilukko2+ toiminto-fn)) "Lukkoa ei saatu asetettua")
    (is (nil? @muuttuja) "Toimintoa ei ajettu")
    (is (qk/avaa-lukko? db +testilukko2+) "Lukon avaaminen onnistui")))

(deftest tarkista-aikapohjaisen-lukon-ajo
  (let [db (luo-testitietokanta)
        muuttuja (atom nil)
        toiminto-fn (fn [] (reset! muuttuja true))]
    (is (qk/aseta-lukko? db +testilukko3+ nil) "Lukon asettaminen onnistui")

    (Thread/sleep 1000)

    (is (lukot/aja-lukon-kanssa db +testilukko3+ toiminto-fn 1) "Lukkoa saatiin asetettua")
    (is @muuttuja "Toiminto ajettiin oikein")
    (tarkista-lukon-avaus +testilukko3+)
    (qk/avaa-lukko? db +testilukko3+)))


(deftest tarkista-poikkeusten-kasittely
  (let [db (luo-testitietokanta)
        toiminto-fn (fn [] (throw (Exception. "Poikkeus")))]
    (is (thrown? Exception (lukot/aja-lukon-kanssa db +testilukko4+ toiminto-fn 1)) "Poikkeus heitettiin ulos")
    (tarkista-lukon-avaus +testilukko4+)))

(deftest tarkista-lukon-odotus
  (let [db (luo-testitietokanta)
        muuttuja (atom [])
        toiminto-fn (fn [tunnus] (swap! muuttuja conj tunnus))
        ensimmainen (future (lukot/aja-lukon-kanssa db "odotus-testi" (fn [] (Thread/sleep 1) (toiminto-fn "A")) 1 1))
        _ (Thread/sleep 2000)
        toinen (future (lukot/aja-lukon-kanssa db "odotus-testi" (fn [] (toiminto-fn "B")) 1 1))
        odotettu-tulos ["A" "B"]]
    (is (= ["A"] @ensimmainen))
    (is (= ["A" "B"] @toinen))
    (is (= odotettu-tulos @muuttuja))))

(deftest tarkista-lukon-odotus-poikkeuksen-kanssa
  (let [db (luo-testitietokanta)]
    (is (thrown? RuntimeException (lukot/aja-lukon-kanssa db "odotus-testi" (fn [] (throw (RuntimeException. ""))) 1 1)))
    (is (nil? (first (first (q "select lukko from lukko where tunniste = 'sampo-sisaanluku'")))))))



