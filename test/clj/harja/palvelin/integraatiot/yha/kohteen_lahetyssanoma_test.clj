(ns harja.palvelin.integraatiot.yha.urakoiden-hakuvastaussanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma :as kohteen-lahetyssanoma])
  (:use [slingshot.slingshot :only [try+]]))

(def testikohde
  {:bitumi_indeksi 0M,
   :nykyinen_paallyste 1,
   :kohdenumero nil,
   :sopimuksen_mukaiset_tyot 0M,
   :keskimaarainen_vuorokausiliikenne nil,
   :tr_kaista 1,
   :aikataulu_paallystys_alku nil,
   :aikataulu_muokkaaja nil,
   :karttapvm #inst"2016-05-25T12:24:45.000000000-00:00",
   :aikataulu_kohde_valmis nil,
   :nimi "Testikohde 1",
   :kaasuindeksi 0M,
   :tr_loppuosa 230,
   :valmis_tiemerkintaan nil,
   :suorittava_tiemerkintaurakka nil,
   :lahetysaika nil,
   :aikataulu_paallystys_loppu nil,
   :tr_numero 3,
   :yllapitoluokka 1,
   :id 39,
   :sopimus 9,
   :aikataulu_muokattu nil,
   :aikataulu_tiemerkinta_alku nil,
   :poistettu false,
   :tr_loppuetaisyys 460,
   :tr_alkuetaisyys 450,
   :arvonvahennykset 0M,
   :yhatunnus nil,
   :aikataulu_tiemerkinta_loppu nil,
   :tyyppi "paallystys",
   :tr_ajorata 0,
   :tr_alkuosa 230,
   :yhaid 251603670})

(def testialikohteet
  [{:yllapitokohde 39,
    :sijainti nil,
    :tr_kaista 1,
    :karttapvm #inst"2016-05-25T12:24:45.000000000-00:00",
    :tunnus "C",
    :nimi nil,
    :tr_loppuosa 230,
    :tr_numero 3,
    :id 45,
    :poistettu false,
    :tr_loppuetaisyys 460,
    :tr_alkuetaisyys 450,
    :tr_ajorata 0,
    :tr_alkuosa 230,
    :toimenpide nil,
    :yhaid 254915669}])

(def testipaallystysilmoitus
  {:tila "aloitettu",
   :muutoshinta 2000M,
   :kohdenimi "Leppäjärven ramppi",
   :kohdeosa_tie 18652,
   :kohdeosa_let 3312,
   :kohdenumero "L03",
   :paatos-tekninen-osa nil,
   :kohdeosa_nimi "Laivaniemi 5",
   :kohdeosa_aet 5190,
   :paatos-taloudellinen-osa nil,
   :kohdeosa_losa 1,
   :kohdeosa_ajorata 1,
   :kohdeosa_aosa 1,
   :valmispvm-kohde nil,
   :sopimuksen-mukaiset-tyot 400M,
   :perustelu-tekninen-osa nil,
   :kaasuindeksi 0M,
   :aloituspvm nil,
   :kohdeosa_kaista 1,
   :bitumi-indeksi 4543.95M,
   :id 1,
   :kasittelyaika-tekninen-osa nil,
   :takuupvm nil,
   :kohdeosa_id 5,
   :arvonvahennykset 100M,
   :valmispvm-paallystys nil,
   :perustelu-taloudellinen-osa nil,
   :kasittelyaika-taloudellinen-osa nil})

(def testikohteet
  [{:kohde testikohde,
    :alikohteet testialikohteet,
    :paallystys-ilmoitus testipaallystysilmoitus}])

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
    (is (xml/validoi "xsd/yha/" "yha.xsd" xml) "Muodostettu XML on validia")))

(deftest tarkista-kokonaishinnan-laskenta
  (is (= 7043.95M (kohteen-lahetyssanoma/laske-hinta-kokonaishinta testipaallystysilmoitus))
      "Kokonaishinta laskettiin oikein testidataa vasten")
  (is (= 6643.95M (kohteen-lahetyssanoma/laske-hinta-kokonaishinta
                    (assoc testipaallystysilmoitus :sopimuksen-mukaiset-tyot nil)))
      "Kokonaishinta laskettiin oikein, kun joukossa on nil-arvoja"))
