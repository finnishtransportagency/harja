(ns harja.views.ilmoitukset.tieliikenneilmoitukset
  "Tieliikenneilmoituksien pääsivu."
  (:require [harja.tiedot.ilmoitukset.tieliikenneilmoitukset :as tiedot]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tietyoilmoitukset-tiedot]
            [harja.domain.tieliikenneilmoitukset :refer
             [kuittausvaatimukset-str ilmoitustyypin-lyhenne-ja-nimi
              +ilmoitusten-selitteet+ kuittaustyypin-selite kuittaustyypin-lyhenne
              tilan-selite vaikutuksen-selite] :as domain]
            [harja.ui.bootstrap :as bs]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :as kentat]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.napit :as napit]
            [harja.ui.lomake :as lomake]
            [harja.ui.protokollat :as protokollat]
            [harja.fmt :as fmt]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.views.kartta :as kartta]
            [harja.views.ilmoituskuittaukset :as kuittaukset]
            [harja.views.ilmoituksen-tiedot :as it]
            [harja.views.ilmoitukset.tietyoilmoitukset :as tietyoilmoitukset-view]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.notifikaatiot :as notifikaatiot]
            [tuck.core :refer [tuck]]
            [harja.tiedot.ilmoitukset.viestit :as v]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.ui.debug :as debug])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tyokalut.ui :refer [for*]]))

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

(defn vihjeet []
  [yleiset/vihje-elementti
   [:span
    [:span "Ilmoituksia päivitetään automaattisesti. Yhteydenottopyynnöt "]
    [:span.bold "lihavoidaan"]
    [:span ", edellinen valinta korostetaan "]
    [:span.vihje-hento-korostus "sinisellä"]
    [:span ", virka-apupyynnöt "]
    [:span.selite-virkaapu "punaisella"]
    [:span " selitelaatikossa."]]])

