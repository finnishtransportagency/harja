(ns harja.tyokalut.lukot-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [harja.testi :refer :all]
    [harja.palvelin.tyokalut.lukot :as lukot]
    [harja.kyselyt.lukot :as qk]
    [harja.palvelin.komponentit.tietokanta :as tietokanta]))

(def +testilukko+ "TESTI")

(defn tarkista-lukon-avaus []
  (let [lukko (first (q (str "SELECT tunniste, lukko FROM lukko WHERE tunniste = '" +testilukko+ "'")))]
    (is (= +testilukko+ (first lukko)))
    (is (nil? (second lukko)) "Lukko on avattu ajon jälkeen")))

(deftest tarkista-lukon-kanssa-ajaminen
  (let [db (apply tietokanta/luo-tietokanta testitietokanta)
        muuttuja (atom nil)
        toiminto-fn (fn [] (reset! muuttuja true))]
    (is (lukot/aja-lukon-kanssa db +testilukko+ toiminto-fn) "Lukko saatiin asetettua oikein")
    (is @muuttuja "Toiminto ajettiin oikein")
    (tarkista-lukon-avaus)))

(deftest tarkista-lukittu-ajo
  (let [db (apply tietokanta/luo-tietokanta testitietokanta)
        muuttuja (atom nil)
        toiminto-fn (fn [] (reset! muuttuja true))]
    (is (qk/aseta-lukko? db +testilukko+ nil) "Lukon asettaminen onnistui")
    (is (false? (lukot/aja-lukon-kanssa db +testilukko+ toiminto-fn)) "Lukkoa ei saatu asetettua")
    (is (nil? @muuttuja) "Toimintoa ei ajettu")
    (is (qk/avaa-lukko? db +testilukko+) "Lukon avaaminen onnistui")))

(deftest tarkista-aikapohjaisen-lukon-ajo
  (let [db (apply tietokanta/luo-tietokanta testitietokanta)
        muuttuja (atom nil)
        toiminto-fn (fn [] (reset! muuttuja true))]
    (is (qk/aseta-lukko? db +testilukko+ nil) "Lukon asettaminen onnistui")

    (Thread/sleep 1000)

    (is (lukot/aja-lukon-kanssa db +testilukko+ toiminto-fn 1) "Lukkoa saatiin asetettua")
    (is @muuttuja "Toiminto ajettiin oikein")
    (tarkista-lukon-avaus)))

