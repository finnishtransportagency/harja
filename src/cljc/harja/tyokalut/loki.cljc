(ns harja.tyokalut.loki)

;; Alert-tageillä koristellut lokiviestit ovat geneerisiä hälytystason virheviestejä, jotka vaativat erityistä huomiota.
(def alert "ALERT")

;; -- JMS hälytystagit --

;; JMS-alert tagilla koristellaan lokiviestit, jotka ovat selkeästi JMS-virheviestejä viestien kulussa
;; ja vaativat erityistä huomiota.
(def jms-alert-TAG "JMS-ALERT")

;; JMS-delay-alert tagilla koristellaan lokiviestit, jotka liittyvät JMS-viestien odotusaikojen ylittymiseen.
(def jms-delay-alert-TAG "JMS-DELAY-ALERT")

(defn koristele-lokiviesti
  "Käytä tätä apuria lokiviestin koristeluun määritellyillä koristelutageilla:
  esim. (log/error (koristele-lokiviesti loki/alert \"Hälytystason virhe!\"))"
  ([viesti] (koristele-lokiviesti viesti nil))
  ([viesti tag]
   (str (when tag (str " - " tag " - ")) viesti)))

