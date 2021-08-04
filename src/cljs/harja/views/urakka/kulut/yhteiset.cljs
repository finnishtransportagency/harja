(ns harja.views.urakka.kulut.yhteiset
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.tyokalut.big :as big]
            [harja.ui.napit :as napit]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.kulut.valikatselmus :as valikatselmus]))

(defn fmt->big
  ([arvo] (fmt->big arvo false))
  ([arvo on-big?]
   (let [arvo (if on-big?
                arvo
                (big/->big arvo))
         fmt-arvo (harja.fmt/desimaaliluku (or (:b arvo) 0) 2 true)]
     fmt-arvo)))

(defn yhteenveto-laatikko [e! app data sivu]
  (let [valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        valittu-hoitovuosi-nro (kustannusten-seuranta-tiedot/hoitokauden-jarjestysnumero valittu-hoitokauden-alkuvuosi)
        tavoitehinta (big/->big (or (kustannusten-seuranta-tiedot/hoitokauden-tavoitehinta valittu-hoitovuosi-nro app) 0))
        kattohinta (big/->big (or (kustannusten-seuranta-tiedot/hoitokauden-kattohinta valittu-hoitovuosi-nro app) 0))
        toteuma (big/->big (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0))
        oikaisujen-summa (big/->big (or (apply + (map ::valikatselmus/summa (filter
                                                                              #(not (or (:poistettu %) (::muokkaustiedot/poistettu? %)))
                                                                              (vals @(:tavoitehinnan-oikaisut-atom app))))) 0))
        oikaisuja? (not (or (nil? oikaisujen-summa) (= 0 oikaisujen-summa)))
        oikaistu-tavoitehinta (big/plus tavoitehinta oikaisujen-summa)
        oikaistu-kattohinta (big/plus kattohinta oikaisujen-summa)
        ;; TODO: logiikka välikatselmuksen tekemättömyyteen tähän e.q onko päätöksiä tehty tietyllä aikavälillä
        valikatselmus-tekematta? true]
    [:div
     [:div.yhteenveto.elevation-2
      [:h2 [:span "Yhteenveto"]]
      (when (and valikatselmus-tekematta? (not= :valikatselmus sivu))
        [:div.valikatselmus
         "Välikatselmus puuttuu"
         [napit/yleinen-ensisijainen
          "Tee välikatselmus"
          #(e! (kustannusten-seuranta-tiedot/->AvaaValikatselmusLomake))]])
      [:div.rivi [:span (if oikaisuja? "Alkuperäinen tavoitehinta" "Tavoitehinta")] [:span (fmt->big tavoitehinta true)]]
      (when oikaisuja?
        [:<>
         [:div.rivi [:span "Tavoitehinnan oikaisu"] [:span (str (when (pos? (:b oikaisujen-summa)) "+") (fmt->big oikaisujen-summa true))]]
         [:div.rivi [:span "Tavoitehinta"] [:span (fmt->big oikaistu-tavoitehinta true)]]])
      (when (big/gt toteuma oikaistu-tavoitehinta)
        [:div.rivi [:span "Tavoitehinnan ylitys"]
         [:span.negatiivinen-numero
          (str "+ " (fmt->big (big/minus toteuma oikaistu-tavoitehinta)))]])
      [:div.rivi [:span "Kattohinta"] [:span (fmt->big oikaistu-kattohinta true)]]
      (when (big/gt toteuma oikaistu-kattohinta)
        [:div.rivi [:span "Kattohinnan ylitys"]
         [:span.negatiivinen-numero
          (str "+ " (fmt->big (big/minus toteuma oikaistu-kattohinta)))]])
      [:div.rivi [:span "Toteuma"] [:span (fmt->big toteuma true)]]

      [:div.rivi [:span "Lisätyöt"] [:span (fmt->big (:lisatyot-summa data) false)]]]]))