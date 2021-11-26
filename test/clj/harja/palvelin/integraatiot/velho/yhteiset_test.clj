(ns harja.palvelin.integraatiot.velho.yhteiset-test
  (:require [clojure.test :refer :all]))

(defn fake-token-palvelin [_ {:keys [body headers]} _]
  "{\"access_token\":\"TEST_TOKEN\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")
