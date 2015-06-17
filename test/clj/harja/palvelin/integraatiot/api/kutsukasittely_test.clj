(ns harja.palvelin.integraatiot.api.kutsukasittely-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.api.skeemat :as skeemat]
            [harja.palvelin.api.kutsukasittely :as kutsukasittely])
  (:import (org.apache.commons.io IOUtils)))

(deftest huomaa-kutsu-jossa-epavalidia-json-dataa
  (let [
        kutsun-data (IOUtils/toInputStream "{\"asdfasdfa\":234}")
        vastaus (kutsukasittely/kasittele-kutsu "testi" {:body kutsun-data :request-method :post} skeemat/+havainnon-kirjaus+ skeemat/+kirjausvastaus+ (fn [parametit data]))]
    (println vastaus)
    (is (= 400 (:status vastaus)))
    (is (.contains (:body vastaus) "Viallinen kutsu. Vastaanotettu JSON ei ole validi"))))