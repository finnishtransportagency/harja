(ns harja.ui.tyokalut.raportti-tyokalut
  ;; Tänne voi laittaa mm yksittäisten raporttien funktioita
  (:require [harja.ui.raportti :as raportointi]
            [harja.fmt :as fmt]))


(defmethod raportointi/muodosta-html
  :tyomaa-laskutusyhteenveto-yhteensa [[_ kyseessa-kk-vali?
                                        laskutusraja-kaytossa?
                                        laskutusraja-ylittynyt?
                                        laskutettu laskutetaan
                                        laskutettavaa_kaikki_yht laskutettavaa_kaikki_val_aika
                                        laskutettu-str laskutetaan-str]]

  [raportointi/muodosta-html
   [:display-flex
    [:sininen-laatikko {:otsikko (if laskutusraja-kaytossa?
                                   "Toteutuneet kustannukset yhteensä"
                                   ;; Vanhat urakat joilla ei ole laskutusrajaa
                                   "Laskutettavaa yhteensä")
                        :layout :sarakkeet}
     [{:fmt :raha
       :arvo laskutettu
       :avain laskutettu-str}

      (when kyseessa-kk-vali?
        {:fmt :raha
         :arvo laskutetaan
         :avain laskutetaan-str})]]

    (when
      (and laskutusraja-kaytossa? laskutusraja-ylittynyt?)
      [:sininen-laatikko {:otsikko "Laskutettavaa yhteensä"
                          :layout :sarakkeet}
       [{:fmt :raha
         :avain "Hoitovuoden alusta"
         :arvo laskutettavaa_kaikki_yht}

        (when kyseessa-kk-vali?
          {:fmt :raha
           :avain "Helmikuu"
           :arvo laskutettavaa_kaikki_val_aika})]])

    #_[:sininen-laatikko {:otsikko "Laskutettavaa yhteensä"}
       [{:avain "Hoitovuoden alun indeksikorjattu tavoitehinta"
         :arvo 123M
         :fmt :raha}
        {:avain "Kirjallisesti sovitut muutokset"
         :arvo 123M
         :fmt :raha}
        {:avain "Toteumiin perustuvat muutokset"
         :arvo 123M
         :fmt :raha}
        {:avain "Yhteensä"
         :arvo 123M
         :fmt :raha :lihavoi? true}]]

    ]

   ]

  ;; Työmaakokouksen laskutusyhteenvedon footer
  #_(if kyseessa-valittu-aikavali?
      [:div
       [:div.tyomaakokous-footer
        [:div.sisalto-valittu-aikavali
         [:h3 (str "Toteutuneet kustannukset yhteensä")]
         [:span.laskutus-yhteensa laskutettu-str]
         [:h1 [:span (str (fmt/euro laskutetaan))]]]]]
      [:div
       [:div.tyomaakokous-footer
        (if kyseessa-kk-vali?
          (when laskutusraja-ylittynyt?
            [:div.sisalto
             [:h3 (str "Toteutuneet kustannukset yhteensä")]
             [:h1 ""]
             [:span.laskutus-yhteensa laskutettu-str]
             [:span.laskutus-yhteensa laskutetaan-str]
             [:h1 (str (fmt/euro laskutettu))]
             [:h1 [:span (str (fmt/euro laskutetaan))]]])

          [:div.sisalto-ei-kk-vali
           [:h3 (str "Toteutuneet kustannukset yhteensä")]
           [:span.laskutus-yhteensa laskutettu-str]
           [:h1 (str (fmt/euro laskutettu))]])
        (if kyseessa-kk-vali?
          [:div.sisalto
           [:h3 (str "Laskutettavaa yhteensä ")]
           [:h1 ""]
           [:span.laskutus-yhteensa laskutettu-str]
           [:span.laskutus-yhteensa laskutetaan-str]
           (if laskutusraja-ylittynyt?
             [:h1 (str (fmt/euro laskutusraja))]
             [:h1 (str (fmt/euro laskutettu))])
           (if laskutusraja-ylittynyt?
             [:h1 [:span.vahvistamaton (str (fmt/euro kk-sallittu-laskutusosuus))]]
             [:h1 [:span.vahvistamaton (str (fmt/euro laskutetaan))]])]

          [:div.sisalto-ei-kk-vali
           [:h3 (str "Laskutettavaa yhteensä")]
           [:span.laskutus-yhteensa laskutettu-str]
           (if laskutusraja-ylittynyt?
             [:h1 (str (fmt/euro laskutusraja))]
             [:h1 (str (fmt/euro laskutettu))])])]]))

(defmethod raportointi/muodosta-html :tyomaa-toteutuneet-kustannukset-yhteenveto [[_ kyseessa-kk-vali? hoitokausi laskutettu laskutetaan laskutettu-str laskutetaan-str]]
  ;; Työmaakokouksen laskutusyhteenvedon footer
  [:div
   [:div.tyomaakokous-footer
    [:h3 (str "Toteutuneet kustannukset yhteensä " hoitokausi)]
    (if kyseessa-kk-vali?
      [:div.sisalto
       [:span.laskutus-yhteensa laskutettu-str]
       [:span.laskutus-yhteensa laskutetaan-str]
       [:h1 (str (fmt/euro laskutettu))]
       [:h1 [:span (str (fmt/euro laskutetaan))]]]

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
