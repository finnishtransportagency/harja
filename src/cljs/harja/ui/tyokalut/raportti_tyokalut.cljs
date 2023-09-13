(ns harja.ui.tyokalut.raportti-tyokalut
  ;; Tänne voi laittaa mm yksittäisten raporttien funktioita
  (:require [harja.ui.raportti :as raportointi]
            [harja.fmt :as fmt]))

(defmethod raportointi/muodosta-html :tyomaa-laskutusyhteenveto-yhteensa [[_ kyseessa-kk-vali? hoitokausi laskutettu laskutetaan laskutettu-str laskutetaan-str]]
  ;; Työmaakokouksen laskutusyhteenvedon footer
  [:div
   [:div.tyomaakokous-footer
    [:h3 (str "Laskutus yhteensä " hoitokausi)]

    (if kyseessa-kk-vali?
      [:div.sisalto
       [:span.laskutus-yhteensa laskutettu-str]
       [:span.laskutus-yhteensa laskutetaan-str]
       [:h1 (str (fmt/euro laskutettu))]
       [:h1 [:span.vahvistamaton (str (fmt/euro laskutetaan))]]]

      [:div.sisalto-ei-kk-vali
       [:span.laskutus-yhteensa laskutettu-str]
       [:h1 (str (fmt/euro laskutettu))]])]])

(defmethod raportointi/muodosta-html :gridit-vastakkain [[_
                                                          {:keys [otsikko-vasen optiot-vasen otsikot-vasen rivit-vasen]}
                                                          {:keys [otsikko-oikea optiot-oikea otsikot-oikea rivit-oikea]}]]
  ;; Tekee 2 taulukkoa vierekkän
  [:div.flex-gridit
   [:div.width-half
    [:h3.gridin-otsikko otsikko-vasen]
    (let [{otsikko :otsikko
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
