(ns harja.palvelin.integraatiot.yha.kohteen-lahetyssanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma :as kohteen-lahetyssanoma])
  (:use [slingshot.slingshot :only [try+]])
  (:import (java.util Calendar)
           (java.sql Date)))

(defn tee-pvm []
  (new Date (.getTimeInMillis (Calendar/getInstance))))

(def testikohde
  {:bitumi_indeksi 0M,
   :nykyinen_paallyste 1,
   :kohdenumero nil,
   :sopimuksen_mukaiset_tyot 0M,
   :keskimaarainen_vuorokausiliikenne nil,
   :aikataulu_paallystys_alku nil,
   :aikataulu_muokkaaja nil,
   :karttapvm (tee-pvm),
   :aikataulu_kohde_valmis nil,
   :nimi "Testikohde 1",
   :kaasuindeksi 0M,
   :tr-loppuosa 41,
   :valmis_tiemerkintaan nil,
   :suorittava_tiemerkintaurakka nil,
   :lahetysaika nil,
   :aikataulu_paallystys_loppu nil,
   :tr-numero 66,
   :yllapitoluokka 3,
   :id 49,
   :sopimus 9,
   :aikataulu_muokattu nil,
   :aikataulu_tiemerkinta_alku nil,
   :poistettu false,
   :tr-loppuetaisyys 2321,
   :tr-alkuetaisyys 0,
   :arvonvahennykset 0M,
   :yhatunnus nil,
   :aikataulu_tiemerkinta_loppu nil,
   :yllapitokohdetyotyyppi "paallystys",
   :tr-alkuosa 36,
   :yhaid 251041528})

(def testialikohteet
  [{:kohdeosa-id 59,
    :yllapitokohde 49,
    :lisaaineet "lölöklö",
    :leveys 11,
    :sijainti nil,
    :massa 1,
    :sideainetyyppi 1
    :muotoarvo "ölkasdfkölasd",
    :tr-kaista 1,
    :massamenekki 11,
    :kokonaismassamaara 11,
    :esiintyma "Testikivi",
    :pitoisuus 900,
    :karttapvm (tee-pvm),
    :tunnus nil,
    :pinta-ala 11,
    :kuulamylly 2,
    :nimi "Testi a",
    :tr-loppuosa 41,
    :raekoko 11,
    :tyomenetelma 31,
    :rc% 90,
    :tr-numero 66,
    :paallystetyyppi 21,
    :id 59,
    :poistettu false,
    :tr-loppuetaisyys 0,
    :tr-alkuetaisyys 0,
    :tr-ajorata 0,
    :tr-alkuosa 36,
    :toimenpide nil,
    :km-arvo "klaösdkföa",
    :yhaid 254915666}
   {:kohdeosa-id 60,
    :yllapitokohde 49,
    :lisaaineet "asd",
    :leveys 2,
    :sijainti nil,
    :massa 22,
    :sideainetyyppi 1,
    :muotoarvo "asdf",
    :tr-kaista 1,
    :massamenekki 2,
    :kokonaismassamaara 22,
    :esiintyma "Testikivi",
    :pitoisuus 33,
    :karttapvm (tee-pvm),
    :tunnus nil,
    :pinta-ala 1,
    :kuulamylly 1,
    :nimi "Testi b",
    :tr-loppuosa 41,
    :raekoko 33,
    :tyomenetelma 21,
    :rc% 2,
    :tr-numero 66,
    :paallystetyyppi 21,
    :id 60,
    :poistettu false,
    :tr-loppuetaisyys 2321,
    :tr-alkuetaisyys 0,
    :tr-ajorata 0,
    :tr-alkuosa 41,
    :toimenpide nil,
    :km-arvo "asd",
    :yhaid 254915667}])

(def testipaallystysilmoitus
  {:tila "valmis",
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
   :kohdeosa_tunnus nil,
   :sopimuksen-mukaiset-tyot 0M,
   :perustelu-tekninen-osa nil,
   :kaasuindeksi 0M,
   :aloituspvm (tee-pvm),
   :kohdeosa_kaista 1,
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
                                    :tr-kaista 1,
                                    :paksuus 1,
                                    :verkon-sijainti 2}]},
   :kohdeosa_id 59,
   :arvonvahennykset 0M,
   :valmispvm-paallystys (tee-pvm)})

(def testikohteet
  [{:kohde testikohde,
    :alikohteet testialikohteet,
    :paallystysilmoitus testipaallystysilmoitus}])

(def testiurakka
  {:yhatunnus "YHA34434",
   :yhaid 76745,
   :yhanimi "TESTIURAKKA",
   :elyt ["KAS" "POS" "POP" "LAP"],
   :vuodet 2016,
   :harjaid 5,
   :sampoid "4242523-TES2"})

(deftest tarkista-xmln-validius
  (let [xml (kohteen-lahetyssanoma/muodosta testiurakka testikohteet)]
    (is (xml/validi-xml? "xsd/yha/" "yha.xsd" xml) "Muodostettu XML on validia")))

(deftest tarkista-kokonaishinnan-laskenta
  (is (== 0 (kohteen-lahetyssanoma/laske-hinta-kokonaishinta testipaallystysilmoitus))
      "Kokonaishinta laskettiin oikein, kun mitään laskettavaa ei ole")
  (is (== 6 (kohteen-lahetyssanoma/laske-hinta-kokonaishinta
             (assoc testipaallystysilmoitus :sopimuksen-mukaiset-tyot 1
                                            :maaramuutokset 5)))
      "Kokonaishinta laskettiin oikein, kun joukossa on nil-arvoja"))
