(ns harja.palvelin.komponentit.tapahtumat-test
  (:require [harja.palvelin.komponentit.tapahtumat :as sut]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [clojure.test :as t :refer [deftest is use-fixtures testing]]))

(defn jarjestelma-fixture [testit]
  (alter-var-root
   #'jarjestelma
   (fn [_]
     (component/start
      (component/system-map
       :db (tietokanta/luo-tietokanta testitietokanta)
       ;; :http-palvelin (testi-http-palvelin)
       :klusterin-tapahtumat (sut/luo-tapahtumat)
       :integraatioloki (component/using (integraatioloki/->Integraatioloki nil) [:db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(deftest kuuntelu-perustapaus []
  (let [saatiin (atom false)]
    (sut/kuuntele! (:klusterin-tapahtumat harja.palvelin.main/harja-jarjestelma) "seppo" (fn kuuntele-callback [viesti] (reset! saatiin true) (println "viesti saatu:" viesti)))
    (sut/julkaise! (:klusterin-tapahtumat harja.palvelin.main/harja-jarjestelma) "seppo" "foo")
    (Thread/sleep 1500)
    (is (= @saatiin true))))
