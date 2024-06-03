(ns harja.palvelin.integraatiot.yha.kohteen-lahetyssanoma-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma :as kohteen-lahetyssanoma]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [try+]])
  (:import (java.util Calendar)
           (java.sql Date)))

(defn tee-pvm []
  (new Date (.getTimeInMillis (Calendar/getInstance))))

(defn tee-pvm-tulos []
  (xml/formatoi-paivamaara (pvm/nyt)))

(def testikohde
  {:yhaid 251041528
   :id 654321
   :paikkauskohde-id 99
   :yha-kohdenumero 323
   :kohdetyyppi "paallyste" ; 1
   :yllapitokohdetyotyyppi "paallystys"
   :nimi "Testikohde 1"
   :tunnus "pa"
   :toiden-aloituspaivamaara (tee-pvm)
   :paallystyksen-valmistumispaivamaara (tee-pvm)
   :kohteen-valmistumispaivamaara (tee-pvm)
   :takuupaivamaara (tee-pvm) 
   :tr-alkuetaisyys 3
   :tr-ajorata 1
   :tr-alkuosa 101
   :tr-kaista 11
   :karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
   :tr-loppuetaisyys 30
   :tr-loppuosa 101
   :tr-numero 4})

(def kohde-tulos
  [[:yha-id 99]
   [:harja-id 654321]
   [:kohdenumero 323]
   [:kohdetyyppi 1]
   [:kohdetyotyyppi "paallystys"]
   [:nimi "Testikohde 1"]
   [:tunnus "pa"]
   [:toiden-aloituspaivamaara (tee-pvm-tulos)]
   [:paallystyksen-valmistumispaivamaara (tee-pvm-tulos)]
   [:kohteen-valmistumispaivamaara (tee-pvm-tulos)]
   [:takuupaivamaara (tee-pvm-tulos)]
   [:toteutunuthinta 4000.4M]
   [:tierekisteriosoitevali
    [:karttapaivamaara "2016-01-01"]
    [:tienumero 4]
    [:aosa 101]
    [:aet 3]
    [:losa 101]
    [:let 30]
    [:ajorata 1]
    [:kaista 11]]])

(def testi-kulutuskerrokselle-tehdyt-toimet
  {:yha-id 123456
   :harja-id 654321
   :poistettu false
   :tr-alkuetaisyys 3
   :tr-ajorata 1
   :tr-alkuosa 101
   :tr-kaista 11
   :tr-loppuetaisyys 30
   :tr-loppuosa 101
   :tr-numero 4
   :leveys 3.5M
   :pinta-ala 50.0M
   :paallystetyomenetelma 12
   :massamenekki 150.0M
   :kokonaismassamaara 20.0M
   :massa [[:massatyyppi 1]
           [:max-raekoko 11]
           [:kuulamyllyluokka 2] ;; AN7
           [:yhteenlaskettu-kuulamyllyarvo 15.5M]
           [:yhteenlaskettu-litteysluku 10.2M]
           [:litteyslukuluokka "F15"]
           [:runkoaineet
            [:runkoaine
             [:runkoainetyyppi 1]
             [:kuulamyllyarvo 5.5M]
             [:litteysluku 5.2M]
             [:massaprosentti 14.5M]
             [:fillerityyppi "Kalkkifilleri (KF)"]
             [:kuvaus "testikuvaus"]]
            [:runkoaine  [:runkoainetyyppi 2]
             [:kuulamyllyarvo 5.0M]
             [:litteysluku 5.0M]
             [:massaprosentti 15.5M]
             [:fillerityyppi "Kalkkifilleri (KF)"]
             [:kuvaus "testikuvaus"]]]
           [:sideaineet
            [:sideaine
             [:tyyppi 3]
             [:pitoisuus 4.5M]]
            [:sideaine
             [:tyyppi 4]
             [:pitoisuus 2.5M]]]
           [:lisaaineet
            [:lisaaine
             [:tyyppi 2]
             [:pitoisuus 25.5M]]]]})

