(ns harja.views.ilmoitukset
  "Harjan ilmoituksien pääsivu."
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :refer [capitalize]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.tiedot.ilmoitukset :as tiedot]
            [harja.domain.ilmoitukset :refer
             [kuittausvaatimukset-str +ilmoitustyypit+ ilmoitustyypin-nimi
              ilmoitustyypin-lyhenne ilmoitustyypin-lyhenne-ja-nimi
              +ilmoitustilat+ nayta-henkilo parsi-puhelinnumero
              +ilmoitusten-selitteet+ parsi-selitteet kuittaustyypit
              kuittaustyypin-selite kuittaustyypin-lyhenne]]
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
            [harja.ui.ikonit :as ikonit]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.ui.valinnat :as valinnat]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.tiedot.ilmoitukset.viestit :as v])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def selitehaku
  (reify protokollat/Haku
    (hae [_ teksti]
      (go (let [haku second
                selitteet +ilmoitusten-selitteet+
                itemit (if (< (count teksti) 1)
                         selitteet
                         (filter #(not= (.indexOf (.toLowerCase (haku %))
                                                  (.toLowerCase teksti)) -1)
                                 selitteet))]
            (vec (sort itemit)))))))

(defn pollauksen-merkki []
  [yleiset/vihje "Ilmoituksia päivitetään automaattisesti" "inline-block"])

(defn yhdeydenottopyynnot-lihavoitu []
  [yleiset/vihje "Yhdeydenottopyynnöt lihavoitu" "inline-block bold"])

(defn virkaapupyynnot-korostettu []
  [:span.selite-virkaapu [ikonit/livicon-warning-sign] "Virka-apupyynnöt korostettu"])

(defn ilmoituksen-tiedot [e! ilmoitus]
  [:div
   [:span
    [napit/takaisin "Listaa ilmoitukset" #(e! (v/->PoistaIlmoitusValinta))]
    (pollauksen-merkki)
    [it/ilmoitus ilmoitus]]])

(defn kuittauslista [{kuittaukset :kuittaukset}]
  [:div.kuittauslista
   (map-indexed
    (fn [i {:keys [kuitattu kuittaustyyppi kuittaaja]}]
      ^{:key i}
      [yleiset/tooltip {}
       [:div.kuittaus {:class (name kuittaustyyppi)}
        (kuittaustyypin-lyhenne kuittaustyyppi)]
       [:div
        (kuittaustyypin-selite kuittaustyyppi)
        [:br]
        (pvm/pvm-aika kuitattu)
        [:br] (:etunimi kuittaaja) " " (:sukunimi kuittaaja)]])
    kuittaukset)])

(defn ilmoitusten-hakuehdot [e! {:keys [aikavali] :as valinnat-nyt}]
  [lomake/lomake
   {:luokka   :horizontal
    :muokkaa! #(e! (v/->AsetaValinnat %))}

   [#_(when @nav/valittu-urakka
      {:nimi          :hoitokausi
       :palstoja      1
       :otsikko       "Hoitokausi"
       :tyyppi        :valinta
       :valinnat      @u/valitun-urakan-hoitokaudet
       :valinta-nayta fmt/pvm-vali-opt})

    {:nimi :aikavali
     :otsikko "Saapunut aikavälillä"
     :tyyppi :komponentti
     :komponentti (fn [{muokkaa! :muokkaa-lomaketta}]
                    [valinnat/aikavali
                     (r/wrap aikavali
                             #(muokkaa! (merge valinnat-nyt
                                               {:aikavali %})))
                     {:lomake? true}])}


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
     :lahde selitehaku}
    {:nimi :tr-numero
     :palstoja 1
     :otsikko "Tienumero"
     :placeholder "Rajaa tienumerolla"
     :tyyppi :positiivinen-numero :kokonaisluku? true}

    (lomake/ryhma
     {:rivi? true}
     {:nimi :ilmoittaja-nimi
      :palstoja 1
      :otsikko "Ilmoittajan nimi"
      :placeholder "Rajaa ilmoittajan nimellä"
      :tyyppi :string}
     {:nimi :ilmoittaja-puhelin
      :palstoja 1
      :otsikko "Ilmoittajan puhelinnumero"
      :placeholder "Rajaa ilmoittajan puhelinnumerolla"
      :tyyppi :puhelin})

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
     {:nimi    :vain-myohassa?
      :otsikko "Kuittaukset"
      :tyyppi  :checkbox
      :teksti  "Näytä ainoastaan myöhästyneet"
      :vihje   kuittausvaatimukset-str}
     {:nimi             :aloituskuittauksen-ajankohta
      :otsikko          "Aloituskuittaus annettu"
      :tyyppi           :radio-group
      :vaihtoehdot      [:kaikki :alle-tunti :myohemmin]
      :vaihtoehto-nayta (fn [arvo]
                          ({:kaikki     "Älä rajoita aloituskuittauksella"
                            :alle-tunti "Alle tunnin kuluessa"
                            :myohemmin  "Yli tunnin päästä"}
                           arvo))})]
   valinnat-nyt])

