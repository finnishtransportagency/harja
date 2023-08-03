(ns harja.ui.tyokalut.raportti-tyokalut
  ;; Tänne voi laittaa mm yksittäisten raporttien funktioita
  (:require [harja.ui.raportti :as raportointi]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.debug :refer [debug]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.nakymasiirrin :as siirrin]))

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
       [:h1 (str (fmt/euro laskutettu))]])
    ]])

(defmethod raportointi/muodosta-html :tyomaapaivakirja-header [[_ tyomaapaivakirja]]
  (when tyomaapaivakirja
    [:<>
     #_[:div [debug tyomaapaivakirja]]

     [:h3.header-yhteiset (:urakka-nimi tyomaapaivakirja)]
     ;; Id lisätty väliaikaisesti visualisointia varten
     [:h1.header-yhteiset (str "Työmaapäiväkirja " (pvm/pvm (:paivamaara tyomaapaivakirja)) " id: " (:tyomaapaivakirja_id tyomaapaivakirja))]

     [:div.nakyma-otsikko-tiedot

      [:span (str "Saapunut " (pvm/pvm-aika-klo (:luotu tyomaapaivakirja)))]
      (when (:muokattu tyomaapaivakirja)
        [:span (str "Päivitetty " (pvm/pvm-aika-klo (:muokattu tyomaapaivakirja)))])
      [:span (str "Versio " (:versio tyomaapaivakirja))]
      ;;TODO: Tehdään myöhemmin
      #_[:a.klikattava "Näytä muutoshistoria"]

      [:span.paivakirja-toimitus
       [:div {:class (str "pallura " (:tila tyomaapaivakirja))}]
       [:span.toimituksen-selite (if (= "myohassa" (:tila tyomaapaivakirja))
                                   "Myöhässä"
                                   "Ok")]]

      ;; Kommentti- nappi scrollaa alas kommentteihin
      ;; TODO: Toteutetaan myöhemmin
      #_[:a.klikattava {:on-click #(.setTimeout js/window (fn [] (siirrin/kohde-elementti-id "Kommentit")) 150)}
         [ikonit/ikoni-ja-teksti (ikonit/livicon-kommentti) "2 kommenttia"]]]

     [:hr]]))

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

(defmethod raportointi/muodosta-html :tyomaapaivakirjan-kommentit [[_]]
  [:div.row.filtterit.kommentit-valistys {:id "Kommentit"}
   [:h2 "Kommentit"]

   ;; Kommentin päiväys ja nimi
   [:div.alarivi-tiedot
    [:span "10.10.2022 15:45"]
    [:span "Timo Tilaaja"]]

   ;; Itse kommentti
   [:div.kommentti
    [:h1.tieto-rivi "Tästähän puuttuu nelostien rekka-kolari"]
    [:span.klikattava.kommentti-poista {:on-click (fn []
                                                    (println "Klikattu poista kommentti"))} (ikonit/action-delete)]]


   ;; Muutoshistoria tiedot
   [:div.alarivi-tiedot
    [:span "11.10.2022 07:45"]
    [:span "Tauno Työnjohtaja"]
    [:span.muutos-info "Jälkikäteismerkintä urakoitsijajärjestelmästä"]]

   ;; Muutoshistoria
   [:div.kommentti.muutos
    [:h1.tieto-rivi "Työmaapäiväkirja päivitetty 11.10.2022 08:10: lisätty rekka-kolari."]
    [:a.klikattava.info-rivi "Näytä muutoshistoria"]]

   [:div.kommentti-lisaa
    [:a.klikattava {:on-click (fn []
                                (println "Klikattu lisää kommentti"))}
     [ikonit/ikoni-ja-teksti (ikonit/livicon-kommentti) "Lisää kommentti"]]]])
