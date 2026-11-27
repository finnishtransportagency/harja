(ns harja.ui.perus-test
  "Shared-komponenttien sopimus hiccup-tasolla: id-yhteydet, roolit ja arvojen välitys."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]
            [harja.ui.perus :as ui]))

(deftest poistettua-valintaryhmaa-ei-ole-public-apina
  (is (nil? (ns-resolve 'harja.ui.perus 'valintaryhma))))

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

(deftest vaaka-renderoi-lapset-ja-public-api-luokat
  (let [hiccup (ui/vaaka {:data-cy "toiminnot"
                           :vali :suuri
                           :rivita? true
                           :tasaus :valiin
                           :ristitasaus :loppu}
                 [:button "Tallenna"]
                 [:button "Peruuta"])]
    (is (= "ui-vaaka ui-vaaka-vali-suuri ui-vaaka-rivita ui-vaaka-tasaus-valiin ui-vaaka-ristitasaus-loppu"
          (:class (attribuutit hiccup))))
    (is (= "toiminnot" (:data-cy (attribuutit hiccup))))
    (is (= [[:button "Tallenna"] [:button "Peruuta"]]
          (subvec hiccup 2)))))

(deftest vaaka-hylkaa-tuntemattomat-asetelmat
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/vaaka {:vali :massiivinen} [:button "Tallenna"])))
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/vaaka {:rivita? :kylla} [:button "Tallenna"])))
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/vaaka {:tasaus :satunnaisesti} [:button "Tallenna"])))
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/vaaka {:ristitasaus :diagonaali} [:button "Tallenna"]))))

(deftest sailio-renderoi-tokenit-ja-ei-aseta-marginaalia
  (let [hiccup (ui/sailio {:data-cy "sivu"
                           :leveys :suuri
                           :sisareuna :pieni}
                 [:main "Sisältö"])]
    (is (= "ui-sailio ui-sailio-leveys-suuri ui-sailio-sisareuna-pieni"
          (:class (attribuutit hiccup))))
    (is (= "sivu" (:data-cy (attribuutit hiccup))))
    (is (nil? (:style (attribuutit hiccup))))
    (is (= [:main "Sisältö"] (get hiccup 2)))))

(deftest sailio-hylkaa-tuntemattomat-tokenit
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/sailio {:leveys :tayteleveys} [:main])))
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/sailio {:sisareuna :ei-vapaata-css} [:main]))))

(deftest sailio-keskittaa-vain-kun-keskita-on-tosi
  (is (= "ui-sailio ui-sailio-leveys-suuri ui-sailio-sisareuna-keski ui-sailio-keskita"
        (:class (attribuutit (ui/sailio {:keskita? true} [:main])))))
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/sailio {:keskita? :kylla} [:main]))))

(deftest osio-yhdistaa-otsikon-ja-sisallon-saavutettavasti
  (let [hiccup (ui/osio {:id "urakkakatsaus"
                          :otsikko "Urakan yhteenveto"
                          :otsikkotaso 3
                          :vali :pieni}
                 [:p "Sisältö"])]
    (is (= "urakkakatsaus-otsikko" (:aria-labelledby (attribuutit hiccup))))
    (is (= "urakkakatsaus" (:data-cy (attribuutit hiccup))))
    (is (= "ui-osio ui-osio-vali-pieni" (:class (attribuutit hiccup))))
    (is (= "urakkakatsaus-otsikko"
          (:id (attribuutit (loyda hiccup :h3 (constantly true))))))
    (is (= [:p "Sisältö"]
          (get (loyda hiccup :div
                 #(= "ui-osio-sisalto" (:class %)))
            2)))))

(deftest osio-hylkaa-puuttuvan-id-n-ja-tuntemattoman-otsikkotason
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/osio {:otsikko "Otsikko"} [:p "Sisältö"])))
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/osio {:id "osio" :otsikko "Otsikko" :otsikkotaso 7} [:p "Sisältö"]))))

