(ns harja.palvelin.integraatiot.sahke.sahke-test
  (:require [clojure.test :refer [deftest is use-fixtures compose-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sahke.sahke-komponentti :as sahke]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.tyokalut.xml :as xml]))

(def +lahetysjono+ "urakat-sahkeeseen")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    "yit"
    :sonja (feikki-sonja)
    :sahke (component/using
             (sahke/->Sahke +lahetysjono+ nil)
             [:db :sonja :integraatioloki])))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest tarkista-viestin-kasittely-ja-kuittaukset
  (let [viestit (atom [])
        urakan-nimi "Oulun alueurakka 2014-2019"
        urakka-id (first (q (str "SELECT id FROM urakka WHERE nimi = '" urakan-nimi "';")))]
    (sonja/kuuntele! (:sonja jarjestelma) +lahetysjono+ #(swap! viestit conj (.getText %)))
    (sahke/laheta-urakka-sahkeeseen (:sahke jarjestelma) urakka-id)
    (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Urakkaviesti lähetetty onnistuneesti." 10000)
    (is (= 1 (count @viestit)) "Urakka viestejä lähti vain 1")

    (let [xml (first @viestit)]
      (is (xml/validi-xml? "xsd/sampo/inbound/" "Sampo2Harja.xsd" xml) "Viesti on validi XSD-skeeman mukaan")
      (is (.contains xml urakan-nimi)))))