(defn ilmoitusten-paanakyma
  [e! ilmoitukset]

  ;; Kun näkymään tullaan, yhdistetään navigaatiosta tulevat valinnat
  (e! (v/->YhdistaValinnat @tiedot/valinnat))

  (komp/luo
   ;; Kun jokin navigaation valinnoista muuttuu, yhdistetään ne valintoihin
   (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                   (e! (v/->YhdistaValinnat uusi))))
   (fn [e! {valinnat-nyt :valinnat
            kuittaa-monta :kuittaa-monta
            haetut-ilmoitukset :ilmoitukset :as ilmoitukset}]
     (let [{valitut-ilmoitukset :ilmoitukset :as kuittaa-monta-nyt} kuittaa-monta
           valitse-ilmoitus! (when kuittaa-monta-nyt
                               #(e! (v/->ValitseKuitattavaIlmoitus %)))]
       [:span.ilmoitukset

        [ilmoitusten-hakuehdot e! valinnat-nyt]
        [:div
         [pollauksen-merkki]
         [yhdeydenottopyynnot-lihavoitu]
         [virkaapupyynnot-korostettu]

         (when-not kuittaa-monta-nyt
           [napit/yleinen "Kuittaa monta ilmoitusta" #(e! (v/->AloitaMonenKuittaus))
            {:luokka "pull-right kuittaa-monta"}])

         (when kuittaa-monta-nyt
           [kuittaukset/kuittaa-monta-lomake e! kuittaa-monta])

         [grid
          {:tyhja (if haetut-ilmoitukset
                    "Ei löytyneitä tietoja"
                    [ajax-loader "Haetaan ilmoutuksia"])
           :rivi-klikattu (or valitse-ilmoitus!
                              #(e! (v/->ValitseIlmoitus %)))
           :piilota-toiminnot true}

          [(when kuittaa-monta-nyt
             {:otsikko " "
              :tasaa :keskita
              :tyyppi :komponentti
              :komponentti (fn [rivi]
                             [:input {:type "checkbox"
                                      :checked (valitut-ilmoitukset rivi)}])
              :leveys 1})
           {:otsikko "Urakka" :nimi :urakkanimi :leveys 7
            :hae (comp fmt/lyhennetty-urakan-nimi :urakkanimi)}
           {:otsikko "Ilmoitettu" :nimi :ilmoitettu
            :hae (comp pvm/pvm-aika :ilmoitettu) :leveys 6}
           {:otsikko "Tyyppi" :nimi :ilmoitustyyppi
            :hae #(ilmoitustyypin-lyhenne (:ilmoitustyyppi %))
            :leveys 2}
           {:otsikko "Sijainti" :nimi :tierekisteri
            :hae #(tr-domain/tierekisteriosoite-tekstina (:tr %))
            :leveys 7}

           {:otsikko "Selitteet" :nimi :selitteet
            :tyyppi :komponentti
            :komponentti it/selitelista
            :leveys 6}
           {:otsikko "Kuittaukset" :nimi :kuittaukset
            :tyyppi :komponentti
            :komponentti kuittauslista
            :leveys 6}

           {:otsikko "Tila" :nimi :tila :leveys 7 :hae #(kuittaustyypin-selite (:tila %))}]
          (mapv #(if (:yhteydenottopyynto %)
                   (assoc % :lihavoi true)
                   %)
                haetut-ilmoitukset)]]]))))

(defn- ilmoitukset* [e! ilmoitukset]
  (komp/luo
   (komp/kuuntelija :ilmoitus-klikattu (fn [_ i] (e! (v/->ValitseIlmoitus i))))

   #_(komp/lippu tiedot/ilmoitusnakymassa?
                 tiedot/karttataso-ilmoitukset
                 istunto/ajastin-taukotilassa?)

   ;(komp/ulos (paivita-periodisesti tiedot/haetut-ilmoitukset 60000)) ;1min

   (fn [e! {valittu-ilmoitus :valittu-ilmoitus :as ilmoitukset}]
     [:span
      [kartta/kartan-paikka]
      (if valittu-ilmoitus
        [ilmoituksen-tiedot e! valittu-ilmoitus]
        [ilmoitusten-paanakyma e! ilmoitukset])])))

(defn ilmoitukset []
  (komp/luo
   (komp/sisaan-ulos #(do
                        (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                        (nav/vaihda-kartan-koko! :M))
                     #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
   (komp/lippu tiedot/karttataso-ilmoitukset)
    ;;(komp/ulos (kartta/kuuntele-valittua! tiedot/valittu-ilmoitus))

    (fn []
      [tuck tiedot/ilmoitukset ilmoitukset*])))
