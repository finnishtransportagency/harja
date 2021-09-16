(ns harja.views.urakka.kulut.yhteiset
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.tyokalut.big :as big]
            [harja.fmt :as fmt]
            [harja.ui.napit :as napit]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.tiedot.urakka.kulut.yhteiset :as t]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.ikonit :as ikonit]
            [harja.pvm :as pvm]))

(defn fmt->big
  ([arvo] (fmt->big arvo false))
  ([arvo on-big?]
   (let [arvo (if on-big?
                arvo
                (big/->big arvo))
         fmt-arvo (harja.fmt/desimaaliluku (or (:b arvo) 0) 2 true)]
     fmt-arvo)))

(defn- paatoksen-maksu-prosentit [paatos vertailtava-summa]
  {:urakoitsija (* 100 (/ (::valikatselmus/urakoitsijan-maksu paatos) vertailtava-summa))
   :tilaaja (* 100 (/ (::valikatselmus/tilaajan-maksu paatos) vertailtava-summa))
   :siirto (* 100 (/ (::valikatselmus/siirto paatos) vertailtava-summa))})

(defn yhteenveto-laatikko [e! app data sivu]
  (let [valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        valittu-hoitovuosi-nro (urakka-tiedot/hoitokauden-jarjestysnumero valittu-hoitokauden-alkuvuosi (-> @tila/yleiset :urakka :loppupvm))
        tavoitehinta (or (kustannusten-seuranta-tiedot/hoitokauden-tavoitehinta valittu-hoitovuosi-nro app) 0)
        kattohinta (or (kustannusten-seuranta-tiedot/hoitokauden-kattohinta valittu-hoitovuosi-nro app) 0)
        toteuma (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0)
        oikaisujen-summa (t/oikaisujen-summa (:tavoitehinnan-oikaisut app) valittu-hoitokauden-alkuvuosi)
        oikaisuja? (not (or (nil? oikaisujen-summa) (= 0 oikaisujen-summa)))
        oikaistu-tavoitehinta (+ tavoitehinta oikaisujen-summa)
        oikaistu-kattohinta (+ kattohinta oikaisujen-summa)
        urakan-paatokset (:urakan-paatokset app)
        filtteroi-paatos-fn (fn [paatoksen-tyyppi]
                              (first (filter #(and (= (::valikatselmus/hoitokauden-alkuvuosi %) valittu-hoitokauden-alkuvuosi)
                                                   (= (::valikatselmus/tyyppi %) (name paatoksen-tyyppi))) urakan-paatokset)))
        tavoitehinta-alitettu? (> oikaistu-tavoitehinta toteuma)
        tavoitehinta-ylitetty? (> toteuma oikaistu-tavoitehinta)
        kattohinta-ylitetty? (> toteuma oikaistu-kattohinta)
        tavoitehinnan-ylitys (- toteuma oikaistu-tavoitehinta)
        kattohinnan-ylitys (- toteuma oikaistu-kattohinta)
        tavoitehinnan-alitus (- oikaistu-tavoitehinta toteuma)
        tavoitehinnan-alitus-paatos (filtteroi-paatos-fn :tavoitehinnan-alitus)
        tavoitehinnan-ylitys-paatos (filtteroi-paatos-fn :tavoitehinnan-ylitys)
        tavoitehhinnan-ylitys-prosentit (paatoksen-maksu-prosentit tavoitehinnan-ylitys-paatos tavoitehinnan-ylitys)
        kattohinnan-ylitys-paatos (filtteroi-paatos-fn :kattohinnan-ylitys)
        kattohinnan-ylitys-prosentit (paatoksen-maksu-prosentit kattohinnan-ylitys-paatos kattohinnan-ylitys)
        lupaus-bonus-paatos (filtteroi-paatos-fn :lupaus-bonus)
        lupaus-sanktio-paatos (filtteroi-paatos-fn :lupaus-sanktio)
        valikatselmus-tekematta? (and
                                   (<= valittu-hoitokauden-alkuvuosi (pvm/vuosi (pvm/nyt)))
                                   (or (and tavoitehinta-alitettu? (nil? tavoitehinnan-alitus-paatos))
                                       (and tavoitehinta-ylitetty? (nil? tavoitehinnan-ylitys-paatos))
                                       (and kattohinta-ylitetty? (nil? kattohinnan-ylitys-paatos))))]
    [:div.yhteenveto.elevation-2
     [:h2 [:span "Yhteenveto"]]
     (when (and valikatselmus-tekematta? (not= :valikatselmus sivu))
       [:div.valikatselmus
        "Välikatselmus puuttuu"
        [napit/yleinen-ensisijainen
         "Tee välikatselmus"
         #(e! (kustannusten-seuranta-tiedot/->AvaaValikatselmusLomake))]])
     [:div.rivi [:span (if oikaisuja? "Alkuperäinen tavoitehinta" "Tavoitehinta")] [:span (fmt/desimaaliluku tavoitehinta)]]
     (when oikaisuja?
       [:<>
        [:div.rivi [:span "Tavoitehinnan oikaisu"] [:span (str (when (pos? (:b oikaisujen-summa)) "+") (fmt/desimaaliluku oikaisujen-summa))]]
        [:div.rivi [:span "Tavoitehinta"] [:span (fmt/desimaaliluku oikaistu-tavoitehinta)]]])
     [:div.rivi [:span "Kattohinta"] [:span (fmt/desimaaliluku oikaistu-kattohinta)]]
     [:div.rivi [:span "Toteuma"] [:span (fmt/desimaaliluku toteuma)]]
     [:hr]
     (when tavoitehinta-ylitetty?
       [:<>
        [:div.rivi
         [:span "Tavoitehinnan ylitys"]
         [:span.negatiivinen-numero
          (str "+ " (fmt/desimaaliluku tavoitehinnan-ylitys))]]
        (when tavoitehinnan-ylitys-paatos
          [:<>
           (when (pos? (::valikatselmus/urakoitsijan-maksu tavoitehinnan-ylitys-paatos))
             [:div.rivi-sisempi
              [:span "Urakoitsija maksaa " (fmt/desimaaliluku (:urakoitsija tavoitehhinnan-ylitys-prosentit)) "%"]
              [:span (fmt/desimaaliluku (::valikatselmus/urakoitsijan-maksu tavoitehinnan-ylitys-paatos))]])
           (when (pos? (::valikatselmus/tilaajan-maksu tavoitehinnan-ylitys-paatos))
             [:div.rivi-sisempi
              [:span "Tilaaja maksaa " (fmt/desimaaliluku (:tilaaja tavoitehhinnan-ylitys-prosentit)) "%"]
              [:span (fmt/desimaaliluku (::valikatselmus/tilaajan-maksu tavoitehinnan-ylitys-paatos))]])])])

     (when kattohinta-ylitetty?
       [:<>
        [:div.rivi
         [:span "Kattohinnan ylitys"]
         [:span.negatiivinen-numero
          (str "+ " (fmt/desimaaliluku kattohinnan-ylitys))]]
        (when kattohinnan-ylitys-paatos
          [:<>
           (when (pos? (::valikatselmus/urakoitsijan-maksu kattohinnan-ylitys-paatos))
             [:div.rivi-sisempi
              [:span "Urakoitsija maksaa " (fmt/desimaaliluku (:urakoitsija kattohinnan-ylitys-prosentit)) "%"]
              [:span (fmt/desimaaliluku (::valikatselmus/urakoitsijan-maksu kattohinnan-ylitys-paatos))]])
           (when (pos? (::valikatselmus/siirto kattohinnan-ylitys-paatos))
             [:div.rivi-sisempi
              [:span "Siirretään seuraavan vuoden kustannuksiin"]
              [:span (fmt/desimaaliluku (::valikatselmus/siirto kattohinnan-ylitys-paatos))]])])])

     (when tavoitehinta-alitettu?
       [:<>
        [:div.rivi
         [:span "Tavoitehinnan alitus"]
         [:span.positiivinen-numero
          (fmt/desimaaliluku tavoitehinnan-alitus)]]
        (when tavoitehinnan-alitus-paatos
          [:<>
           (when (pos? (::valikatselmus/siirto tavoitehinnan-alitus-paatos))
             [:div.rivi-sisempi
              [:span "Siirretään seuraavan vuoden lisäbudjetiksi"]
              [:span (fmt/desimaaliluku (::valikatselmus/siirto tavoitehinnan-alitus-paatos))]])
           (when (neg? (::valikatselmus/urakoitsijan-maksu tavoitehinnan-alitus-paatos))
             [:div.rivi-sisempi
              [:span "Maksetaan tavoitepalkkiona "]
              [:span (fmt/desimaaliluku (- (::valikatselmus/urakoitsijan-maksu tavoitehinnan-alitus-paatos)))]])

           (when (neg? (::valikatselmus/tilaajan-maksu tavoitehinnan-alitus-paatos))
             [:div.rivi-sisempi
              [:span "Säästö tilaajalle"]
              [:span (fmt/desimaaliluku (- (::valikatselmus/tilaajan-maksu tavoitehinnan-alitus-paatos)))]])])])

     (when (and (not (nil? (:lisatyot-summa data))) (not= 0 (:lisatyot-summa data)))
       [:div.rivi [:span "Lisätyöt"] [:span (fmt/desimaaliluku (:lisatyot-summa data))]])
     (when (and (not (nil? (:bonukset-toteutunut data))) (not= 0 (:bonukset-toteutunut data)))
       [:div.rivi [:span "Bonukset yms."] [:span (fmt/desimaaliluku (:bonukset-toteutunut data))]])
     (when lupaus-bonus-paatos
       [:div.rivi [:span "Lupauksien bonus"] [:span.positiivinen-numero (fmt/desimaaliluku (::valikatselmus/tilaajan-maksu lupaus-bonus-paatos))]])
     (when lupaus-sanktio-paatos
       [:div.rivi [:span "Lupauksien sanktio"] [:span.negatiivinen-numero (fmt/desimaaliluku (::valikatselmus/urakoitsijan-maksu lupaus-sanktio-paatos))]])
     (when (and (not valikatselmus-tekematta?) (not= :valikatselmus sivu))
       [:div.valikatselmus-tehty
        [napit/yleinen-ensisijainen "Avaa välikatselmus" #(e! (kustannusten-seuranta-tiedot/->AvaaValikatselmusLomake)) {:luokka "napiton-nappi tumma" :ikoni (ikonit/harja-icon-action-show)}]])]))