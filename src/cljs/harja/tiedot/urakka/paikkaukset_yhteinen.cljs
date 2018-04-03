(ns harja.tiedot.urakka.paikkaukset-yhteinen
  "Tarvitaan oma ns circular dependencyn poistamiseksi")

(defonce paikkauskohde (atom nil))