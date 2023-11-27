(ns harja.tyokalut.loki)

(def alert "ALERT")

(defn koristele-lokiviesti
  "Käytä tätä apuria lokiviestin koristeluun määritellyillä koristelutageilla:
  esim. (log/error (koristele-lokiviesti loki/alert \"Hälytystason virhe!\"))"
  ([viesti] (koristele-lokiviesti viesti nil))
  ([viesti tag]
   (str (when tag (str " - " tag " - ")) viesti)))

