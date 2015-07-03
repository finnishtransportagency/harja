(ns harja.palvelin.integraatiot.sampo.tuonti-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.sampo.tuonti :as tuonti])
  (:import (javax.jms TextMessage)))

(def +testihanke-sanoma+ "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Sampo2harja xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"SampToharja.xsd\">
    <Program id=\"TESTIHANKE\" manager_Id=\"A010098\" manager_User_Name=\"A010098\" message_Id=\"HankeMessageId\"
             name=\"Testi alueurakka 2009-2014\" schedule_finish=\"2013-12-31T00:00:00.0\"
             schedule_start=\"2009-01-01T00:00:00.0\" vv_alueurakkanro=\"1238\" vv_code=\"14-1177\">
        <documentLinks/>
    </Program>
</Sampo2harja>
")

(def +kuittausjono-sisaan+ "kuittausjono-sisaan")

(deftest tarkista-hankkeen-tallentuminen
  (let [db (apply tietokanta/luo-tietokanta testitietokanta)
        viesti (reify TextMessage
                 (getText [this] +testihanke-sanoma+))]
    (tuonti/kasittele-viesti db +kuittausjono-sisaan+ viesti)
    (is (= 1 (count(q "select id from hanke where sampoid = 'TESTIHANKE';"))))
    (u "delete from hanke where sampoid = 'TESTIHANKE'")))