(defn ilmoituksen-tiedot [e! ilmoitus]
  [:div
   [:span
    [:div.margin-vertical-16
     [napit/takaisin "Listaa ilmoitukset" #(e! (v/->PoistaIlmoitusValinta)) {:luokka "nappi-reunaton"} ]]
    [it/ilmoitus e! ilmoitus]]])

(defn- kuittaus-tooltip [{:keys [kuittaustyyppi kuitattu kuittaaja] :as kuittaus} napin-kuittaustyypi kuitattu? oikeus?]
  (let [selite (kuittaustyypin-selite (or kuittaustyyppi napin-kuittaustyypi))]
    (conj
      (if kuitattu?
        [:div
         selite
         [:br]
         (pvm/pvm-aika kuitattu)
         [:br] (:etunimi kuittaaja) " " (:sukunimi kuittaaja)
         [:br]]

        [:div
         selite
         [:br]
         "Ei tehty"
         [:br]])

      (if oikeus?
        "Kuittaa klikkaamalla"
        "Ei oikeutta kuitata"))))


(defn kuittauslista [e! pikakuittaus {id :id kuittaukset :kuittaukset :as ilmoitus}]
  (let [oikeus? (oikeudet/voi-kirjoittaa? oikeudet/ilmoitukset-ilmoitukset @nav/valittu-urakka-id)
        kuittaukset-tyypin-mukaan (group-by :kuittaustyyppi kuittaukset)
        pikakuittaus? (and pikakuittaus (= id (get-in pikakuittaus [:ilmoitus :id])))]
    [:span
     (when pikakuittaus?
       [kuittaukset/pikakuittaus e! pikakuittaus])
     [:div.kuittauslista
      (for*
        [kuittaustyyppi domain/kuittaustyypit
         :let [kuitattu? (contains? kuittaukset-tyypin-mukaan kuittaustyyppi)]]
        [yleiset/tooltip {}
         [:div.kuittaus {:class (str (name kuittaustyyppi)
                                     (when-not kuitattu?
                                       "-ei-kuittausta")
                                     (when-not oikeus?
                                       " ei-sallittu"))
                         :on-click #(do (.stopPropagation %)
                                        (.preventDefault %)
                                        (when oikeus?
                                          (e! (v/->AloitaPikakuittaus ilmoitus kuittaustyyppi))))}
          [:span (kuittaustyypin-lyhenne kuittaustyyppi)]]
         [kuittaus-tooltip (last (kuittaukset-tyypin-mukaan kuittaustyyppi)) kuittaustyyppi kuitattu? oikeus?]])]]))

(defn ilmoitusten-hakuehdot [e! valinnat-nyt]
  [lomake/lomake
   {:luokka "css-grid sm-css-grid-colums-4x1"
    :muokkaa! #(e! (v/->AsetaValinnat %))}
   [(valinnat/aikavalivalitsin "Tiedotettu urakkaan aikavälillä"
      tiedot/aikavalit
      (merge valinnat-nyt {:palstoita-vapaa-aikavali? true})
      {:vakioaikavali :valitetty-urakkaan-vakioaikavali
       :alkuaika      :valitetty-urakkaan-alkuaika
       :loppuaika     :valitetty-urakkaan-loppuaika}
      false
      {::lomake/col-luokka"col-xs-12"
       :palstoja 2})
    (valinnat/aikavalivalitsin "Toimenpiteet aloitettu"
      tiedot/toimenpiteiden-aikavalit
      (merge valinnat-nyt {:palstoita-vapaa-aikavali? true})
      {:vakioaikavali :toimenpiteet-aloitettu-vakioaikavali
       :alkuaika :toimenpiteet-aloitettu-alkuaika
       :loppuaika :toimenpiteet-aloitettu-loppuaika}
      false
      {::lomake/col-luokka "col-xs-12"
       :palstoja 2}) 
    {:nimi :hakuehto :otsikko "Hakusana"
     :placeholder "Hae tekstillä..."
     :tyyppi :string
     :pituus-max 64
     :palstoja 2
     ::lomake/col-luokka "col-xs-12"}
    {:nimi :selite
     :palstoja 2
     :otsikko "Selite"
     :placeholder "Hae ja valitse selite"
     :tyyppi :haku
     :hae-kun-yli-n-merkkia 0
     :nayta second :fmt second
     :lahde selitehaku
     ::lomake/col-luokka "col-xs-12"}
    {:nimi :tr-numero
     :palstoja 2
     :otsikko "Tienumero"
     :placeholder "Rajaa tienumerolla"
     :tyyppi :positiivinen-numero :kokonaisluku? true
     ::lomake/col-luokka"col-xs-12"}
    {:nimi :tunniste
     :palstoja 2
     :otsikko "Tunniste"
     :placeholder "Rajaa tunnisteella"
     :tyyppi :string
     ::lomake/col-luokka "col-xs-12"}
    {:nimi :ilmoittaja-nimi
     :otsikko "Ilmoittajan nimi"
     :placeholder "Rajaa ilmoittajan nimellä"
     :tyyppi :string
     ::lomake/col-luokka "col-xs-12"}
    {:nimi :ilmoittaja-puhelin
     :palstoja 2
     :otsikko "Ilmoittajan puhelinnumero"
     :placeholder "Rajaa ilmoittajan puhelinnumerolla"
     :tyyppi :puhelin
     ::lomake/col-luokka "col-xs-12"}
    {:nimi :tilat
     :otsikko "Tila"
     :tyyppi :checkbox-group
     :vaihtoehdot tiedot/tila-filtterit
     :palstoja 2
     ::lomake/col-luokka "col-xs-12"
     :vaihtoehto-nayta tilan-selite}
    {:nimi :tyypit
     :otsikko "Tyyppi"
     :tyyppi :checkbox-group
     :palstoja 2
     ::lomake/col-luokka "col-xs-12"
     :vaihtoehdot [:toimenpidepyynto :tiedoitus :kysely]
     :vaihtoehto-nayta ilmoitustyypin-lyhenne-ja-nimi}
    {:nimi :vaikutukset
     :otsikko "Vaikutukset"
     :tyyppi :checkbox-group
     :palstoja 1
     ::lomake/col-luokka "col-xs-12"
     :vaihtoehdot tiedot/vaikutukset-filtterit
     :vaihtoehto-nayta vaikutuksen-selite
     :vihje kuittausvaatimukset-str}
    {:nimi :aloituskuittauksen-ajankohta
     :otsikko "Aloituskuittaus annettu"
     :tyyppi :radio-group
     :palstoja 2
     ::lomake/col-luokka "col-xs-12"
     :vaihtoehdot [:kaikki :alle-tunti :myohemmin]
     :vaihtoehto-nayta (fn [arvo]
                         ({:kaikki "Älä rajoita aloituskuittauksella"
                           :alle-tunti "Alle tunnin kuluessa"
                           :myohemmin "Yli tunnin päästä"}
                          arvo))}]
   valinnat-nyt])

(defn ilmoitustyypin-selite [ilmoitustyyppi]
  (let [tyyppi (domain/ilmoitustyypin-lyhenne ilmoitustyyppi)]
    [:div {:class [tyyppi "text-nowrap"]} tyyppi]))

(defn tunniste-tooltip [tunniste]
  [:div
   [:div.harmaa-teksti "Tunniste"]
   [:span (or tunniste "-")]])

(defn ilmoitusten-paanakyma
  [e! {valinnat-nyt :valinnat
       kuittaa-monta :kuittaa-monta
       haetut-ilmoitukset :ilmoitukset
       ilmoituksen-haku-kaynnissa? :ilmoituksen-haku-kaynnissa?
       pikakuittaus :pikakuittaus :as ilmoitukset}]

  (let [{valitut-ilmoitukset :ilmoitukset :as kuittaa-monta-nyt} kuittaa-monta
        valitse-ilmoitus! (when kuittaa-monta-nyt
                            #(e! (v/->ValitseKuitattavaIlmoitus %)))
        pikakuittaus-ilmoitus-id (when pikakuittaus
                                   (get-in pikakuittaus [:ilmoitus :id]))]
    [:span.ilmoitukset

     [ilmoitusten-hakuehdot e! valinnat-nyt]
     [:div
      [kentat/tee-kentta {:tyyppi :checkbox
                          :teksti "Äänimerkki uusista ilmoituksista"}
       tiedot/aanimerkki-uusista-ilmoituksista?]

      [vihjeet]

      (when-not kuittaa-monta-nyt
        [napit/yleinen-toissijainen "Kuittaa monta ilmoitusta" #(e! (v/->AloitaMonenKuittaus))
         {:luokka "pull-right kuittaa-monta"}])

      (when kuittaa-monta-nyt
        [kuittaukset/kuittaa-monta-lomake e! kuittaa-monta])

      [:h2 (str (count haetut-ilmoitukset) " Ilmoitusta"
             (when @nav/valittu-urakka (str " Urakassa " (:nimi @nav/valittu-urakka))))]


      [grid
       {:tyhja (if haetut-ilmoitukset
                 "Ei löytyneitä tietoja"
                 [ajax-loader "Haetaan ilmoituksia"])
        :data-cy "ilmoitukset-grid"
        :rivi-klikattu (when (and (not ilmoituksen-haku-kaynnissa?)
                                  (nil? pikakuittaus))
                         (or valitse-ilmoitus!
                             #(e! (v/->ValitseIlmoitus (:id %)))))
        :piilota-toiminnot true
        :max-rivimaara 500
        :max-rivimaaran-ylitys-viesti "Yli 500 ilmoitusta. Tarkenna hakuehtoja."
        :rivin-luokka #(when (and pikakuittaus (not= (:id %) pikakuittaus-ilmoitus-id))
                         "ilmoitusrivi-fade")}

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
                             [kentat/raksiboksi {:disabled (or liidosta-tullut?
                                                               (not kirjoitusoikeus?))
                                                 :toiminto (when (and (not ilmoituksen-haku-kaynnissa?)
                                                                      (nil? pikakuittaus))
                                                             #(valitse-ilmoitus! rivi))}
                              (boolean (valitut-ilmoitukset rivi))]]))
           :leveys "40px"})
        (when-not @nav/valittu-urakka
          {:otsikko "Urakka" :otsikkorivi-luokka "urakka" :leveys "" :nimi :urakkanimi
           :hae (comp fmt/lyhennetty-urakan-nimi :urakkanimi)})
        {:otsikko "Saapunut" :nimi :valitetty-urakkaan
         :hae (comp pvm/pvm-aika :valitetty-urakkaan)
         :otsikkorivi-luokka "saapunut" :leveys ""
         :solun-tooltip (fn [rivi]
                          {:tooltip-tyyppi :komponentti
                           :tooltip-komponentti (tunniste-tooltip (:tunniste rivi))})}
        {:otsikko "Tyyppi" :nimi :ilmoitustyyppi
         :tyyppi :komponentti
         :komponentti #(ilmoitustyypin-selite (:ilmoitustyyppi %))
         :otsikkorivi-luokka "tyyppi" :leveys ""}
        {:otsikko "Selite" :nimi :selitteet
         :tyyppi :komponentti
         :komponentti it/selitelista
         :otsikkorivi-luokka "selite" :leveys ""}
        {:otsikko "Lisätieto" :nimi :lisatieto :otsikkorivi-luokka "lisatieto"
         :leveys ""
         :luokka "lisatieto-rivi"
         :solun-tooltip (fn [rivi]
                          {:teksti (:lisatieto rivi)})}

        {:otsikko "Tie" :nimi :tierekisteri
         :hae #(tr-domain/tierekisteriosoite-tekstina (:tr %) {:teksti-tie? false})
         :tyyppi :string
         :otsikkorivi-luokka "tie" :leveys ""}
        {:otsikko "Kuittaukset" :nimi :kuittaukset
         :tyyppi :komponentti
         :komponentti (partial kuittauslista e! pikakuittaus)
         :otsikkorivi-luokka "kuittaukset" :leveys ""}
        {:otsikko "Tila" :nimi :tila :otsikkorivi-luokka "tila"
         :leveys ""
         :hae #(let [selite (tilan-selite (:tila %))]
                 (if (:aiheutti-toimenpiteita %)
                   (str selite " (Toimenpitein)")
                   selite))}
        {:otsikko "Toimenpiteet aloitettu" :nimi :toimenpiteet-aloitettu
         :tyyppi :pvm :fmt pvm/pvm-aika
         :otsikkorivi-luokka "aloitettu" :leveys ""}]
       (mapv #(merge %
                     (when (:yhteydenottopyynto %)
                       {:lihavoi true})
                     (when (= (:id %) (:edellinen-valittu-ilmoitus-id ilmoitukset))
                       {:korosta-hennosti true}))

             haetut-ilmoitukset)]]]))

