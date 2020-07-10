(ns harja.views.ilmoitukset.tieliikenneilmoitukset
  "Tieliikenneilmoituksien pääsivu."
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :refer [capitalize]]
            [harja.tiedot.ilmoitukset.tieliikenneilmoitukset :as tiedot]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tietyoilmoitukset-tiedot]
            [harja.domain.tieliikenneilmoitukset :refer
             [kuittausvaatimukset-str +ilmoitustyypit+ ilmoitustyypin-nimi
              ilmoitustyypin-lyhenne ilmoitustyypin-lyhenne-ja-nimi
              +ilmoitustilat+ nayta-henkilo parsi-puhelinnumero
              +ilmoitusten-selitteet+ parsi-selitteet kuittaustyypit
              kuittaustyypin-selite kuittaustyypin-lyhenne kuittaustyypin-otsikko
              tilan-selite vaikutuksen-selite] :as domain]
            [harja.ui.bootstrap :as bs]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.napit :refer [palvelinkutsu-nappi] :as napit]
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
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.tiedot.ilmoitukset.viestit :as v]
            [harja.ui.kentat :as kentat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.ui.debug :as debug]
            [harja.loki :as loki])
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
    [napit/takaisin "Listaa ilmoitukset" #(e! (v/->PoistaIlmoitusValinta))]
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
   {:luokka :horizontal
    :muokkaa! #(e! (v/->AsetaValinnat %))}
   [(valinnat/aikavalivalitsin "Tiedotettu urakkaan aikavälillä"
                               tiedot/aikavalit
                               valinnat-nyt
                               {:vakioaikavali :tiedotettu-vakioaikavali
                                :alkuaika      :tiedotettu-alkuaika
                                :loppuaika     :tiedotettu-loppuaika})
    (valinnat/aikavalivalitsin "Toimenpiteet aloitettu"
                               tiedot/toimenpiteiden-aikavalit
                               valinnat-nyt
                               {:vakioaikavali :toimenpiteet-aloitettu-vakioaikavali
                                :alkuaika :toimenpiteet-aloitettu-alkuaika
                                :loppuaika :toimenpiteet-aloitettu-loppuaika})
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
    {:nimi :ilmoittaja-nimi
     :palstoja 1
     :otsikko "Ilmoittajan nimi"
     :placeholder "Rajaa ilmoittajan nimellä"
     :tyyppi :string}
    {:nimi :ilmoittaja-puhelin
     :palstoja 1
     :otsikko "Ilmoittajan puhelinnumero"
     :placeholder "Rajaa ilmoittajan puhelinnumerolla"
     :tyyppi :puhelin}

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
      {:nimi :vaikutukset
       :otsikko "Vaikutukset"
       :tyyppi :checkbox-group
       :vaihtoehdot tiedot/vaikutukset-filtterit
       :vaihtoehto-nayta vaikutuksen-selite
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

      [grid
       {:tyhja (if haetut-ilmoitukset
                 "Ei löytyneitä tietoja"
                 [ajax-loader "Haetaan ilmoituksia"])
        :data-cy "ilmoitukset-grid"
        :rivi-klikattu (when (and (not ilmoituksen-haku-kaynnissa?)
                                  (nil? pikakuittaus))
                         (or valitse-ilmoitus!
                             #(e! (v/->ValitseIlmoitus %))))
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
                             [:input {:type "checkbox"
                                      :disabled (or liidosta-tullut?
                                                    (not kirjoitusoikeus?))
                                      :checked (valitut-ilmoitukset rivi)}]]))
           :leveys 1})
        {:otsikko "Urakka" :nimi :urakkanimi :leveys 5
         :hae (comp fmt/lyhennetty-urakan-nimi :urakkanimi)}
        {:otsikko "Tunniste" :nimi :tunniste :leveys 3}
        {:otsikko "Lisätietoja" :nimi :lisatieto :leveys 6
         :hae #(leikkaa-sisalto-pituuteen 30 (:lisatieto %))}
        {:otsikko "Tiedotettu urakkaan" :nimi :valitetty-urakkaan
         :hae (comp pvm/pvm-aika :valitetty-urakkaan) :leveys 6}
        {:otsikko "Tyyppi" :nimi :ilmoitustyyppi
         :tyyppi :komponentti
         :komponentti #(ilmoitustyypin-selite (:ilmoitustyyppi %))
         :leveys 2}
        {:otsikko "Sijainti" :nimi :tierekisteri
         :hae #(tr-domain/tierekisteriosoite-tekstina (:tr %))
         :tyyppi :pvm-aika
         :leveys 7}

        {:otsikko "Selitteet" :nimi :selitteet
         :tyyppi :komponentti
         :komponentti it/selitelista
         :leveys 6}
        {:otsikko "Kuittaukset" :nimi :kuittaukset
         :tyyppi :komponentti
         :komponentti (partial kuittauslista e! pikakuittaus)
         :leveys 8}

        {:otsikko "Tila" :nimi :tila :leveys 5 
           :hae #(let [selite (tilan-selite (:tila %))]
                 (if (:aiheutti-toimenpiteita %)
                   (str selite " (Toimenpitein)")
                   selite))}
        {:otsikko "Toimenpiteet aloitettu" :nimi :toimenpiteet-aloitettu
         :tyyppi :pvm :fmt pvm/pvm-aika
         :leveys 6}]
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
    (komp/kuuntelija :ilmoitus-klikattu (fn [_ i] (e! (v/->ValitseIlmoitus i))))
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (v/->YhdistaValinnat uusi))))
    (komp/sisaan-ulos #(do
                         (notifikaatiot/pyyda-notifikaatiolupa)
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (nav/vaihda-kartan-koko! :M)
                         (nav/vaihda-urakkatyyppi! {:nimi "Kaikki" :arvo :kaikki})
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
                :active (nav/valittu-valilehti-atom :ilmoitukset)}

       "Tieliikenne"
       :tieliikenne
       [tuck tiedot/ilmoitukset ilmoitukset*]

       "Tietyö"
       :tietyo
       [tuck tietyoilmoitukset-tiedot/tietyoilmoitukset tietyoilmoitukset-view/ilmoitukset*]])))
