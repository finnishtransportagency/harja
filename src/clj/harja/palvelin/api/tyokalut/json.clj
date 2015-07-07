(ns harja.palvelin.api.tyokalut.json
  "APIn kautta tulevan JSONin käsittelyn apureita."
  (:require [harja.kyselyt.konversio :as konv])
  (:import (java.text SimpleDateFormat)))

(defn parsi-aika [paivamaara]
  (konv/sql-date (.parse (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") paivamaara)))
