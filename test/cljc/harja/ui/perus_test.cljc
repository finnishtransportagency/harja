(ns harja.ui.perus-test
  "Shared-komponenttien sopimus hiccup-tasolla: id-yhteydet, roolit ja arvojen välitys."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]
            [harja.ui.perus :as ui]))

(defn- loyda
  "Palauttaa ensimmäisen hiccup-elementin, jonka tagi on `tagi` ja joka täyttää `ehto`:n."
  [hiccup tagi ehto]
  (let [tulos (atom nil)]
    (walk/postwalk
      (fn [x]
        (when (and (vector? x) (= tagi (first x)) (nil? @tulos)
                (ehto (when (map? (second x)) (second x))))
          (reset! tulos x))
        x)
      hiccup)
    @tulos))

(defn- attribuutit [elementti]
  (when (map? (second elementti)) (second elementti)))

(deftest kenttaryhma-yhdistaa-labelin-ohjeen-ja-virheen-kenttaan
  (let [saatu (atom nil)
        hiccup (ui/kenttaryhma
                 {:otsikko "Nimi" :id "nimi" :ohje "Anna nimi." :virhe "Nimi puuttuu"
                  :pakollinen? true}
                 (fn [attribuutit] (reset! saatu attribuutit) [:input]))]
    (is (= "nimi" (:for (attribuutit (loyda hiccup :label (constantly true)))))
      "label osoittaa kentän id:hen")
    (is (= {:id "nimi"
            :aria-describedby "nimi-ohje nimi-virhe"
            :aria-invalid true
            :aria-required true}
           @saatu)
      "kenttä saa id:n ja kuvaus-/virheyhteydet")
    (is (= "nimi-ohje" (:id (attribuutit (loyda hiccup :p.ui-ohje (constantly true))))))
    (is (= {:id "nimi-virhe" :role "alert"}
           (attribuutit (loyda hiccup :p.ui-virhe (constantly true)))))))

(deftest kenttaryhma-ilman-ohjetta-ja-virhetta-ei-viittaa-puuttuviin-elementteihin
  (let [saatu (atom nil)]
    (ui/kenttaryhma {:otsikko "Nimi" :id "nimi"}
      (fn [attribuutit] (reset! saatu attribuutit) [:input]))
    (is (= {:id "nimi"} @saatu))))

(deftest kenttaryhma-hyvaksyy-valmiin-hiccupin
  (is (some? (loyda (ui/kenttaryhma {:otsikko "Nimi" :id "nimi"} [:input#nimi])
               :input#nimi (constantly true)))))

(def ^:private vaihtoehdot
  [{:arvo :hoito :nimi "Hoito"}
   {:arvo :paallyste :nimi "Päällyste"}])

(deftest valintaryhma-valittaa-valitun-arvon-sellaisenaan
  (let [valittu (atom ::ei-valittu)
        hiccup (ui/valintaryhma {:otsikko "Tyyppi" :id "tyyppi" :vaihtoehdot vaihtoehdot
                                 :arvo :hoito :vaihda-fn #(reset! valittu %)})
        select (loyda hiccup :select (constantly true))]
    (testing "keyword-arvo valitaan ja palautuu keywordina"
      (is (= "0" (:value (attribuutit select))))
      ((:on-change (attribuutit select)) {:target {:value "1"}})
      (is (= :paallyste @valittu)))
    (testing "label ja select jakavat id:n"
      (is (= "tyyppi" (:for (attribuutit (loyda hiccup :label (constantly true))))))
      (is (= "tyyppi" (:id (attribuutit select)))))))

(deftest valintaryhma-tyhja-valinta-valittaa-nil
  (let [valittu (atom ::ei-valittu)
        hiccup (ui/valintaryhma {:otsikko "Tyyppi" :id "tyyppi" :vaihtoehdot vaihtoehdot
                                 :arvo nil :tyhja-valinta "Valitse..."
                                 :vaihda-fn #(reset! valittu %)})
        select (loyda hiccup :select (constantly true))]
    (is (= "" (:value (attribuutit select))))
    ((:on-change (attribuutit select)) {:target {:value ""}})
    (is (nil? @valittu))))

(deftest valintaryhma-kayttaa-samaa-virhe-ja-disabled-sopimusta-kuin-kenttaryhma
  (let [hiccup (ui/valintaryhma {:otsikko "Tyyppi" :id "tyyppi" :vaihtoehdot vaihtoehdot
                                 :arvo :hoito :vaihda-fn identity
                                 :virhe "Valitse tyyppi" :pakollinen? true :disabled? true})
        select (attribuutit (loyda hiccup :select (constantly true)))]
    (is (= "tyyppi-virhe" (:aria-describedby select)))
    (is (true? (:aria-invalid select)))
    (is (true? (:disabled select)))))

(deftest tilaviesti-renderoi-sailion-aina-ja-valitsee-roolin-tilan-mukaan
  (testing "virhe on alert, muut status"
    (is (= "alert" (:role (attribuutit (ui/tilaviesti {:tila :virhe :viesti "Virhe"})))))
    (is (= "status" (:role (attribuutit (ui/tilaviesti {:tila :lataus :viesti "Ladataan"})))))
    (is (= "status" (:role (attribuutit (ui/tilaviesti {:tila :onnistui :viesti "Valmis"}))))))
  (testing "tyhjä viesti säilyttää säiliön, jotta ruudunlukija huomaa muutoksen"
    (is (some? (ui/tilaviesti {:tila :onnistui :viesti nil}))))
  (testing "tuntematon tila hylätään"
    (is (thrown? #?(:clj AssertionError :cljs js/Error)
          (ui/tilaviesti {:tila :tuntematon :viesti "x"})))))

(deftest suodatin-antaa-kentalle-id-n-ja-ohittaa-puuttuvan-labelin
  (let [saatu (atom nil)
        otsikollinen (ui/suodatin {:otsikko "Urakka" :id "urakka"}
                       (fn [attribuutit] (reset! saatu attribuutit) [:input]))
        otsikoton (ui/suodatin {:data-cy "valinta"} [:input])]
    (is (= {:id "urakka"} @saatu))
    (is (= "urakka" (:for (attribuutit (loyda otsikollinen :label (constantly true))))))
    (is (nil? (loyda otsikoton :label (constantly true))))
    (is (= "valinta" (:data-cy (attribuutit otsikoton))))))

(deftest luokka-lisataan-juurielementille
  (is (= "ui-pino oma" (:class (attribuutit (ui/pino {:luokka "oma"})))))
  (is (= "ui-toimintoryhma" (:class (attribuutit (ui/toimintoryhma {}))))))
