(ns harja.palvelin.integraatiot.integraatioloki-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki] :as integraatioloki]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :integraatioloki (component/using (->Integraatioloki nil) [:db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest tarkista-integraation-aloituksen-kirjaaminen
  (let [tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio (:integraatioloki jarjestelma) "sampo" "sisäänluku" nil nil)]
    (is tapahtuma-id "Tapahtumalle palautettiin id.")
    (is (first (first (q "SELECT exists(SELECT id FROM integraatiotapahtuma WHERE id = " tapahtuma-id ");")))
        "Tietokannasta löytyy integraatiotapahtuma integraation aloituksen jälkeen.")
    (u "DELETE FROM integraatiotapahtuma WHERE id = " tapahtuma-id ";")))

(deftest tarkista-onnistuneen-integraation-kirjaaminen
  (let [tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio (:integraatioloki jarjestelma) "sampo" "sisäänluku" nil nil)]
    (integraatioloki/kirjaa-onnistunut-integraatio (:integraatioloki jarjestelma) nil nil tapahtuma-id nil)
    (is (first (first (q "SELECT exists(SELECT id FROM integraatiotapahtuma WHERE id = " tapahtuma-id " AND onnistunut is true AND paattynyt is not null);")))
        "Tietokannasta löytyy integraatiotapahtuma joka on merkitty onnistuneeksi.")
    (u "DELETE FROM integraatiotapahtuma WHERE id = " tapahtuma-id ";")))
