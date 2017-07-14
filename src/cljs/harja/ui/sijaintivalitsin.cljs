(ns harja.ui.sijaintivalitsin
  "GPS-sijainnin karttavalintaan liittyvät komponentit."
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.kartta :as kartta]
            [harja.views.kartta.tasot :as karttatasot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.sijaintivalitsin :as sijaintivalitsin]
            [harja.tyokalut.vkm :as vkm]
            [cljs.core.async :refer [>! <! alts! chan] :as async]
            [harja.geo :as geo]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit])
  (:require-macros
    [reagent.ratom :refer [reaction run!]]
    [harja.makrot :refer [nappaa-virhe with-loop-from-channel with-items-from-channel]]
    [cljs.core.async.macros :refer [go go-loop]]))

(defn nayta-ohjeet-ohjelaatikossa! []
  (kartta/aseta-ohjelaatikon-sisalto! [:span.karttavalitsin-ohje
                                       "Valitse piste kartalta."]))

(defn valintakontrollit [peruttu]
  [:div
   [napit/peruuta "Peruuta" peruttu]])

(defn sijaintivalitsin
  "Komponentti sijainnin valitsemiseen kartalta.
   Asettaa kartan näkyviin, jos se ei ole jo näkyvissä, ja keskittää sen
   löytyneeseen pisteeseen.

   Optiot on mäppi parametreja, jossa seuraavat avaimet:

   :kun-valmis  Funktio, jota kutsutaan viimeisenä kun käyttäjän valinta on valmis.
   :kun-peruttu Funktio, jota kutsutaan, jos käyttäjä haluaa perua karttavalinnan."
  [{:keys [kun-valmis kun-peruttu]}]
  (let [tapahtumat (chan)
        valinta-peruttu (fn [_]
                          (kun-peruttu))]

    ;; Kuunnellaan kartan viestejä
    (with-items-from-channel [{:keys [tyyppi sijainti x y] :as viesti} tapahtumat]
                             (when (= tyyppi :click)
                               (reset! sijaintivalitsin/valittu-sijainti
                                       {:sijainti
                                        {:type :point :coordinates sijainti}})
                               (kun-valmis sijainti)))

    (let [kartan-koko @nav/kartan-koko]
      (komp/luo
        (komp/karttakontrollit
          :sijaintivalintakontrollit
          (with-meta [valintakontrollit valinta-peruttu]
                     {:class "kartan-sijaintivalintakontrollit"}))

        (komp/sisaan-ulos #(do
                             (reset! kartta/pida-geometriat-nakyvilla? false) ; Emme halua, että zoom-taso muuttuu kun TR:ää valitaan
                             (reset! nav/kartan-edellinen-koko kartan-koko)
                             (when-not (= :XL kartan-koko) ; ;ei syytä pienentää karttaa
                               (nav/vaihda-kartan-koko! :L))
                             (nayta-ohjeet-ohjelaatikossa!)
                             (kartta/aseta-kursori! :crosshair))
                          #(do
                             (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)
                             (reset! nav/kartan-edellinen-koko nil)
                             (kartta/tyhjenna-ohjelaatikko!)
                             (kartta/aseta-kursori! nil)))
        (komp/ulos (kartta/kaappaa-hiiri tapahtumat))
        (komp/kuuntelija :esc-painettu
                         valinta-peruttu)
        (fn [_]
          [:div.inline-block.karttasijaintivalitsin-teksti
           [ikonit/ikoni-ja-teksti (ikonit/livicon-info-sign) "Valitse sijainti kartalta"]])))))