(defn- ilmoitukset* [e! ilmoitukset]
  ;; Kun näkymään tullaan, yhdistetään navigaatiosta tulevat valinnat
  (e! (v/->YhdistaValinnat @tiedot/valinnat))

  (komp/luo
    (komp/lippu tiedot/karttataso-ilmoitukset)
    (komp/kuuntelija :ilmoitus-klikattu (fn [_ i] (e! (v/->ValitseIlmoitus (:id i)))))
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (v/->YhdistaValinnat uusi))))
    (komp/sisaan-ulos #(do
                         (notifikaatiot/pyyda-notifikaatiolupa)
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (nav/vaihda-kartan-koko! :M)
                         (nav/vaihda-urakkatyyppi! {:nimi "Kaikki" :arvo :kaikki})
                         (when @nav/valittu-ilmoitus-id
                           (e! (v/->ValitseIlmoitus @nav/valittu-ilmoitus-id)))
                         (kartta-tiedot/kasittele-infopaneelin-linkit!
                           {:ilmoitus {:toiminto (fn [ilmoitus-infopaneelista]
                                                   (e! (v/->ValitseIlmoitus (:id ilmoitus-infopaneelista))))
                                       :teksti "Valitse ilmoitus"}}))
                      #(do
                         (kartta-tiedot/kasittele-infopaneelin-linkit! nil)
                         (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)))
    (fn [e! {valittu-ilmoitus :valittu-ilmoitus :as ilmoitukset}]
      [:span
       [kartta/kartan-paikka]
       (if (and @nav/valittu-ilmoitus-id valittu-ilmoitus)
         [ilmoituksen-tiedot e! valittu-ilmoitus]
         [ilmoitusten-paanakyma e! ilmoitukset])])))

(defn ilmoitukset []
  (fn []
    (if-not (istunto/ominaisuus-kaytossa? :tietyoilmoitukset)
      [tuck tiedot/ilmoitukset ilmoitukset*]
      ;; else
      [bs/tabs {:style :tabs :classes "tabs-taso1"
                :active (nav/valittu-valilehti-atom :ilmoitukset)}

       "Tieliikenne"
       :tieliikenne
       [tuck tiedot/ilmoitukset ilmoitukset*]

       "Tietyö"
       :tietyo
       [tuck tietyoilmoitukset-tiedot/tietyoilmoitukset tietyoilmoitukset-view/ilmoitukset*]])))
