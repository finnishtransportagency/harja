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
       :klusterin-tapahtumat (component/using (sut/luo-tapahtumat) [:db])
       :integraatioloki (component/using (integraatioloki/->Integraatioloki nil) [:db])))))

  (testit)
  (try (alter-var-root #'jarjestelma component/stop)
       (catch Exception e (println "saatiin poikkeus komponentin sammutuksessa: " e))))

(use-fixtures :once jarjestelma-fixture)

(deftest julkaisu-ja-kuuntelu []
  (testing "tapahtumat-komponentti on luotu onnistuneesti"
    (is (some? (:klusterin-tapahtumat jarjestelma))))
  (let [saatiin (atom nil)]
    (testing "Perustapaus" (sut/kuuntele! (:klusterin-tapahtumat jarjestelma) "seppo" (fn kuuntele-callback [viesti] (reset! saatiin true) (println "viesti saatu:" viesti)))
             (sut/julkaise! (:klusterin-tapahtumat jarjestelma) "seppo" "foo")
             (is (odota-arvo saatiin)))
    (testing "Toipuminen kantayhteyden katkosta"
      (reset! saatiin nil)

      (pudota-ja-luo-testitietokanta-templatesta)
      (dotimes [n 5]
        (try
          (sut/julkaise! (:klusterin-tapahtumat jarjestelma) "seppo" "foo")
          (catch Exception e
            (Thread/sleep 500))))
      (is (odota-arvo saatiin)))))
