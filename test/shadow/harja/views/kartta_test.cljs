(ns harja.views.kartta-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [harja.views.kartta :as kartta]))

(deftest klikkauksesta-seuraavat-tapahtumat
  (let [ilmoitus {:id 1}
        hallintayksikko {:type :hy :id 5}
        urakka {:type :ur :id 3}
        asia {:id 4}]
    (testing "Ilmoituksen klikkaaminen valitsee yhden ilmoituksen"
      (is (= {:keskeyta-event? true
              :valitse-ilmoitus ilmoitus}
             (kartta/klikkauksesta-seuraavat-tapahtumat
               [urakka ilmoitus]
               false
               :ilmoitukset
               3
               2))))

    (testing "Usean ilmoituksen tuplaklikkaaminen avaa paneelin ja keskittaa"
      (let [ilmoitukset [{:id 1} {:id 2}]]
        (is (= {:keskeyta-event? true
                :avaa-paneeli? true
                :nayta-nama-paneelissa ilmoitukset
                :keskita-naihin ilmoitukset}
               (kartta/klikkauksesta-seuraavat-tapahtumat
                 (into [urakka] ilmoitukset)
                 true
                 :ilmoitukset
                 3
                 2)))))

    (testing "Tilannekuvan tyhja klikkaus avaa paneelin vain ilman tuplaklikkausta"
      (is (= {:keskeyta-event? true
              :avaa-paneeli? true}
             (kartta/klikkauksesta-seuraavat-tapahtumat
               []
               false
               :tilannekuva
               3
               2)))
      (is (nil? (kartta/klikkauksesta-seuraavat-tapahtumat
                  []
                  true
                  :tilannekuva
                  3
                  2))))

    (testing "Raportissa organisaation klikkaaminen valitsee sen"
      (is (= {:keskeyta-event? true
              :valitse-hallintayksikko hallintayksikko}
             (kartta/klikkauksesta-seuraavat-tapahtumat
               [hallintayksikko]
               false
               :raportit
               3
               2)))
      (is (= {:keskeyta-event? true
              :valitse-urakka urakka
              :keskita-naihin [urakka]}
             (kartta/klikkauksesta-seuraavat-tapahtumat
               [urakka]
               true
               :raportit
               1
               2))))

    (testing "Valitun urakan asian klikkaaminen avaa infopaneelin"
      (is (= {:keskeyta-event? true
              :avaa-paneeli? true
              :nayta-nama-paneelissa [asia]
              :keskita-naihin [asia]}
             (kartta/klikkauksesta-seuraavat-tapahtumat
               [urakka asia]
               true
               :toteumat
               3
               2))))))