(def kulutuskerrokselle-tehdyt-toimet-tulos
  [:kulutuskerrokselle-tehty-toimenpide
   [:yha-id 123456]
   [:harja-id 654321]
   [:poistettu false]
   [:tierekisteriosoitevali
    [:karttapaivamaara "2016-01-01"]
    [:tienumero 4]
    [:aosa 101]
    [:aet 3]
    [:losa 101]
    [:let 30]
    [:ajorata 1]
    [:kaista 11]]
   [:leveys 3.5M]
   [:pinta-ala 50.0M] 
   [:paallystetyomenetelma 12]
   [:massamenekki 150.0M]
   [:kokonaismassamaara 20.0M] 
   [:massa
    [:massatyyppi 1]
    [:max-raekoko 11]
    [:kuulamyllyluokka 2] ;; AN7
    [:yhteenlaskettu-kuulamyllyarvo 15.5M]
    [:yhteenlaskettu-litteysluku 10.2M]
    [:litteyslukuluokka "F15"]
    [:runkoaineet
     [:runkoaine
      [:runkoainetyyppi 1]
      [:kuulamyllyarvo 5.5M]
      [:litteysluku 5.2M]
      [:massaprosentti 14.5M]
      [:fillerityyppi "Kalkkifilleri (KF)"]
      [:kuvaus "testikuvaus"]]
     [:runkoaine  [:runkoainetyyppi 2]
      [:kuulamyllyarvo 5.0M]
      [:litteysluku 5.0M]
      [:massaprosentti 15.5M]
      [:fillerityyppi "Kalkkifilleri (KF)"]
      [:kuvaus "testikuvaus"]]]
    [:sideaineet
     [:sideaine
      [:tyyppi 3]
      [:pitoisuus 4.5M]]
     [:sideaine
      [:tyyppi 4]
      [:pitoisuus 2.5M]]]
    [:lisaaineet
     [:lisaaine
      [:tyyppi 2]
      [:pitoisuus 25.5M]]]]])


(def testi-alustalle-tehdyt-toimet
  {:harja-id 1234567890
   :tr-alkuetaisyys 3
   :tr-ajorata 1
   :tr-alkuosa 101
   :tr-kaista 11
   :tr-loppuetaisyys 30
   :tr-loppuosa 101
   :tr-numero 4
   :kasittelymenetelma 2 ; AB
   :lisatty-paksuus 15
   :kasittelysyvyys 10
   :verkkotyyppi 2 ; Lasikuituverkko
   :verkon-tarkoitus 2 ; Muiden routavaurioiden ehkäisy
   :verkon-sijainti 2 ; Kantavan kerroksen yläpinnassa
   :toimenpide 1 ; rakentaminen
   :massamenekki 150.5M
   :kokonaismassamaara 25.5M
   :massa {:massatyyppi 1
           :max-raekoko 11
           :kuulamyllyluokka 2 ;; AN7
           :yhteenlaskettu-kuulamyllyarvo 15.5M
           :yhteenlaskettu-litteysluku 10.2M
           :litteyslukuluokka "F15"
           :runkoaineet
            '({:runkoainetyyppi 1
             :kuulamyllyarvo 5.5M
             :litteysluku 5.2M
             :massaprosentti 14.5M
             :fillerityyppi "Kalkkifilleri (KF)"
             :kuvaus "testikuvaus"}
             {:runkoainetyyppi 2
             :kuulamyllyarvo 5.0M
             :litteysluku 5.0M
             :massaprosentti 15.5M
             :fillerityyppi "Kalkkifilleri (KF)"
             :kuvaus "testikuvaus"})
           :sideaineet 
            '({:tyyppi 3
             :pitoisuus 4.5M}
             {:tyyppi 4
             :pitoisuus 2.5M})
           :lisaaineet
            '({:tyyppi 2
             :pitoisuus 25.5M})} 
      :murske {:mursketyyppi 2
               :rakeisuus "string"
               :iskunkestavyys "LA30"}})

(def alustalle-tehdyt-toimet-tulos
  [:alustalle-tehty-toimenpide
   [:harja-id 1234567890]
   [:tierekisteriosoitevali
    [:karttapaivamaara "2016-01-01"]
    [:tienumero 4]
    [:aosa 101]
    [:aet 3]
    [:losa 101]
    [:let 30]
    [:ajorata 1]
    [:kaista 11]]
   [:kasittelymenetelma 2]
   [:lisatty-paksuus 15]
   [:kasittelysyvyys 10]
   [:verkkotyyppi 2]
   [:verkon-tarkoitus 2]
   [:verkon-sijainti 2]
   [:massamenekki 150.5M]
   [:kokonaismassamaara 25.5M]
   [:massa
    [:massatyyppi 1]
    [:max-raekoko 11]
    [:kuulamyllyluokka 2] ;; AN7
    [:yhteenlaskettu-kuulamyllyarvo 15.5M]
    [:yhteenlaskettu-litteysluku 10.2M]
    [:litteyslukuluokka "F15"]
    [:runkoaineet
     [:runkoaine
      [:runkoainetyyppi 1]
      [:kuulamyllyarvo 5.5M]
      [:litteysluku 5.2M]
      [:massaprosentti 14.5M]
      [:fillerityyppi "Kalkkifilleri (KF)"]
      [:kuvaus "testikuvaus"]]
     [:runkoaine  
      [:runkoainetyyppi 2]
      [:kuulamyllyarvo 5.0M]
      [:litteysluku 5.0M]
      [:massaprosentti 15.5M]
      [:fillerityyppi "Kalkkifilleri (KF)"]
      [:kuvaus "testikuvaus"]]]
    [:sideaineet
     [:sideaine
      [:tyyppi 3]
      [:pitoisuus 4.5M]]
     [:sideaine
      [:tyyppi 4]
      [:pitoisuus 2.5M]]]
    [:lisaaineet
     [:lisaaine
      [:tyyppi 2]
      [:pitoisuus 25.5M]]]]
   [:murske
    [:mursketyyppi 2]
    [:rakeisuus "string"]
    [:iskunkestavyys "LA30"]]])


