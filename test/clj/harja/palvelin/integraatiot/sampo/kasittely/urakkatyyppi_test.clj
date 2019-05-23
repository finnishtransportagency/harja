(ns harja.palvelin.integraatiot.sampo.kasittely.urakkatyyppi-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.kasittely.urakkatyyppi :as urakkatyyppi]))

(deftest liikennemuodo-paattely
  (is (= "r" (urakkatyyppi/vaylamuoto "R")) "Rautatie liikennemuoto päätellään oikein.")
  (is (= "v" (urakkatyyppi/vaylamuoto "V")) "Vesiväylä liikennemuoto päätellään oikein.")
  (is (= "t" (urakkatyyppi/vaylamuoto "T")) "Tie liikennemuoto päätellään oikein.")
  (is (= "t" (urakkatyyppi/vaylamuoto nil)) "Tyhjä arvo päätellään oletuksena tie liikennemuodoksi.")
  (is (= "t" (urakkatyyppi/vaylamuoto "123")) "Ei-validi arvo päätellään oletuksena tie liikennemuodoksi."))

(deftest urakkatyypin-paattely-tievaylaurakoille
  (is (= "paallystys" (urakkatyyppi/urakkatyyppi "TYP")) "Päällystys urakkatyyppi päätellään oikein.")
  (is (= "valaistus" (urakkatyyppi/urakkatyyppi "TYV")) "Valaistus urakkatyyppi päätellään oikein.")
  (is (= "paallystys" (urakkatyyppi/urakkatyyppi "TY")) "Ilman alityyppiä tuotu ylläpidon urakka merkitään päällystykseksi.")
  (is (= "siltakorjaus" (urakkatyyppi/urakkatyyppi "TYS")) "Siltakorjaus urakkatyyppi päätellään oikein.")
  (is (= "tiemerkinta" (urakkatyyppi/urakkatyyppi "TYT")) "Tiemerkintä urakkatyyppi päätellään oikein.")
  (is (= "paallystys" (urakkatyyppi/urakkatyyppi "THP")) "Päällystys urakkatyyppi päätellään oikein.")
  (is (= "valaistus" (urakkatyyppi/urakkatyyppi "THV")) "Valaistus urakkatyyppi päätellään oikein.")
  (is (= "siltakorjaus" (urakkatyyppi/urakkatyyppi "THS")) "Siltakorjaus urakkatyyppi päätellään oikein.")
  (is (= "tiemerkinta" (urakkatyyppi/urakkatyyppi "THT")) "Tiemerkintä urakkatyyppi päätellään oikein.")
  (is (= "tekniset-laitteet" (urakkatyyppi/urakkatyyppi "THL")) "Tekniset laittet urakkatyyppi päätellään oikein.")
  (is (= "tekniset-laitteet" (urakkatyyppi/urakkatyyppi "TYL")) "Tekniset laittet urakkatyyppi päätellään oikein.")
  (is (= "teiden-hoito" (urakkatyyppi/urakkatyyppi "THJ")) "Maanteiden hoidon urakkatyyppi päätellään oikein."))


(deftest urakkatyypin-paattely-vesivaylaurakoille
  (is (= "vesivayla-hoito" (urakkatyyppi/urakkatyyppi "VHH")) "Vesiväylän hoitourakka päätellään oikein")
  (is (= "vesivayla-hoito" (urakkatyyppi/urakkatyyppi "VH")) "Vesiväylän hoitourakka päätellään oikein")
  (is (= "vesivayla-kanavien-hoito" (urakkatyyppi/urakkatyyppi "VHK")) "Vesiväylän kanavanhoitourakka päätellään oikein")
  (is (= "vesivayla-ruoppaus" (urakkatyyppi/urakkatyyppi "VYR")) "Vesiväylän ruoppausurakka päätellään oikein")
  (is (= "vesivayla-turvalaitteiden-korjaus" (urakkatyyppi/urakkatyyppi "VYT")) "Vesiväylän turvalaite päätellään oikein")
  (is (= "vesivayla-kanavien-korjaus" (urakkatyyppi/urakkatyyppi "VYK")) "Vesiväylän kanavan korjausurakka päätellään oikein"))

(deftest urakkatyyppien-erikoistapaukset
  (is (= "hoito" (urakkatyyppi/urakkatyyppi "TH")) "Hoito urakkatyyppi päätellään oikein.")
  (is (= "paallystys" (urakkatyyppi/urakkatyyppi "typ")) "Päättely toimii pienillä kirjaimilla.")
  (is (= "hoito" (urakkatyyppi/urakkatyyppi "TH123")) "Hoito urakkatyyppi päätellään oikein.")
  (is (= "hoito" (urakkatyyppi/urakkatyyppi "")) "Tyhjä arvo päätellään oletuksena hoito urakkatyypiksi.")
  (is (= "hoito" (urakkatyyppi/urakkatyyppi nil)) "Tyhjä arvo päätellään oletuksena hoito urakkatyypiksi.")
  (is (= "hoito" (urakkatyyppi/urakkatyyppi "123")) "Ei-validi arvo päätellään oletuksena hoito urakkatyypiksi.")
  (is (= "hoito" (urakkatyyppi/urakkatyyppi "")) "Liian lyhyt arvo päätellään hoito urakkatyypiksi.")
  (is (= "hoito" (urakkatyyppi/urakkatyyppi "1")) "Liian lyhyt arvo päätellään hoito urakkatyypiksi.")
  (is (= "hoito" (urakkatyyppi/urakkatyyppi nil)) "Nil arvo päätellään hoito urakkatyypiksi."))

(deftest sampon-urakkatyypin-rakentaminen
  (is (= "TH" (urakkatyyppi/rakenna-sampon-tyyppi "hoito")))
  (is (= "TYP" (urakkatyyppi/rakenna-sampon-tyyppi "paallystys")))
  (is (= "TYT" (urakkatyyppi/rakenna-sampon-tyyppi "tiemerkinta")))
  (is (= "TYV" (urakkatyyppi/rakenna-sampon-tyyppi "valaistus")))
  (is (= "TYS" (urakkatyyppi/rakenna-sampon-tyyppi "siltakorjaus")))
  (is (= "TYL" (urakkatyyppi/rakenna-sampon-tyyppi "tekniset-laitteet")))
  (is (thrown-with-msg? RuntimeException #"Tuntematon urakkatyyppi: tuntematon"
                        (urakkatyyppi/rakenna-sampon-tyyppi "tuntematon"))))
