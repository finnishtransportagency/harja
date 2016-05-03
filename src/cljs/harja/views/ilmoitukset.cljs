(ns harja.views.ilmoitukset
  "Harjan ilmoituksien pääsivu."
  (:require [reagent.core :refer [atom]]
            [clojure.string :refer [capitalize]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.tiedot.ilmoitukset :as tiedot]
            [harja.domain.ilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-nimi ilmoitustyypin-lyhenne-ja-nimi
                                                 +ilmoitustilat+ nayta-henkilo parsi-puhelinnumero
                                                 +ilmoitusten-selitteet+ parsi-selitteet kuittaustyypit
                                                 kuittaustyypin-selite nayta-tierekisteriosoite]]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.napit :refer [palvelinkutsu-nappi] :as napit]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.ui.lomake :as lomake]
            [harja.ui.protokollat :as protokollat]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka :as u]
            [harja.ui.bootstrap :as bs]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.views.kartta :as kartta]
            [harja.views.ilmoituskuittaukset :as kuittaukset]
            [harja.views.ilmoituksen-tiedot :as it]
            [harja.ui.ikonit :as ikonit])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn pollauksen-merkki []
  [yleiset/vihje "Ilmoituksia päivitetään automaattisesti" "inline-block"])

(defn ilmoituksen-tiedot []
  (let [ilmoitus @tiedot/valittu-ilmoitus]
    [:div
     [:span
      [napit/takaisin "Listaa ilmoitukset" #(tiedot/sulje-ilmoitus!)]
      (pollauksen-merkki)
      [it/ilmoitus ilmoitus]]]))

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

         (lomake/ryhma
           {:ulkoasu  :rivi
            :palstoja 2}
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
          :palstoja    1}
         {:nimi                  :selite
          :palstoja              1
          :otsikko               "Selite"
          :placeholder           "Hae ja valitse selite"
          :tyyppi                :haku
          :hae-kun-yli-n-merkkia 0
          :nayta                 second :fmt second
          :lahde                 (reify protokollat/Haku
                                   (hae [_ teksti]
                                     (go (let [haku second
                                               selitteet +ilmoitusten-selitteet+
                                               itemit (if (< (count teksti) 1)
                                                        selitteet
                                                        (filter #(not= (.indexOf (.toLowerCase (haku %)) (.toLowerCase teksti)) -1)
                                                                selitteet))]
                                           (vec (sort itemit))))))}
         (lomake/ryhma
           {:rivi? true}
           {:nimi             :kuittaustyypit
            :otsikko          "Tila"
            :tyyppi           :checkbox-group
            :vaihtoehdot      tiedot/kuittaustyyppi-filtterit
            :vaihtoehto-nayta kuittaustyypin-selite}
           {:nimi             :tyypit
            :otsikko          "Tyyppi"
            :tyyppi           :checkbox-group
            :vaihtoehdot      [:toimenpidepyynto :tiedoitus :kysely]
            :vaihtoehto-nayta ilmoitustyypin-lyhenne-ja-nimi}
           {:nimi             :vain-myohassa?
            :otsikko          "Kuittaukset"
            :tyyppi           :checkbox
            :teksti           "Näytä ainoastaan myöhästyneet"}
           {:nimi             :aloituskuittauksen-ajankohta
            :otsikko          "Aloituskuittaus annettu"
            :tyyppi           :radio-group
            :vaihtoehdot      [:kaikki :alle-tunti :myohemmin]
            :vaihtoehto-nayta (fn [arvo]
                                ({:kaikki     "Kaikki"
                                  :alle-tunti "Alle tunnin kuluessa"
                                  :myohemmin  "Yli tunnin päästä"}
                                  arvo))})]
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
          {:otsikko "Tila" :nimi :tila :leveys "10%" :hae #(kuittaustyypin-selite (:tila %))}]
         @tiedot/haetut-ilmoitukset]]])))

(defn ilmoitukset []
  (komp/luo
    (komp/sisaan-ulos #(do
                        (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                        (nav/vaihda-kartan-koko! :M))
                      #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
    (komp/ulos (kartta/kuuntele-valittua! tiedot/valittu-ilmoitus))
    (komp/kuuntelija :ilmoitus-klikattu #(tiedot/avaa-ilmoitus! %2))
    (komp/lippu tiedot/ilmoitusnakymassa? tiedot/karttataso-ilmoitukset istunto/ajastin-taukotilassa?)
    (komp/ulos (paivita-periodisesti tiedot/haetut-ilmoitukset 60000)) ;1min

    (fn []
      [:span
       [kartta/kartan-paikka]
       (if @tiedot/valittu-ilmoitus
         [ilmoituksen-tiedot]
         [ilmoitusten-paanakyma])])))
