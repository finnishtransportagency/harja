(ns harja.ui.gps
  "GPS-sijainnin karttavalintaan liittyvät komponentit."
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.kartta :as kartta]
            [harja.views.kartta.tasot :as karttatasot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tyokalut.vkm :as vkm]
            [harja.tiedot.tierekisteri :as tierekisteri]
            [cljs.core.async :refer [>! <! alts! chan] :as async]
            [harja.geo :as geo]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit])
  (:require-macros
   [reagent.ratom :refer [reaction run!]]
   [harja.makrot :refer [nappaa-virhe with-loop-from-channel with-items-from-channel]]
   [cljs.core.async.macros :refer [go go-loop]]))

(defn karttavalitsin
  "Komponentti GPS-pisteen valitsemiseen kartalta.
   Asettaa kartan näkyviin, jos se ei ole jo näkyvissä, ja keskittää sen
   löytyneeseen pisteeseen.

   Optiot on mäppi parametreja, jossa seuraavat avaimet:

   :kun-valmis  Funktio, jota kutsutaan viimeisenä kun käyttäjän valinta on valmis.
   :kun-peruttu Funktio, jota kutsutaan, jos käyttäjä haluaa perua karttavalinnan."
  [optiot]
  (let [tapahtumat (chan)
        vkm-haku (chan)
        tila (atom :ei-valittu)
        alkupiste (atom nil)
        tr-osoite (atom {})
        optiot (cljs.core/atom optiot)
        valinta-peruttu (fn [_]
                          ((:kun-peruttu @optiot))
                          (poistu-tr-valinnasta!))
        valinta-hyvaksytty #(go (>! tapahtumat {:tyyppi :enter}))]

    (with-items-from-channel [{:keys [tyyppi sijainti x y]} tapahtumat]
                             (case tyyppi
                               ;; Hiirtä liikutellaan kartan yllä, aseta tilan mukainen tooltip
                               :hover
                               (kartta/aseta-tooltip! x y
                                                      (luo-tooltip (case @tila
                                                                     :ei-valittu "Klikkaa alkupiste"
                                                                     :alku-valittu "Klikkaa loppupiste tai hyväksy pistemäinen painamalla Enter")))

                               ;; Enter näppäimellä voi hyväksyä pistemäisen osoitteen
                               :enter
                               (when (= @tila :alku-valittu)
                                 ((:kun-valmis @optiot) @tr-osoite)
                                 (poistu-tr-valinnasta!))

                               :click
                               (if (= :alku-valittu @tila)
                                 (do
                                   (kartta/aseta-kursori! :progress)
                                   (>! vkm-haku (<! (vkm/koordinaatti->trosoite-kahdella @alkupiste sijainti))))
                                 (do
                                   (reset! alkupiste sijainti)
                                   (kartta/aseta-kursori! :progress)
                                   (>! vkm-haku (<! (vkm/koordinaatti->trosoite sijainti)))))))

    (with-loop-from-channel vkm-haku osoite
                            (kartta/aseta-kursori! :crosshair)
                            (if (vkm/virhe? osoite)
                              (pisteelle-ei-loydy-tieta-ilmoitus!)
                              (let [{:keys [kun-valmis paivita]} @optiot]
                                (kartta/tyhjenna-ohjelaatikko!)
                                (case @tila
                                  :ei-valittu
                                  (let [osoite (reset! tr-osoite (konvertoi-pistemaiseksi-tr-osoitteeksi osoite))]
                                    (paivita osoite)
                                    (karttatasot/taso-paalle! :tr-valitsin)
                                    (reset! tila :alku-valittu)
                                    (reset! tierekisteri/valittu-alkupiste (:geometria osoite))
                                    (nayta-alkupiste-ohjelaatikossa! osoite))

                                  :alku-valittu
                                  (let [osoite (reset! tr-osoite (konvertoi-tr-osoitteeksi osoite))]
                                    (poistu-tr-valinnasta!)
                                    (kun-valmis osoite))))))

    (let [kartan-koko @nav/kartan-koko]
      (komp/luo
        {:component-will-receive-props
         (fn [_ _ uudet-optiot]
           (reset! optiot uudet-optiot))}

        (komp/karttakontrollit
          :tr-karttavalitsin
          (with-meta [tr-kontrollit valinta-peruttu valinta-hyvaksytty tila]
                     {:class "kartan-tr-kontrollit"}))

        (komp/sisaan-ulos #(do
                             (log "TR karttavalitsin - sisään!")
                             (reset! kartta/pida-geometriat-nakyvilla? false) ; Emme halua, että zoom-taso muuttuu kun TR:ää valitaan
                             (reset! nav/kartan-edellinen-koko kartan-koko)
                             (when-not (= :XL kartan-koko) ;;ei syytä pienentää karttaa
                               (nav/vaihda-kartan-koko! :L))
                             (kartta/aseta-kursori! :crosshair)
                             (nayta-ohjeet-ohjelaatikossa!))
                          #(do
                             (log "TR karttavalitsin - ulos!")
                             (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)
                             (reset! nav/kartan-edellinen-koko nil)
                             (poistu-tr-valinnasta!)
                             (kartta/aseta-kursori! nil)))
        (komp/ulos (kartta/kaappaa-hiiri tapahtumat))
        (komp/kuuntelija :esc-painettu
                         valinta-peruttu
                         :enter-painettu
                         valinta-hyvaksytty)
        (fn [_]                                             ;; suljetaan kun-peruttu ja kun-valittu yli
          [:div.tr-valitsin-teksti
           [:div (ikonit/livicon-info-sign) (case @tila
                                              :ei-valittu " Valitse alkupiste kartalta"
                                              :alku-valittu " Valitse loppupiste kartalta"
                                              "")]])))))
