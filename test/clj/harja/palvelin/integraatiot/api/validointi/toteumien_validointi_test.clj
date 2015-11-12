(ns harja.palvelin.integraatiot.api.validointi.toteumien-validointi-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.api.validointi.toteumat :as validointi]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer :all]))

(deftest tarkista-reittipisteiden-aikojen-tarkistus
  (let [reitti [{:reittipiste {:aika "2014-02-02T12:00:00Z"}}
                {:reittipiste {:aika "2014-02-02T13:00:00Z"}}]]

    (validointi/tarkista-reittipisteet
      {:reittitoteuma {:toteuma {:alkanut   "2014-01-01T12:00:00Z"
                                 :paattynyt "2014-02-03T12:00:00Z"}
                       :reitti  reitti}})

    (is (thrown? Exception (validointi/tarkista-reittipisteet
                             {:reittitoteuma
                              {:toteuma
                                       {:alkanut   "2014-02-03T12:00:00Z"
                                        :paattynyt "2014-02-04T12:00:00Z"}
                               :reitti reitti}})
                 "Poikkeusta ei heitetty epävalidista reittipisteestä, kun reittipiste on kirjattu ennen toteuman alkua."))

    (is (thrown? Exception (validointi/tarkista-reittipisteet
                             {:reittitoteuma
                              {:toteuma
                                       {:alkanut   "2014-01-01T12:00:00Z"
                                        :paattynyt "2014-01-02T12:00:00Z"}
                               :reitti reitti}})
                 "Poikkeusta ei heitetty epävalidista reittipisteestä, kun reittipiste on kirjattu toteuman päättymisen jälkeen."))))

(deftest tarkiasta-toteuman-tehtavien-tarkistus
  (let [db (apply tietokanta/luo-tietokanta testitietokanta)]
    (validointi/tarkista-tehtavat db 1 [{:tehtava {:id 1370}}])
    (is (thrown? Exception (validointi/tarkista-tehtavat db 1 [{:tehtava {:id 608}} {:tehtava {:id 1}}])
                 "Poikkeusta ei heitetty, kun yksi toteuman tehtävistä ei ole urakalla"))))