(deftest ruudukko-renderoi-rajatun-palstamaaran-ja-valin
  (let [hiccup (ui/ruudukko {:data-cy "yhteenvedot"
                              :palstoja 3
                              :vali :suuri}
                 [:article "A"]
                 [:article "B"]
                 [:article "C"])]
    (is (= "ui-ruudukko ui-ruudukko-palstoja-3 ui-ruudukko-vali-suuri"
          (:class (attribuutit hiccup))))
    (is (= "yhteenvedot" (:data-cy (attribuutit hiccup))))
    (is (= 3 (count (subvec hiccup 2))))))

(deftest ruudukko-hylkaa-tuettamattoman-palstamaaran
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/ruudukko {:palstoja 0} [:article])))
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/ruudukko {:palstoja 5} [:article])))
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/ruudukko {:vali :vapaa} [:article]))))

(deftest luokka-lisataan-juurielementille
  (is (= "ui-pino ui-pino-vali-keski oma" (:class (attribuutit (ui/pino {:luokka "oma"})))))
  (is (= "ui-toimintoryhma" (:class (attribuutit (ui/toimintoryhma {}))))))

(deftest pino-valitsee-valin-nimetyn-tokenin-mukaan
  (is (= "ui-pino ui-pino-vali-pieni" (:class (attribuutit (ui/pino {:vali :pieni})))))
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/pino {:vali :vapaa}))))

(deftest tietolista-renderoi-semanttiset-rivit-ja-sailyttaa-hiccup-arvon
  (let [hiccup (ui/tietolista {:data-cy "urakan-tiedot"
                                :otsikkoleveys :suuri
                                :tiivis? true
                                :viivat? false
                                :rivit [{:otsikko "Urakka" :arvo "Esimerkkiurakka"}
                                        {:otsikko "Puuttuva" :arvo nil}
                                        {:otsikko "Tila" :arvo [:a {:href "#tila"} "Käsittelyssä"]}]})
        juuri (attribuutit hiccup)]
    (is (= :dl (first hiccup)))
    (is (= "urakan-tiedot" (:data-cy juuri)))
    (is (= "ui-tietolista ui-tietolista-otsikkoleveys-suuri ui-tietolista-tiivis ui-tietolista-ilman-viivoja"
          (:class juuri)))
    (is (= ["Urakka" "Tila"]
           (->> hiccup
                (filter vector?)
                (filter #(= :dt (first %)))
                (map #(nth % 2)))))
    (is (some? (loyda hiccup :a #(= "#tila" (:href %)))))
    (is (empty? (filter #(and (vector? %)
                              (= "Puuttuva" (nth % 2 nil)))
                   hiccup)))))

(deftest tietolista-hylkaa-tuntemattoman-otsikkoleveyden
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/tietolista {:otsikkoleveys :liian-levea :rivit []}))))

(deftest tietolista-hylkaa-puutteen-ja-virheellisen-rivikartan
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/tietolista {})))
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/tietolista {:rivit [{:otsikko nil :arvo "Arvo"}]})))
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
        (ui/tietolista {:rivit [{:arvo "Arvo"}]}))))

(deftest kortti-yhdistaa-otsikon-ja-sisallot-kokonaisuudeksi
  (let [hiccup (ui/kortti {:id "urakkatiedot"
                            :otsikko "Urakkatiedot"
                            :otsikkotaso 2
                            :kuvaus "Keskeiset tiedot valitusta urakasta."
                            :toiminto [:button "Muokkaa"]
                            :alatunniste [:button "Sulje"]
                            :koko :pieni
                            :luokka "oma-kortti"}
                   [:p "Sisältö"])]
    (is (= :article (first hiccup)))
    (is (= "urakkatiedot"
          (:id (attribuutit hiccup))))
    (is (= "urakkatiedot"
          (:data-cy (attribuutit hiccup))))
    (is (= "urakkatiedot-otsikko"
          (:aria-labelledby (attribuutit hiccup))))
    (is (= "ui-kortti ui-kortti-pieni oma-kortti"
          (:class (attribuutit hiccup))))
    (is (= "Urakkatiedot"
          (nth (loyda hiccup :h2 (constantly true)) 2)))
    (is (some? (loyda hiccup :header #(= "ui-kortti-otsake" (:class %)))))
    (is (some? (loyda hiccup :div #(= "ui-kortti-sisalto" (:class %)))))
    (is (some? (loyda hiccup :footer #(= "ui-kortti-alatunniste" (:class %)))))))
