(ns harja.palvelin.integraatiot.labyrintti.sms-test
  (:require [clojure.test :refer [deftest is use-fixtures compose-fixtures]]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [slingshot.slingshot :refer [try+]]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.labyrintti.sms :as labyrintti]))

(def +testi-sms-url+ "harja.testi.sms")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    nil
    :labyrintti (component/using (labyrintti/->Labyrintti +testi-sms-url+ "testi" "testi") [:db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest tarkista-tietolajin-haku
  (with-fake-http
    [+testi-sms-url+ "ok"]
    (let [vastaus (labyrintti/laheta (:labyrintti jarjestelma) "0987654321" "Testi")]
      (is (= "ok" (:sisalto vastaus))))))

