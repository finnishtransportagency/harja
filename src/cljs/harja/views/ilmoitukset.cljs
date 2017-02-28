(ns harja.views.ilmoitukset
  "Harjan ilmoituksien pääsivu."
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :refer [capitalize]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.tiedot.ilmoitukset :as tiedot]
            [harja.tiedot.ilmoitukset.tietyot :as tietyoilmoitukset-tiedot]
            [harja.domain.ilmoitukset :refer
             [kuittausvaatimukset-str +ilmoitustyypit+ ilmoitustyypin-nimi
              ilmoitustyypin-lyhenne ilmoitustyypin-lyhenne-ja-nimi
              +ilmoitustilat+ nayta-henkilo parsi-puhelinnumero
              +ilmoitusten-selitteet+ parsi-selitteet kuittaustyypit
              kuittaustyypin-selite kuittaustyypin-lyhenne
              tilan-selite] :as domain]
            [harja.ui.bootstrap :as bs]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.napit :refer [palvelinkutsu-nappi] :as napit]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.ui.lomake :as lomake]
            [harja.ui.protokollat :as protokollat]
            [harja.fmt :as fmt]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.views.kartta :as kartta]
            [harja.views.ilmoituskuittaukset :as kuittaukset]
            [harja.views.ilmoituksen-tiedot :as it]
            [harja.views.ilmoitukset.tietyot :as tietyoilmoitukset-view]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.notifikaatiot :as notifikaatiot]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.tiedot.ilmoitukset.viestit :as v]
            [harja.ui.kentat :as kentat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.kartta :as kartta-tiedot])
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
    [it/ilmoitus e! ilmoitus]]])

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
     (remove domain/valitysviesti? kuittaukset))])

(defn aikavalivalitsin [valinnat-nyt]
  (let [vapaa-aikavali? (get-in valinnat-nyt [:vakioaikavali :vapaa-aikavali])
        alkuaika (:alkuaika valinnat-nyt)
        vakio-aikavalikentta {:nimi :vakioaikavali
                              :otsikko "Ilmoitettu aikavälillä"
                              :fmt :nimi
                              :tyyppi :valinta
                              :valinnat tiedot/aikavalit
                              :valinta-nayta :nimi}
        alkuaikakentta {:nimi :alkuaika
                        :otsikko "Alku"
                        :tyyppi :pvm-aika
                        :validoi [[:ei-tyhja "Anna alkuaika"]]}
        loppuaikakentta {:nimi :loppuaika
                         :otsikko "Loppu"
                         :tyyppi :pvm-aika
                         :validoi [[:ei-tyhja "Anna loppuaika"]
                                   [:pvm-toisen-pvmn-jalkeen alkuaika "Loppuajan on oltava alkuajan jälkeen"]]}]

    (if vapaa-aikavali?
      (lomake/ryhma
        {:rivi? true}
        vakio-aikavalikentta
        alkuaikakentta
        loppuaikakentta)
      (lomake/ryhma
        {:rivi? true}
        vakio-aikavalikentta))))

(defn ilmoitusten-hakuehdot [e! {:keys [aikavali urakka valitun-urakan-hoitokaudet] :as valinnat-nyt}]
  [lomake/lomake
   {:luokka :horizontal
    :muokkaa! #(e! (v/->AsetaValinnat %))}

   [(aikavalivalitsin valinnat-nyt)
    {:nimi :hakuehto :otsikko "Hakusana"
     :placeholder "Hae tekstillä..."
     :tyyppi :string
     :pituus-max 64
     :palstoja 1}
    {:nimi :selite
     :palstoja 1
     :otsikko "Selite"
     :placeholder "Hae ja valitse selite"
     :tyyppi :haku
     :hae-kun-yli-n-merkkia 0
     :nayta second :fmt second
     :lahde selitehaku}
    {:nimi :tr-numero
     :palstoja 1
     :otsikko "Tienumero"
     :placeholder "Rajaa tienumerolla"
     :tyyppi :positiivinen-numero :kokonaisluku? true}
    {:nimi :tunniste
     :palstoja 1
     :otsikko "Tunniste"
     :placeholder "Rajaa tunnisteella"
     :tyyppi :string}

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
      {:nimi :tilat
       :otsikko "Tila"
       :tyyppi :checkbox-group
       :vaihtoehdot tiedot/tila-filtterit
       :vaihtoehto-nayta tilan-selite}
      {:nimi :tyypit
       :otsikko "Tyyppi"
       :tyyppi :checkbox-group
       :vaihtoehdot [:toimenpidepyynto :tiedoitus :kysely]
       :vaihtoehto-nayta ilmoitustyypin-lyhenne-ja-nimi}
      {:nimi :vain-myohassa?
       :otsikko "Kuittaukset"
       :tyyppi :checkbox
       :teksti "Näytä ainoastaan myöhästyneet"
       :vihje kuittausvaatimukset-str}
      {:nimi :aloituskuittauksen-ajankohta
       :otsikko "Aloituskuittaus annettu"
       :tyyppi :radio-group
       :vaihtoehdot [:kaikki :alle-tunti :myohemmin]
       :vaihtoehto-nayta (fn [arvo]
                           ({:kaikki "Älä rajoita aloituskuittauksella"
                             :alle-tunti "Alle tunnin kuluessa"
                             :myohemmin "Yli tunnin päästä"}
                             arvo))})]
   valinnat-nyt])

(defn leikkaa-sisalto-pituuteen [pituus sisalto]
  (if (> (count sisalto) pituus)
    (str (fmt/leikkaa-merkkijono pituus sisalto) "...")
    sisalto))

