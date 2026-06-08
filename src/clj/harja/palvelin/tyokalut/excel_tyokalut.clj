(ns harja.palvelin.tyokalut.excel-tyokalut
  ;; Tänne voi laittaa mm yksittäisten raporttien funktioita
  (:require [taoensso.timbre :as log]
            [dk.ative.docjure.spreadsheet :as excel]

            [harja.fmt :as fmt]
            [harja.domain.raportointi :refer [tee-solu]]
            [harja.palvelin.raportointi.excel :as excel-raportointi]))

(defmethod excel-raportointi/muodosta-excel
  :tyomaa-laskutusyhteenveto-yhteensa
  [[_ kyseessa-kk-vali?
    laskutusraja-kaytossa?
    laskutusraja-ylittynyt?
    laskutettu laskutetaan
    laskutettavaa_kaikki_yht laskutettavaa_kaikki_val_aika
    laskutettu-str laskutetaan-str] workbook]

  (let [otsikko-1 (if laskutusraja-kaytossa?
                    "Toteutuneet kustannukset yhteensä"
                    "Laskutettavaa yhteensä")
        otsikko-2? (and laskutusraja-kaytossa? laskutusraja-ylittynyt?)
        sheet (last (excel/sheet-seq workbook))
        start-rivi (+ 2 (.getLastRowNum sheet))
        tyyli-tiedot {:font {:color :black :size 12 :name "Open Sans"}}
        tyyli-normaali (excel/create-cell-style! workbook tyyli-tiedot)
        tyyli-otsikko (excel/create-cell-style! workbook (assoc-in tyyli-tiedot [:font :bold] true))

        euro #(if (some? %) (str (fmt/euro %)) "")

        kirjoita-yhteenveto! (fn [rivi-nro otsikko arvo-yht arvo-val-aika avain-yht avain-val-aika]
                               (let [rivi (.createRow sheet rivi-nro)]
                                 (tee-solu (.createCell rivi 0) otsikko tyyli-otsikko)
                                 (tee-solu (.createCell rivi 1) avain-yht tyyli-otsikko)
                                 (when kyseessa-kk-vali?
                                   (tee-solu (.createCell rivi 2) avain-val-aika tyyli-otsikko)))

                               (let [rivi (.createRow sheet (inc rivi-nro))]
                                 (tee-solu (.createCell rivi 1) (euro arvo-yht) tyyli-normaali)
                                 (when kyseessa-kk-vali?
                                   (tee-solu (.createCell rivi 2) (euro arvo-val-aika) tyyli-normaali)))
                               (+ rivi-nro 3))

        seuraava-rivi (kirjoita-yhteenveto!
                        start-rivi
                        otsikko-1
                        laskutettu laskutetaan
                        laskutettu-str laskutetaan-str)]

    (when otsikko-2? (kirjoita-yhteenveto!
                       seuraava-rivi
                       "Laskutettavaa yhteensä"
                       laskutettavaa_kaikki_yht
                       laskutettavaa_kaikki_val_aika
                       "Hoitovuoden alusta"
                       laskutetaan-str))))

(defn liikenneyhteenveto-arvo-str [arvot tyyppi avain]
  (str (avain (get arvot tyyppi))))

(defmethod excel-raportointi/muodosta-excel :liikenneyhteenveto [[_ sarakkeiden-arvot] workbook]
  ;; Luodaan tehdyn taulukon loppuun yhteenveto liikennetapahtumista
  (try
    (let [aiempi-sheet (last (excel/sheet-seq workbook))
          [sheet nolla] [aiempi-sheet (+ 2 (.getLastRowNum aiempi-sheet))]

          ;; Rivit sekä ensimmäiset sarakkeet
          sarakkeen-ensimmainen-solu {:toimenpiteet "Toimenpiteet"
                                      :palvelumuoto "Palvelumuoto, sulutukset"}
          ;; Yhteenvetosarakkeet
          sarakkeen_nimet {:toimenpiteet {:sulutukset-ylos "Sulutukset ylös: "
                                          :sulutukset-alas "Sulutukset alas: "
                                          :sillan-avaukset "Sillan avaukset: "
                                          :tyhjennykset "Tyhjennykset: "}
                           :palvelumuoto {:paikallispalvelu "Paikallispalvelu: "
                                          :kaukopalvelu "Kaukopalvelu: "
                                          :itsepalvelu "Itsepalvelu: "
                                          :muu "Muu: "
                                          :yhteensa "Sulutukset yhteensä: "}}

          raportin-tiedot-tyyli (excel/create-cell-style! workbook {:font {:color :black
                                                                           :size 12
                                                                           :bold true
                                                                           :name "Open Sans"}})
          nolla (+ 2 nolla)]

      (doall
        ;; Käydään läpi annettujen parametrien (yhteenveto) avaimet
        (for [x (keys sarakkeiden-arvot)]

          (let [rivi-indeksi (.indexOf (keys sarakkeen-ensimmainen-solu) x)
                nolla (+ nolla rivi-indeksi)
                rivi (.createRow sheet nolla)
                tyyli-normaali (excel/create-cell-style! workbook {:font {:color :black
                                                                          :size 12
                                                                          :bold false
                                                                          :name "Open Sans"}})
                ensimmainen-sarake (.createCell rivi 0)]

            ;; Tehdään uusi rivi ja ensimmäinen sarake
            (excel/set-cell! ensimmainen-sarake (str (x sarakkeen-ensimmainen-solu)))
            (excel/set-cell-style! ensimmainen-sarake tyyli-normaali)

            ;; Loput sarakkeet
            (doseq [y (get sarakkeiden-arvot x)]

              (let [nimi (liikenneyhteenveto-arvo-str sarakkeen_nimet x (first y))
                    arvo (liikenneyhteenveto-arvo-str sarakkeiden-arvot x (first y))
                    ;; Sarake indeksi (mille sarakkeelle data laitetaan)
                    ;; Avaimet on indeksijärjestyksessä
                    solu-nro (inc (.indexOf (keys (x sarakkeen_nimet)) (first y)))
                    solu (.createCell rivi solu-nro)]

                (excel/set-cell! solu (str nimi arvo))
                (excel/set-cell-style! solu raportin-tiedot-tyyli)

                ;; Korjattu välitys
                (.autoSizeColumn sheet solu-nro)))))))

    (catch Throwable t
      (log/error t "Virhe Excel muodostamisessa (liikenneyhteenveto)"))))
