(ns harja.ui.tyokalut.raportti-tyokalut
  ;; Tänne voi laittaa mm yksittäisten raporttien funktioita
  (:require [harja.fmt :as fmt]
            [harja.ui.raportti :as raportointi]))


(defmethod raportointi/muodosta-html
  :tyomaa-laskutusyhteenveto-yhteensa [[_ kyseessa-kk-vali?
                                        laskutusraja-kaytossa?
                                        laskutusraja-ylittynyt?
                                        laskutettu laskutetaan
                                        laskutettavaa_kaikki_yht laskutettavaa_kaikki_val_aika
                                        laskutettu-str laskutetaan-str]]
  [raportointi/muodosta-html
   [:display-flex
    [:sininen-laatikko {:otsikko (if (and laskutusraja-kaytossa? laskutusraja-ylittynyt?)
                                   "Toteutuneet kustannukset yhteensä"
                                   "Laskutettavaa yhteensä")
                        :layout :sarakkeet}
     [{:fmt :raha
       :arvo laskutettu
       :avain laskutettu-str}

      (when kyseessa-kk-vali?
        {:fmt :raha
         :arvo laskutetaan
         :avain laskutetaan-str
         :korosta? (and
                     (not laskutusraja-ylittynyt?) laskutusraja-kaytossa?)})]]

    (when
      (and laskutusraja-kaytossa? laskutusraja-ylittynyt?)
      [:sininen-laatikko {:otsikko "Laskutettavaa yhteensä"
                          :layout :sarakkeet}
       [{:fmt :raha
         :korosta? (not kyseessa-kk-vali?)
         :avain "Hoitovuoden alusta"
         :arvo laskutettavaa_kaikki_yht}

        (when kyseessa-kk-vali?
          {:fmt :raha
           :korosta? true
           :avain laskutetaan-str
           :arvo laskutettavaa_kaikki_val_aika})]])]])


(defmethod raportointi/muodosta-html :gridit-vastakkain [[_
                                                          {:keys [otsikko-vasen optiot-vasen otsikot-vasen rivit-vasen]}
                                                          {:keys [otsikko-oikea optiot-oikea otsikot-oikea rivit-oikea]}]]
  ;; Tekee 2 taulukkoa vierekkän
  [:div.flex-gridit
   [:div.width-half
    [:h3.gridin-otsikko otsikko-vasen]
    (let [{otsikko :otsikko
           gridin-luokka :gridin-luokka
           viimeinen-rivi-yhteenveto? :viimeinen-rivi-yhteenveto?
           rivi-ennen :rivi-ennen
           piilota-border? :piilota-border?
           raportin-tunniste :raportin-tunniste
           tyhja :tyhja
           korosta-rivit :korosta-rivit
           korostustyyli :korostustyyli
           oikealle-tasattavat-kentat :oikealle-tasattavat-kentat
           vetolaatikot :vetolaatikot
           esta-tiivis-grid? :esta-tiivis-grid?
           avattavat-rivit :avattavat-rivit
           sivuttain-rullattava? :sivuttain-rullattava?
           ensimmainen-sarake-sticky? :ensimmainen-sarake-sticky?} optiot-vasen]
      [raportointi/grid
       otsikko
       gridin-luokka
       viimeinen-rivi-yhteenveto?
       rivi-ennen
       piilota-border?
       raportin-tunniste
       tyhja
       korosta-rivit
       korostustyyli
       oikealle-tasattavat-kentat
       vetolaatikot
       esta-tiivis-grid?
       avattavat-rivit
       sivuttain-rullattava?
       ensimmainen-sarake-sticky?
       otsikot-vasen rivit-vasen])]
   ;; Ei piirretä oikeaa elementtiä, jos sitä ei ole annettu.
   (if otsikko-oikea
     [:div.width-half
      [:h3.gridin-otsikko otsikko-oikea]
      (let [{otsikko :otsikko
             gridin-luokka :gridin-luokka
             viimeinen-rivi-yhteenveto? :viimeinen-rivi-yhteenveto?
             rivi-ennen :rivi-ennen
             piilota-border? :piilota-border?
             raportin-tunniste :raportin-tunniste
             tyhja :tyhja
             korosta-rivit :korosta-rivit
             korostustyyli :korostustyyli
             oikealle-tasattavat-kentat :oikealle-tasattavat-kentat
             vetolaatikot :vetolaatikot
             esta-tiivis-grid? :esta-tiivis-grid?
             avattavat-rivit :avattavat-rivit
             sivuttain-rullattava? :sivuttain-rullattava?
             ensimmainen-sarake-sticky? :ensimmainen-sarake-sticky?} optiot-oikea]
        [raportointi/grid
         otsikko 
         gridin-luokka
         viimeinen-rivi-yhteenveto?
         rivi-ennen
         piilota-border?
         raportin-tunniste
         tyhja
         korosta-rivit
         korostustyyli
         oikealle-tasattavat-kentat
         vetolaatikot
         esta-tiivis-grid?
         avattavat-rivit
         sivuttain-rullattava?
         ensimmainen-sarake-sticky?
         otsikot-oikea rivit-oikea])]
     [:div.width-half])])

(defmethod raportointi/muodosta-html :tyomaapaivakirjan-kommentit [[_ _]]
  ;; Kommenteiden html käsitellään paivakirja.cljs koska niiden kanssa tehdään palvelinkutsuja
  ;; Kommenteille tehdään oma PDF metodi erikseen pdf_tyokalut.clj jossa generoidaan kommentit PDFään
  nil)

(defmethod raportointi/muodosta-html :tyomaapaivakirja-header [[_ _]]
  ;; Header HTML käsitellään myös paivakirja.cljs
  nil)
