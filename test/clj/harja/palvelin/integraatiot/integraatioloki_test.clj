(ns harja.palvelin.integraatiot.integraatioloki-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki] :as integraatioloki]))

(def +testiviesti+ {:suunta "ulos" :sisaltotyyppi "application/xml" :siirtotyyppi "jms" :sisalto "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" :otsikko nil :parametrit nil})

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :integraatioloki (component/using (->Integraatioloki nil) [:db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(defn poista-testitapahtuma [tapahtuma-id]
  (u "DELETE FROM integraatioviesti WHERE integraatiotapahtuma = " tapahtuma-id ";")
  (u "DELETE FROM integraatiotapahtuma WHERE id = " tapahtuma-id ";"))

(deftest tarkista-integraation-aloituksen-kirjaaminen
  (let [tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio (:integraatioloki jarjestelma) "sampo" "sisaanluku" nil nil)]
    (is tapahtuma-id "Tapahtumalle palautettiin id.")
    (is (first (first (q "SELECT exists(SELECT id FROM integraatiotapahtuma WHERE id = " tapahtuma-id ");")))
        "Tietokannasta löytyy integraatiotapahtuma integraation aloituksen jälkeen.")
    (poista-testitapahtuma tapahtuma-id)))

(deftest tarkista-onnistuneen-integraation-kirjaaminen
  (let [tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio (:integraatioloki jarjestelma) "sampo" "sisaanluku" nil nil)]
    (integraatioloki/kirjaa-onnistunut-integraatio (:integraatioloki jarjestelma) nil nil tapahtuma-id nil)
    (is (first (first (q "SELECT exists(SELECT id FROM integraatiotapahtuma WHERE id = " tapahtuma-id " AND onnistunut is true AND paattynyt is not null);")))
        "Tietokannasta löytyy integraatiotapahtuma joka on merkitty onnistuneeksi.")
    (poista-testitapahtuma tapahtuma-id)))

(deftest tarkista-viestin-kirjaaminen
  (let [tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio (:integraatioloki jarjestelma) "sampo" "sisaanluku" nil +testiviesti+)]
    (is (= 1 (count (q "SELECT id FROM integraatioviesti WHERE integraatiotapahtuma = " tapahtuma-id ";"))))
    (poista-testitapahtuma tapahtuma-id)))
