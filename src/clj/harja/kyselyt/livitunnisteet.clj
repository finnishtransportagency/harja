(ns harja.kyselyt.livitunnisteet
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/livitunnisteet.sql"
  {:positional? true})

(defn hae-seuraava-livitunniste [db]
  (let [numero (:nextval (first (hae-seuraava-tunniste db)))]
    (str "HARJ" (format "%016d" numero))))