(def testipaallystysilmoitus
  {:tila "valmis",
   :vuodet 2022
   :kohdenimi "Testikohde 1",
   :kohdeosa_tie 66,
   :kohdeosa_let 0,
   :kohdenumero nil,
   :paatos-tekninen-osa nil,
   :kohdeosa_nimi "Testi a",
   :kohdeosa_aet 0,
   :kohdeosa_losa 41,
   :kohdeosa_ajorata 0,
   :kohdeosa_aosa 36,
   :valmispvm-kohde (tee-pvm),
   :paikkauskohde-toteutunut-hinta 4000.4M
   :kohdeosa_tunnus nil,
   :sopimuksen-mukaiset-tyot 0M,
   :perustelu-tekninen-osa nil,
   :kaasuindeksi 0M,
   :aloituspvm (tee-pvm),
   :kohdeosa_kaista 11,
   :bitumi-indeksi 0M,
   :id 31,
   :kasittelyaika-tekninen-osa nil,
   :takuupvm (tee-pvm),
   :ilmoitustiedot {:osoitteet [{:kohdeosa-id 59,
                                 :lisaaineet "lölöklö",
                                 :leveys 11,
                                 :massa 1,
                                 :sideainetyyppi "1"
                                 :muotoarvo "ölkasdfkölasd",
                                 :massamenekki 11,
                                 :esiintyma "Testikivi",
                                 :pitoisuus 900,
                                 :tunnus "A",
                                 :pinta-ala 11,
                                 :kuulamylly 2,
                                 :raekoko 11,
                                 :tyomenetelma 31,
                                 :rc% 90,
                                 :paallystetyyppi 21,
                                 :km-arvo "klaösdkföa"}
                                {:kohdeosa-id 60,
                                 :lisaaineet "asd",
                                 :leveys 2,
                                 :massa 22,
                                 :sideainetyyppi 1,
                                 :muotoarvo "asdf",
                                 :massamenekki 2,
                                 :esiintyma "Testikivi",
                                 :pitoisuus 33,
                                 :tunnus "B",
                                 :pinta-ala 1,
                                 :kuulamylly 1,
                                 :raekoko 33,
                                 :tyomenetelma 21,
                                 :rc% 2,
                                 :paallystetyyppi 21,
                                 :km-arvo "asd"}],
                    :alustatoimet [{:verkkotyyppi 1,
                                    :tr-alkuosa 1,
                                    :tr-loppuetaisyys 1,
                                    :verkon-tarkoitus 1,
                                    :kasittelymenetelma 11,
                                    :tr-loppuosa 1,
                                    :tr-alkuetaisyys 1,
                                    :tekninen-toimenpide 1,
                                    :tr-ajorata 0,
                                    :tr-kaista 11,
                                    :paksuus 1,
                                    :verkon-sijainti 2}]},
   :kohdeosa_id 59,
   :arvonvahennykset 0M,
   :valmispvm-paallystys (tee-pvm)})


(def testikohteet
  [{:kohde testikohde 
    :alustalle-tehdyt-toimet [testi-alustalle-tehdyt-toimet]
    :kulutuskerrokselle-tehdyt-toimet [testi-kulutuskerrokselle-tehdyt-toimet],
    :paallystysilmoitus testipaallystysilmoitus}])

