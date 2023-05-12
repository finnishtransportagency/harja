(ns harja.ui.tyokalut.raportti-tyokalut
  ;; Tänne voi laittaa mm yksittäisten raporttien funktioita

  (:require [harja.ui.raportti :as raportointi]
            [harja.ui.ikonit :as ikonit]
            [harja.fmt :as fmt]))

(defmethod raportointi/muodosta-html :tyomaa-laskutusyhteenveto-yhteensa [[_ kyseessa-kk-vali? hoitokausi laskutettu laskutetaan laskutettu-str laskutetaan-str]]
  ;; Työmaakokouksen laskutusyhteenvedon footer
  [:div
   [:div {:class "tyomaakokous-footer"}
    [:h3 (str "Laskutus yhteensä " hoitokausi)]

    (if kyseessa-kk-vali?
      [:div {:class "sisalto"}
       [:span {:class "laskutus-yhteensa"} laskutettu-str]
       [:span {:class "laskutus-yhteensa"} laskutetaan-str]
       [:h1 (str (fmt/euro laskutettu))]
       [:h1 [:span {:class "vahvistamaton"} (str (fmt/euro laskutetaan))]]]

      [:div {:class "sisalto-ei-kk-vali"}
       [:span {:class "laskutus-yhteensa"} laskutettu-str]
       [:h1 (str (fmt/euro laskutettu))]])
    ]])

(defmethod raportointi/muodosta-html :tyomaapaivakirja-header [[_ valittu-rivi]]
  [:<>
   [:p (str valittu-rivi)]

   [:h3 {:class "header-yhteiset"} "UUD MHU 2022–2027"]
   [:h1 {:class "header-yhteiset"} "Työmaapäiväkirja 9.10.2022"]

   [:div {:class "nakyma-otsikko-tiedot"}

    [:span "Saapunut 11.10.2022 05:45"]
    [:span "Päivitetty 11.10.2022 05:45"]
    [:a "Näytä muutoshistoria"]

    [:span {:class "paivakirja-toimitus"}
     [:div {:class (str "pallura " "myohassa")}]
     [:span {:class "kohta"} "Myöhässä"]]

    [:a
     [ikonit/ikoni-ja-teksti (ikonit/livicon-kommentti) "2 kommenttia"]]]

   [:hr]])


(defmethod raportointi/muodosta-html :gridit-vastakkain [[_
                                                          {:keys [otsikko-vasen optiot-vasen otsikot-vasen rivit-vasen]}
                                                          {:keys [otsikko-oikea optiot-oikea otsikot-oikea rivit-oikea]}]]
  ;; Tekee 2 taulukkoa vierekkän
  [:<>
   [:div {:class "flex-gridit"}
    [:div
     [:h3 {:class "gridin-otsikko"} otsikko-vasen]
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
       (raportointi/generoi-gridi
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
         otsikot-vasen rivit-vasen))]

    [:div
     [:h3 {:class "gridin-otsikko"} otsikko-oikea]
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
       (raportointi/generoi-gridi
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
         otsikot-oikea rivit-oikea))]]])
