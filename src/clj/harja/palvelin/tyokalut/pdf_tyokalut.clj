(ns harja.palvelin.tyokalut.pdf-tyokalut
  ;; Tänne voi laittaa mm yksittäisten raporttien funktioita

  (:require [harja.fmt :as fmt]
            [harja.palvelin.raportointi.pdf :as pdf-raportointi]))

(defmethod pdf-raportointi/muodosta-pdf :tyomaa-laskutusyhteenveto-yhteensa [[_ hoitokausi laskutettu laskutetaan laskutettu-str laskutetaan-str]]
  ;; Muodostaa työmaakokouksen laskutusyhteenvedolle "Laskutus yhteensä" -yhteenvedon 
  ;; Näihin tulee Hoitokauden & Valitun kuukauden otsikot joiden alle arvot annettujen parametrien perusteella

  (pdf-raportointi/arvotaulukko-valittu-aika
   (str "Laskutus yhteensä " hoitokausi)
   (str laskutettu-str)
   (str laskutetaan-str)
   (str (fmt/euro laskutettu))
   (str (fmt/euro laskutetaan))))
