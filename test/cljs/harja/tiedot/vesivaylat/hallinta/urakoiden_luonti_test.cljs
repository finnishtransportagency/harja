(ns harja.tiedot.vesivaylat.hallinta.urakoiden-luonti-test
  (:require [harja.tiedot.vesivaylat.hallinta.urakoiden-luonti :as luonti]
            [harja.domain.urakka :as u]
            [harja.domain.sopimus :as s]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]))

(deftest urakan-valinta
  (let [ur {:foobar 1}]
    (is (= ur (:valittu-urakka (e! (luonti/->ValitseUrakka ur)))))))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (luonti/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (luonti/->Nakymassa? false))))))

(deftest uuden-urakan-luonnin-aloitus
  (is (= luonti/uusi-urakka (:valittu-urakka (e! (luonti/->UusiUrakka))))))

(deftest tallentamisen-aloitus
  (vaadi-async-kutsut
    #{luonti/->UrakkaTallennettu luonti/->UrakkaEiTallennettu}

    (is (true? (:tallennus-kaynnissa? (e! (luonti/->TallennaUrakka {:id 1}) {:haetut-urakat []}))))))

(deftest tallentamisen-valmistuminen
  (testing "Uuden urakan tallentaminen"
    (let [vanhat [{::u/id 1} {::u/id 2}]
          uusi {::u/id 3}
          tulos (e! (luonti/->UrakkaTallennettu uusi) {:haetut-urakat vanhat})]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-urakka tulos)))
      (is (= (conj vanhat uusi) (:haetut-urakat tulos)))))

  (testing "Urakan muokkaaminen"
    (let [vanhat [{::u/id 1 :nimi :a} {::u/id 2 :nimi :b}]
          uusi {::u/id 2 :nimi :bb}
          tulos (e! (luonti/->UrakkaTallennettu uusi) {:haetut-urakat vanhat})]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-urakka tulos)))
      (is (= [{::u/id 1 :nimi :a} {::u/id 2 :nimi :bb}] (:haetut-urakat tulos))))))

(deftest tallentamisen-epaonnistuminen
  (let [tulos (e! (luonti/->UrakkaEiTallennettu "virhe"))]
    (is (false? (:tallennus-kaynnissa? tulos)))
    (is (nil? (:valittu-urakka tulos)))))

(deftest urakan-muokkaaminen-lomakkeessa
  (let [ur {:nimi :foobar}]
    (is (= ur (:valittu-urakka (e! (luonti/->UrakkaaMuokattu ur)))))))

(deftest hakemisen-aloitus
  (vaadi-async-kutsut
    #{luonti/->UrakatHaettu luonti/->UrakatEiHaettu}
    (is (true? (:urakoiden-haku-kaynnissa? (e! (luonti/->HaeUrakat) {:id 1}))))))

(deftest hakemisen-valmistuminen
  (let [urakat [{:id 1 :nimi :a} {:id 2 :nimi :b}]
        tulos (e! (luonti/->UrakatHaettu urakat))]
    (is (false? (:urakoiden-haku-kaynnissa? tulos)))
    (is (= [{:id 1 :nimi :a} {:id 2 :nimi :b}] (:haetut-urakat tulos)))))

(deftest hakemisen-epaonnistuminen
  (let [tulos (e! (luonti/->UrakatEiHaettu "virhe"))]
    (is (false? (:urakoiden-haku-kaynnissa? tulos)))))

