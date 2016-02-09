(ns harja.views.ilmoitukset
  "Harjan ilmoituksien pääsivu."
  (:require [reagent.core :refer [atom]]
            [clojure.string :refer [capitalize]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.tiedot.ilmoitukset :as tiedot]
            [harja.domain.ilmoitusapurit :refer [+ilmoitustyypit+ ilmoitustyypin-nimi ilmoitustyypin-lyhenne-ja-nimi
                                                 +ilmoitustilat+ nayta-henkilo parsi-puhelinnumero parsi-selitteet]]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.napit :refer [palvelinkutsu-nappi] :as napit]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.ui.lomake :as lomake]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka :as u]
            [harja.ui.bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.views.kartta :as kartta]
            [harja.views.ilmoituskuittaukset :as kuittaukset]
            [harja.ui.ikonit :as ikonit]))

(defn pollauksen-merkki []
  [yleiset/vihje "Ilmoituksia päivitetään automaattisesti" "inline-block"])


(defn nayta-tierekisteriosoite
  [tr]
  (if tr
    (str "Tie " (:numero tr) " / " (:alkuosa tr) " / " (:alkuetaisyys tr) " / " (:loppuosa tr) " / " (:loppuetaisyys tr))

    (str "Ei tierekisteriosoitetta")))

