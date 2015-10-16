(ns harja.kyselyt.livitunnisteet
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/livitunnisteet.sql")

(defn hae-seuraava-livitunniste [db]
  (let [numero (:nextval (first (harja.kyselyt.livitunnisteet/hae-seuraava-tunniste db)))]
    (str "HARJ" (format "%016d" numero))))