(deftest sopimuksen-paivittaminen
  (let [testaa (fn [tila annettu haluttu]
                 (let [uusi-tila (-> (e! (luonti/->PaivitaSopimuksetGrid annettu) {:valittu-urakka {::u/sopimukset tila}})
                                     (get-in [:valittu-urakka ::u/sopimukset]))]
                   (is (= uusi-tila haluttu))))]
    (testing "Rivin lisääminen tyhjään gridiin"
      (testaa []
              [{::s/id -1 ::s/paasopimus-id nil}]
              [{::s/id -1 ::s/paasopimus-id nil}]))

    (testing "Rivin lisääminen valmiiseen gridiin"
      ;; Gridissä on yksi pääsopimus, lisätään uusi rivi, uusi sopimus
      ;; viittaa nyt pääsopimukseen
      (testaa [{::s/id 1 ::s/paasopimus-id nil}]
              [{::s/id 1 ::s/paasopimus-id nil} {::s/id -2 ::s/paasopimus-id nil}]
              [{::s/id 1 ::s/paasopimus-id nil} {::s/id -2 ::s/paasopimus-id 1}]))

    (testing "Rivin asettaminen sopimukseksi gridiin"
      (testaa [{::s/id 1 ::s/paasopimus-id nil} {::s/id -2 ::s/paasopimus-id nil}]
              [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id nil}]
              [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id nil}]))

    ;; Pääsopimus asetetaan muualla

    (testing "Sopimuksen lisääminen gridiin, kun pääsopimus on jo asetettu"
      (testaa [{::s/id 1 ::s/paasopimus-id nil}
               {::s/id 2 ::s/paasopimus-id 1}]
              [{::s/id 1 ::s/paasopimus-id nil}
               {::s/id 2 ::s/paasopimus-id 1}
               {::s/id -3 ::s/paasopimus-id nil}]
              [{::s/id 1 ::s/paasopimus-id nil}
               {::s/id 2 ::s/paasopimus-id 1}
               {::s/id -3 ::s/paasopimus-id 1}]))

    (testing "Ainoan sopimuksen nimen vaihtaminen"
      (testaa [{::s/id 1 ::s/paasopimus-id nil}]
              [{::s/id 2 ::s/paasopimus-id nil}]
              [{::s/id 2 ::s/paasopimus-id nil}]))

    (testing "Pääsopimuksen vaihtaminen kun sopimuksia on kaksi"
      (testaa [{::s/id 1 ::s/paasopimus-id nil}
               {::s/id 2 ::s/paasopimus-id 1}]
              [{::s/id 3 ::s/paasopimus-id nil}
               {::s/id 2 ::s/paasopimus-id 1}]
              [{::s/id 3 ::s/paasopimus-id nil}
               {::s/id 2 ::s/paasopimus-id nil}]))

    (testing "Sivusopimuksen vaihtaminen kun sopimuksia on kaksi"
      (testaa [{::s/id 1 ::s/paasopimus-id nil}
               {::s/id 2 ::s/paasopimus-id 1}]
              [{::s/id 1 ::s/paasopimus-id nil}
               {::s/id 3 ::s/paasopimus-id nil}]
              [{::s/id 1 ::s/paasopimus-id nil}
               {::s/id 3 ::s/paasopimus-id 1}]))

    (testing "Rivin poistaminen gridistä"
      (testaa [{::s/id 1 ::s/paasopimus-id nil}
               {::s/id 2 ::s/paasopimus-id 1}
               {::s/id -3 ::s/paasopimus-id 1}]
              [{::s/id 1 ::s/paasopimus-id nil}
               {::s/id 2 ::s/paasopimus-id 1 :poistettu true}
               {::s/id -3 ::s/paasopimus-id nil :poistettu true}]
              [{::s/id 1 ::s/paasopimus-id nil}
               {::s/id 2 ::s/paasopimus-id 1 :poistettu true}
               {::s/id -3 ::s/paasopimus-id 1 :poistettu true}]))))


(deftest lomakevaihtoehtojen-hakemisen-aloitus
  (vaadi-async-kutsut
    #{luonti/->LomakevaihtoehdotHaettu luonti/->LomakevaihtoehdotEiHaettu}
    (is (= {:foo :bar} (e! (luonti/->HaeLomakevaihtoehdot) {:foo :bar})))))

(deftest lomakevaihtoehtojen-hakemisen-valmistuminen
  (let [hy [{:id 1}]
        ur [{:id 2}]
        h [{:id 3}]
        s [{:id 4}]
        payload {:hallintayksikot hy
                 :urakoitsijat ur
                 :hankkeet h
                 :sopimukset s}
        app (e! (luonti/->LomakevaihtoehdotHaettu payload))]
    (is (= hy (:haetut-hallintayksikot app)))
    (is (= ur (:haetut-urakoitsijat app)))
    (is (= h (:haetut-hankkeet app)))
    (is (= s (:haetut-sopimukset app)))))


(deftest lomakevaihtoehtojen-hakemisen-epaonnistuminen
  (is (= {:foo :bar} (e! (luonti/->LomakevaihtoehdotEiHaettu "virhe") {:foo :bar}))))