(defn ilmoituksen-tiedot []
  (let [ilmoitus @tiedot/valittu-ilmoitus]
    [:div
     [:span
      [napit/takaisin "Listaa ilmoitukset" #(tiedot/sulje-ilmoitus!)]
      (pollauksen-merkki)
      [bs/panel {}
       (ilmoitustyypin-nimi (:ilmoitustyyppi ilmoitus))
       [:span
        [yleiset/tietoja {}
         "Ilmoitettu: " (pvm/pvm-aika-sek (:ilmoitettu ilmoitus))
         "Sijainti: " (nayta-tierekisteriosoite (:tr ilmoitus))
         "Otsikko: " (:otsikko ilmoitus)
         "Lyhyt selite: " (:lyhytselite ilmoitus)
         "Pitkä selite: " (when (:pitkaselite ilmoitus)
                            [yleiset/pitka-teksti (:pitkaselite ilmoitus)])
         "Selitteet: " (parsi-selitteet (:selitteet ilmoitus))]

        [:br]
        [yleiset/tietoja {}
         "Ilmoittaja:" (let [henkilo (nayta-henkilo (:ilmoittaja ilmoitus))
                             tyyppi (capitalize (name (get-in ilmoitus [:ilmoittaja :tyyppi])))]
                         (if (and henkilo tyyppi)
                           (str henkilo ", " tyyppi)
                           (str (or henkilo tyyppi))))
         "Puhelinnumero: " (parsi-puhelinnumero (:ilmoittaja ilmoitus))
         "Sähköposti: " (get-in ilmoitus [:ilmoittaja :sahkoposti])]

        [:br]
        [yleiset/tietoja {}
         "Lähettäjä:" (nayta-henkilo (:lahettaja ilmoitus))
         "Puhelinnumero: " (parsi-puhelinnumero (:lahettaja ilmoitus))
         "Sähköposti: " (get-in ilmoitus [:lahettaja :sahkoposti])]]]

      [:div.kuittaukset
       [:h3 "Kuittaukset"]
       [:div
        (if @tiedot/uusi-kuittaus-auki?
          [kuittaukset/uusi-kuittaus-lomake]
          [:button.nappi-ensisijainen {:class    "uusi-kuittaus-nappi"
                                       :on-click #(tiedot/avaa-uusi-kuittaus!)} (ikonit/plus) " Uusi kuittaus"])

        (when-not (empty? (:kuittaukset ilmoitus))
          [:div
           (for [kuittaus (:kuittaukset ilmoitus)]
             (kuittaukset/kuittauksen-tiedot kuittaus))])]]]]))

(defn ilmoitusten-paanakyma
  []
  (tiedot/hae-ilmoitukset)
  (komp/luo
    (fn []
      [:span.ilmoitukset
       [lomake/lomake
        {:luokka   :horizontal
         :muokkaa! (fn [uusi]
                     (log "UUDET ILMOITUSVALINNAT: " (pr-str uusi))
                     (tiedot/hae-ilmoitukset)
                     (swap! tiedot/valinnat
                            (fn [vanha]
                              (if (not= (:hoitokausi vanha) (:hoitokausi uusi))
                                (assoc uusi
                                  :aikavali (:hoitokausi uusi))
                                uusi))))}

        [(when @nav/valittu-urakka
           {:nimi          :hoitokausi
            :palstoja      1
            :otsikko       "Hoitokausi"
            :tyyppi        :valinta
            :valinnat      @u/valitun-urakan-hoitokaudet
            :valinta-nayta fmt/pvm-vali-opt})

         (lomake/ryhma {:ulkoasu :rivi :palstoja 2}
                       {:nimi     :saapunut-alkaen
                        :hae      (comp first :aikavali)
                        :aseta    #(assoc-in %1 [:aikavali 0] %2)
                        :otsikko  "Alkaen"
                        :palstoja 1
                        :tyyppi   :pvm}

                       {:nimi     :saapunut-paattyen
                        :otsikko  "Päättyen"
                        :palstoja 1
                        :hae      (comp second :aikavali)
                        :aseta    #(assoc-in %1 [:aikavali 1] %2)
                        :tyyppi   :pvm})

         {:nimi        :hakuehto :otsikko "Hakusana"
          :placeholder "Hae tekstillä..."
          :tyyppi      :string
          :pituus-max  64
          :palstoja    2}

         (lomake/ryhma {:ulkoasu :rivi}
                       {:nimi        :tilat :otsikko "Tila"
                        :tyyppi      :boolean-group
                        :vaihtoehdot [:suljetut :avoimet]}

                       {:nimi             :tyypit :otsikko "Tyyppi"
                        :tyyppi           :boolean-group
                        :vaihtoehdot      [:toimenpidepyynto :tiedoitus :kysely]
                        :vaihtoehto-nayta ilmoitustyypin-lyhenne-ja-nimi})]

        @tiedot/valinnat]

       [:div
        (pollauksen-merkki)
        [grid
         {:tyhja             (if @tiedot/haetut-ilmoitukset "Ei löytyneitä tietoja" [ajax-loader "Haetaan ilmoutuksia"])
          :rivi-klikattu     #(tiedot/avaa-ilmoitus! %)
          :piilota-toiminnot true}

         [{:otsikko "Ilmoitettu" :nimi :ilmoitettu :hae (comp pvm/pvm-aika :ilmoitettu) :leveys "15%"}
          {:otsikko "Tyyppi" :nimi :ilmoitustyyppi :hae #(ilmoitustyypin-nimi (:ilmoitustyyppi %)) :leveys "15%"}
          {:otsikko "Sijainti" :nimi :tierekisteri :hae #(nayta-tierekisteriosoite (:tr %)) :leveys "15%"}
          {:otsikko "Selitteet" :nimi :selitteet :hae #(parsi-selitteet (:selitteet %)) :leveys "15%"}
          {:otsikko "Viimeisin kuittaus" :nimi :uusinkuittaus :hae #(if (:uusinkuittaus %) (pvm/pvm-aika (:uusinkuittaus %)) "-") :leveys "15%"}
          {:otsikko "Vast." :tyyppi :boolean :nimi :suljettu :leveys "10%"}]

         @tiedot/haetut-ilmoitukset]]])))

(defn ilmoitukset []
  (komp/luo
    (komp/sisaan-ulos #(do
                        (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                        (nav/vaihda-kartan-koko! :L))
                      #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
    (komp/ulos (kartta/kuuntele-valittua! tiedot/valittu-ilmoitus))
    (komp/kuuntelija :ilmoitus-klikattu #(tiedot/avaa-ilmoitus! %2))
    (komp/lippu tiedot/ilmoitusnakymassa? tiedot/karttataso-ilmoitukset)
    (komp/ulos (paivita-periodisesti tiedot/haetut-ilmoitukset 60000)) ;1min

    (fn []
      [:span
       [kartta/kartan-paikka]
       (if @tiedot/valittu-ilmoitus
         [ilmoituksen-tiedot]
         [ilmoitusten-paanakyma])])))