(def testikohteet-tulos
  [:urakan-kohteiden-toteumatietojen-kirjaus
   {:xmlns "http://www.vayla.fi/xsd/yha"}
   [:urakka
    [:yha-id 76745]
    [:harja-id 5]
    [:sampotunnus "4242523-TES2"]
    [:tunnus "YHA34434"]
    [:kohteet
     (into [:kohde] (concat kohde-tulos
                      [[:alustalle-tehdyt-toimet alustalle-tehdyt-toimet-tulos]
                       [:kulutuskerrokselle-tehdyt-toimet kulutuskerrokselle-tehdyt-toimet-tulos]]))]]])


(def testiurakka
  {:yhatunnus "YHA34434",
   :yhaid 76745,
   :yhanimi "TESTIURAKKA",
   :elyt ["KAS" "POS" "POP" "LAP"],
   :vuodet 2016,
   :harjaid 5,
   :sampoid "4242523-TES2"})

(def kohteen-tienumero "456")
(def karttapvm #inst "2015-12-31T22:00:00.000-00:00")

(def testi-runkoaineet
  '({:fillerityyppi "Kalkkifilleri (KF)",
     :kuulamyllyarvo 5.5M,
     :kuvaus "testikuvaus",
     :litteysluku 5.2M,
     :massaprosentti 14.5M,
     :runkoainetyyppi 1}
    {:fillerityyppi "Kalkkifilleri (KF)",
     :kuulamyllyarvo 5.0M,
     :kuvaus "testikuvaus",
     :litteysluku 5.0M,
     :massaprosentti 1.0M,
     :runkoainetyyppi 2}))

(def avainten-jarjestys 
  [:runkoainetyyppi 
   :kuulamyllyarvo
   :litteysluku
   :massaprosentti
   :fillerityyppi
   :kuvaus])

(def runkoaineet-tulos
  [[:runkoaine
    [:runkoainetyyppi 1]
    [:kuulamyllyarvo 5.5M]
    [:litteysluku 5.2M]
    [:massaprosentti 14.5M]
    [:fillerityyppi "Kalkkifilleri (KF)"]
    [:kuvaus "testikuvaus"]]
   [:runkoaine  
    [:runkoainetyyppi 2]
    [:kuulamyllyarvo 5.0M]
    [:litteysluku 5.0M]
    [:massaprosentti 1.0M]
    [:fillerityyppi "Kalkkifilleri (KF)"]
    [:kuvaus "testikuvaus"]]])

(deftest muunna-collection-mappeja-vektoreiksi
  (testing "muunna-collection-mappeja-vektoreiksi"
     (let [tulos (kohteen-lahetyssanoma/muunna-collection-mappeja-vektoreiksi testi-runkoaineet :runkoaine avainten-jarjestys)]
       (is (= runkoaineet-tulos tulos)))))

(deftest kulutuskerrokselle-tehdyt-toimet
  (testing "muodosta kulutuskerrokselle-tehty-toimenpide"
     (let [tulos (kohteen-lahetyssanoma/tee-kulutuskerrokselle-tehdyt-toimet testi-kulutuskerrokselle-tehdyt-toimet kohteen-tienumero karttapvm)]
       (is (= kulutuskerrokselle-tehdyt-toimet-tulos tulos)))))

(deftest tee-alustalle-tehty-toimenpide
  (testing "muodosta alustalle-tehty-toimenpide"
    (let [tulos (kohteen-lahetyssanoma/tee-alustalle-tehty-toimenpide testi-alustalle-tehdyt-toimet kohteen-tienumero karttapvm)]
      (is (= alustalle-tehdyt-toimet-tulos tulos)))))

(deftest muodosta-sanoma-test
  (testing "muodosta-sanoma funktio"
    (let [tulos (kohteen-lahetyssanoma/muodosta-sanoma testiurakka testikohteet)]
      (is (= testikohteet-tulos tulos)))))

(deftest tarkista-xmln-validius
  (let [xml (kohteen-lahetyssanoma/muodosta testiurakka testikohteet)]
    (is (xml/validi-xml? "xsd/yha/" "yha2.xsd" xml) "Muodostettu XML on validia")))

(deftest tarkista-kokonaishinnan-laskenta
  (is (== 0 (kohteen-lahetyssanoma/laske-hinta-kokonaishinta testipaallystysilmoitus))
      "Kokonaishinta laskettiin oikein, kun mitään laskettavaa ei ole")
  (is (== 6 (kohteen-lahetyssanoma/laske-hinta-kokonaishinta
             (assoc testipaallystysilmoitus :sopimuksen-mukaiset-tyot 1
                                            :maaramuutokset 5)))
      "Kokonaishinta laskettiin oikein, kun joukossa on nil-arvoja"))
