(ns harja.asiakas.main
  (:require [harja.asiakas.ymparisto :as ymparisto]))

(defn ^:export harja []
  (ymparisto/alusta)
  (aset js/window "HARJA_LADATTU" true))


