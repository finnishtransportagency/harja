(ns harja.tyokalut.loki)

(def alert "ALERT")

(defn koristele-lokiviesti
  "Käytä tätä apuria lokiviestin koristeluun määritellyillä koristelutageilla:
  esim. (log/error (koristele-lokiviesti loki/alert \"Hälytystason virhe!\"))"
  [tag viesti]
  (str " - " tag " - " viesti))

