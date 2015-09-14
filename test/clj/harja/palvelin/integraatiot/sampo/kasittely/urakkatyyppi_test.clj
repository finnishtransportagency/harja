(ns harja.palvelin.integraatiot.sampo.kasittely.urakkatyyppi-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.kasittely.urakkatyyppi :as urakkatyyppi]))

(deftest tarkista-liikennemuodo-paattely
  (is (= "r" (urakkatyyppi/paattele-liikennemuoto "R")) "Rautatie liikennemuoto päätellään oikein.")
  (is (= "v" (urakkatyyppi/paattele-liikennemuoto "V")) "Vesiväylä liikennemuoto päätellään oikein.")
  (is (= "t" (urakkatyyppi/paattele-liikennemuoto "T")) "Tie liikennemuoto päätellään oikein.")
  (is (= "t" (urakkatyyppi/paattele-liikennemuoto nil)) "Tyhjä arvo päätellään oletuksena tie liikennemuodoksi.")
  (is (= "t" (urakkatyyppi/paattele-liikennemuoto "123")) "Ei-validi arvo päätellään oletuksena tie liikennemuodoksi."))

(deftest tarkista-urakkatyypin-paattely
  (is (= "paallystys" (urakkatyyppi/paattele-urakkatyyppi "TYP")) "Päällystys urakkatyyppi päätellään oikein.")
  (is (= "valaistus" (urakkatyyppi/paattele-urakkatyyppi "TYV")) "Valaistus urakkatyyppi päätellään oikein.")
  (is (= "siltakorjaus" (urakkatyyppi/paattele-urakkatyyppi "TYS")) "Siltakorjaus urakkatyyppi päätellään oikein.")
  (is (= "tiemerkinta" (urakkatyyppi/paattele-urakkatyyppi "TYT")) "Tiemerkintä urakkatyyppi päätellään oikein.")
  (is (= "hoito" (urakkatyyppi/paattele-urakkatyyppi "TH")) "Hoito urakkatyyppi päätellään oikein.")
  (is (= "hoito" (urakkatyyppi/paattele-urakkatyyppi "TH123")) "Hoito urakkatyyppi päätellään oikein.")
  (is (= "hoito" (urakkatyyppi/paattele-urakkatyyppi "")) "Tyhjä arvo päätellään oletuksena hoito urakkatyypiksi.")
  (is (= "hoito" (urakkatyyppi/paattele-urakkatyyppi nil)) "Tyhjä arvo päätellään oletuksena hoito urakkatyypiksi.")
  (is (= "hoito" (urakkatyyppi/paattele-urakkatyyppi "123")) "Ei-validi arvo päätellään oletuksena hoito urakkatyypiksi.")
  (is (= "hoito" (urakkatyyppi/paattele-urakkatyyppi "")) "Liian lyhyt arvo päätellään hoito urakkatyypiksi.")
  (is (= "hoito" (urakkatyyppi/paattele-urakkatyyppi "1")) "Liian lyhyt arvo päätellään hoito urakkatyypiksi.")
  (is (= "hoito" (urakkatyyppi/paattele-urakkatyyppi nil)) "Nil arvo päätellään hoito urakkatyypiksi."))