(ns harja.palvelin.integraatiot.yha.urakoiden-hakuvastaussanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma :as kohteen-lahetyssanoma])
  (:use [slingshot.slingshot :only [try+]]))

(def testikohde
  {:bitumi_indeksi 4543.95M,
   :nykyinen_paallyste nil,
   :yha_urakka_id 76745,
   :kohdenumero "L03",
   :sopimuksen_mukaiset_tyot 400M,
   :harja_urakka_id 5,
   :keskimaarainen_vuorokausiliikenne nil,
   :tr_kaista 1,
   :aikataulu_paallystys_alku #inst"2016-05-19T03:00:00.000000000-00:00",
   :aikataulu_muokkaaja 2,
   :karttapvm nil,
   :aikataulu_kohde_valmis nil,
   :nimi "Leppäjärven ramppi",
   :kaasuindeksi 0M,
   :tr_loppuosa 1,
   :valmis_tiemerkintaan #inst"2016-05-21T13:00:00.000000000-00:00",
   :suorittava_tiemerkintaurakka nil,
   :lahetysaika nil,
   :aikataulu_paallystys_loppu #inst"2016-05-21T13:00:00.000000000-00:00",
   :tr_numero 18652,
   :harja_urakka_nimi "Muhoksen päällystysurakka",
   :yllapitoluokka nil,
   :id 1,
   :sopimus 8,
   :aikataulu_muokattu #inst"2016-05-30T04:27:46.161231000-00:00",
   :aikataulu_tiemerkinta_alku nil,
   :poistettu false,
   :tr_loppuetaisyys 3312,
   :sampo_urakka_id "4242523-TES2",
   :tr_alkuetaisyys 5190,
   :arvonvahennykset 100M,
   :yhatunnus nil,
   :aikataulu_tiemerkinta_loppu nil,
   :tyyppi "paallystys",
   :tr_ajorata 1,
   :tr_alkuosa 1,
   :yhaid 1233534})

(def testialikohteet
  [{:yllapitokohde 1,
    :tr_kaista 1,
    :nimi "Laivaniemi 5",
    :tr_loppuosa 1,
    :tr_numero 18652,
    :id 5,
    :poistettu false,
    :tr_loppuetaisyys 3312,
    :tr_alkuetaisyys 5190,
    :tr_ajorata 1,
    :tr_alkuosa 1,
    :toimenpide nil,
    :yhaid nil}])

(def testipaallystys-ilmoitus
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
  [{:kohde testikohde
    :alikohteet testialikohteet
    :paallystys-ilmoitus testipaallystys-ilmoitus}])

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
  (is (= 7043.95M (kohteen-lahetyssanoma/laske-hinta-kokonaishinta testipaallystys-ilmoitus))
      "Kokonaishinta laskettiin oikein testidataa vasten")
  (is (= 6643.95M (kohteen-lahetyssanoma/laske-hinta-kokonaishinta
                    (assoc testipaallystys-ilmoitus :sopimuksen-mukaiset-tyot nil)))
      "Kokonaishinta laskettiin oikein, kun joukossa on nil-arvoja"))
