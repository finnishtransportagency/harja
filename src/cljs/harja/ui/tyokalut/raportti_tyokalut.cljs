(ns harja.ui.tyokalut.raportti-tyokalut
  ;; Tänne voi laittaa mm yksittäisten raporttien funktioita

  (:require [harja.ui.raportti :as raportointi]
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
