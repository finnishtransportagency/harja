(ns harja.palvelin.integraatiot.api.kutsukasittely-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.api.tyokalut.kutsukasittely :as kutsukasittely]
            [harja.palvelin.komponentit.tietokanta :as tietokanta])
  (:import (org.apache.commons.io IOUtils)))

(deftest huomaa-kutsu-jossa-epavalidia-json-dataa
  (let [
        kutsun-data (IOUtils/toInputStream "{\"asdfasdfa\":234}")
        vastaus (kutsukasittely/kasittele-kutsu (apply tietokanta/luo-tietokanta testitietokanta) "testi" {:body kutsun-data :request-method :post :headers {"oam_remote_user" "yit-rakennus",}} skeemat/+havainnon-kirjaus+ skeemat/+kirjausvastaus+ (fn [parametit data]))]
    (is (= 400 (:status vastaus)))
    (is (.contains (:body vastaus) "invalidi-json"))))

(deftest huomaa-kutsu-jossa-tuntematon-kayttaja
  (let [
        kutsun-data (IOUtils/toInputStream "{\"asdfasdfa\":234}")
        vastaus (kutsukasittely/kasittele-kutsu (apply tietokanta/luo-tietokanta testitietokanta) "testi" {:body kutsun-data :request-method :post :headers {"oam_remote_user" "tuntematon",}} skeemat/+havainnon-kirjaus+ skeemat/+kirjausvastaus+ (fn [parametit data]))]
    (is (= 400 (:status vastaus)))
    (is (.contains (:body vastaus) "tuntematon-kayttaja"))))