(defn ilmoitustyypin-selite [ilmoitustyyppi]
  (let [tyyppi (domain/ilmoitustyypin-lyhenne ilmoitustyyppi)]
    [:div {:class tyyppi} tyyppi]))

(defn ilmoitusten-paanakyma
  [e! {valinnat-nyt :valinnat
       kuittaa-monta :kuittaa-monta
       haetut-ilmoitukset :ilmoitukset
       ilmoituksen-haku-kaynnissa? :ilmoituksen-haku-kaynnissa? :as ilmoitukset}]

  (let [{valitut-ilmoitukset :ilmoitukset :as kuittaa-monta-nyt} kuittaa-monta
        valitse-ilmoitus! (when kuittaa-monta-nyt
                            #(e! (v/->ValitseKuitattavaIlmoitus %)))]
    [:span.ilmoitukset

     [ilmoitusten-hakuehdot e! valinnat-nyt]
     [:div
      [kentat/tee-kentta {:tyyppi :checkbox
                          :teksti "Äänimerkki uusista ilmoituksista"}
       tiedot/aanimerkki-uusista-ilmoituksista?]

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
                 [ajax-loader "Haetaan ilmoituksia"])
        :rivi-klikattu (when-not ilmoituksen-haku-kaynnissa?
                         (or valitse-ilmoitus!
                             #(e! (v/->ValitseIlmoitus %))))
        :piilota-toiminnot true
        :max-rivimaara 500
        :max-rivimaaran-ylitys-viesti "Yli 500 ilmoitusta. Tarkenna hakuehtoja."}

       [(when kuittaa-monta-nyt
          {:otsikko " "
           :tasaa :keskita
           :tyyppi :komponentti
           :komponentti (fn [rivi]
                          (let [liidosta-tullut? (not (:ilmoitusid rivi))
                                kirjoitusoikeus? (oikeudet/voi-kirjoittaa? oikeudet/ilmoitukset-ilmoitukset
                                                                           (:urakka rivi))]
                            [:span (when liidosta-tullut?
                                     {:title tiedot/vihje-liito})
                             [:input {:type "checkbox"
                                      :disabled (or liidosta-tullut?
                                                    (not kirjoitusoikeus?))
                                      :checked (valitut-ilmoitukset rivi)}]]))
           :leveys 1})
        {:otsikko "Urakka" :nimi :urakkanimi :leveys 7
         :hae (comp fmt/lyhennetty-urakan-nimi :urakkanimi)}
        {:otsikko "Id" :nimi :ilmoitusid :leveys 3}
        {:otsikko "Tunniste" :nimi :tunniste :leveys 3}
        {:otsikko "Otsikko" :nimi :otsikko :leveys 7
         :hae #(leikkaa-sisalto-pituuteen 30 (:otsikko %))}
        {:otsikko "Lisätietoja" :nimi :lisatieto :leveys 7
         :hae #(leikkaa-sisalto-pituuteen 30 (:lisatieto %))}
        {:otsikko "Ilmoitettu" :nimi :ilmoitettu
         :hae (comp pvm/pvm-aika :ilmoitettu) :leveys 6}
        {:otsikko "Tyyppi" :nimi :ilmoitustyyppi
         :tyyppi :komponentti
         :komponentti #(ilmoitustyypin-selite (:ilmoitustyyppi %))
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

        {:otsikko "Tila" :nimi :tila :leveys 7 :hae #(tilan-selite (:tila %))}]
       (mapv #(if (:yhteydenottopyynto %)
                (assoc % :lihavoi true)
                %)
             haetut-ilmoitukset)]]]))

(defn- ilmoitukset* [e! ilmoitukset]
  ;; Kun näkymään tullaan, yhdistetään navigaatiosta tulevat valinnat
  (e! (v/->YhdistaValinnat @tiedot/valinnat))

  (komp/luo
    (komp/lippu tiedot/karttataso-ilmoitukset)
    (komp/kuuntelija :ilmoitus-klikattu (fn [_ i] (e! (v/->ValitseIlmoitus i))))
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (v/->YhdistaValinnat uusi))))
    (komp/sisaan-ulos #(do
                         (notifikaatiot/pyyda-notifikaatiolupa)
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (nav/vaihda-kartan-koko! :M)
                         (kartta-tiedot/kasittele-infopaneelin-linkit!
                           {:ilmoitus {:toiminto (fn [ilmoitus-infopaneelista]
                                                   (e! (v/->ValitseIlmoitus ilmoitus-infopaneelista)))
                                       :teksti "Valitse ilmoitus"}}))
                      #(do
                         (kartta-tiedot/kasittele-infopaneelin-linkit! nil)
                         (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)))
    (fn [e! {valittu-ilmoitus :valittu-ilmoitus :as ilmoitukset}]
      [:span
       [kartta/kartan-paikka]
       (if valittu-ilmoitus
         [ilmoituksen-tiedot e! valittu-ilmoitus]
         [ilmoitusten-paanakyma e! ilmoitukset])])))

(defn ilmoitukset []
  (fn []
    (if-not (istunto/ominaisuus-kaytossa? :tietyoilmoitukset)
      [tuck tiedot/ilmoitukset ilmoitukset*]
      ;; else
      [bs/tabs {:style :tabs :classes "tabs-taso1"
                :active (nav/valittu-valilehti-atom :ilmoitukset-valilehti)}

       "Tieliikenne"
       :tieliikenne
       [tuck tiedot/ilmoitukset ilmoitukset*]

       "Tietyö"
       :tietyo
       [tuck tietyoilmoitukset-tiedot/ilmoitukset tietyoilmoitukset-view/ilmoitukset*]])))