(deftest paasopimuksen-kasittely
  (testing "Uuden pääsopimuksen asettaminen"
    (is (= [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id 1}]
           (luonti/sopimukset-paasopimuksella [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id nil}]
                                              {::s/id 1 ::s/paasopimus-id nil})))
    (is (= [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id 1} {::s/id 3 ::s/paasopimus-id 1}]
           (luonti/sopimukset-paasopimuksella [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id nil} {::s/id 3 ::s/paasopimus-id nil}]
                                              {::s/id 1 ::s/paasopimus-id nil})))
    (is (= [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id 1} {::s/id -3 ::s/paasopimus-id 1}]
           (luonti/sopimukset-paasopimuksella [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id nil} {::s/id -3 ::s/paasopimus-id nil}]
                                              {::s/id 1 ::s/paasopimus-id nil})))
    (is (= [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id 1} {::s/id 3 ::s/paasopimus-id 1 :poistettu true}]
           (luonti/sopimukset-paasopimuksella [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id nil} {::s/id 3 ::s/paasopimus-id nil :poistettu true}]
                                              {::s/id 1 ::s/paasopimus-id nil}))))

  (testing "Pääsopimuksen muuttaminen"
    (is (= [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id 1}]
           (luonti/sopimukset-paasopimuksella [{::s/id 1 ::s/paasopimus-id 2} {::s/id 2 ::s/paasopimus-id nil}]
                                              {::s/id 1 ::s/paasopimus-id nil})))
    (is (= [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id 1} {::s/id 3 ::s/paasopimus-id 1}]
           (luonti/sopimukset-paasopimuksella [{::s/id 1 ::s/paasopimus-id 2} {::s/id 2 ::s/paasopimus-id nil} {::s/id 3 ::s/paasopimus-id 2}]
                                              {::s/id 1 ::s/paasopimus-id nil})))
    (is (= [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id 1} {::s/id -3 ::s/paasopimus-id 1}]
           (luonti/sopimukset-paasopimuksella [{::s/id 1 ::s/paasopimus-id 2} {::s/id 2 ::s/paasopimus-id nil} {::s/id -3 ::s/paasopimus-id 2}]
                                              {::s/id 1 ::s/paasopimus-id nil})))
    (is (= [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id 1} {::s/id 3 ::s/paasopimus-id 1 :poistettu true}]
           (luonti/sopimukset-paasopimuksella [{::s/id 1 ::s/paasopimus-id 2} {::s/id 2 ::s/paasopimus-id nil} {::s/id 3 ::s/paasopimus-id 2 :poistettu true}]
                                              {::s/id 1 ::s/paasopimus-id nil})))))

(deftest urakan-sopimusvaihtoehdot
  (let [urakka {::u/id 99}
        kaikki-sopimukset [{::s/id 1 ::s/urakka {::u/id 1}} {::s/id 2 ::s/urakka {::u/id 1}} {::s/id 3 ::s/urakka nil} {::s/id 4 ::s/urakka nil}]
        urakan-sopimukset [{::s/id 1 ::s/urakka {::u/id 1}} {::s/id 2 ::s/urakka {::u/id 1}} {::s/id 3 ::s/urakka nil}]]
    (is (= [{::s/id 4 ::s/urakka nil}] (luonti/vapaat-sopimukset urakka kaikki-sopimukset urakan-sopimukset)))
    (is (true? (empty? (luonti/vapaat-sopimukset urakka [{::s/id 1 ::s/urakka {::u/id 1}} {::s/id 2 ::s/urakka {::u/id 1}} {::s/id 3 ::s/urakka {::u/id 1}} {::s/id 4 ::s/urakka {::u/id 1}}] urakan-sopimukset))))
    (is (true? (empty? (luonti/vapaat-sopimukset urakka [{::s/id 1 ::s/urakka nil}] [{::s/id 1 ::s/urakka nil}])))))

  (is (true? (luonti/vapaa-sopimus? {::s/urakka nil})))
  (is (false? (luonti/vapaa-sopimus? {::s/urakka {::u/id 1}})))

  (is (true? (luonti/sopiva-sopimus-urakalle? {::u/id 1} {::s/urakka {::u/id 1}})))
  (is (true? (luonti/sopiva-sopimus-urakalle? {::u/id 1} {::s/urakka nil})))
  (is (false? (luonti/sopiva-sopimus-urakalle? {::u/id 1} {::s/urakka {::u/id 2}}))))
