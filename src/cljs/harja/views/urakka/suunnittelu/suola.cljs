(ns harja.views.urakka.suunnittelu.suola
  "Urakan suolan käytön suunnittelu"
  (:require [reagent.core :refer [atom wrap] :as r]
            [cljs.core.async :refer [<!]]
            [tuck.core :as tuck]

            [harja.domain.oikeudet :as oikeudet]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.fmt :as fmt]

            [harja.tiedot.urakka.toteumat.suola :as tiedot-suola]
            [harja.tiedot.urakka.suunnittelu.suolarajoitukset-tiedot :as suolarajoitukset-tiedot]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.hallinta.indeksit :as i]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]

            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.napit :refer [palvelinkutsu-nappi]]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.validointi :as validointi]
            [harja.ui.modal :as modal]

            [harja.views.kartta :as kartta]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.views.kartta.pohjavesialueet :as pohjavesialueet])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(defn- rajoituksen-poiston-varmistus-modaali [e! {:keys [varmistus-fn data] :as parametrit}]
  [:div.row
   [:p "Olet poistamassa <todo---->"]


   [:div {:style {:padding-bottom "1rem"}}
    [:span {:style {:padding-right "1rem"}}
     [napit/yleinen-toissijainen
      "Peruuta"
      (r/partial (fn []
                   (modal/piilota!)))
      {:vayla-tyyli? true
       :luokka       "suuri"}]]
    [:span
     [napit/poista
      "Poista rajoitusalue"
      varmistus-fn
      {:vayla-tyyli? true
       :luokka       "suuri"}]]]])

(defn lomake-rajoitusalue-skeema [lomake]
  (into []
    (concat
      [(lomake/rivi
         {:nimi :kopioidaan-tuleville-vuosille?
          :palstoja 3
          :tyyppi :checkbox
          :teksti "Kopioi rajoitukset tuleville hoitovuosille"})]
      [(lomake/ryhma
         {:otsikko "Sijainti"
          :rivi? true}
         {:nimi :tie
          :otsikko "Tie"
          :tyyppi :positiivinen-numero
          :kokonaisluku? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:tie] lomake)
          :virheteksti (validointi/nayta-virhe-teksti [:tie] lomake)}
         {:nimi :aosa
          :otsikko "A-osa"
          :tyyppi :positiivinen-numero
          :kokonaisluku? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:aosa] lomake)}
         {:nimi :aet
          :otsikko "A-et."
          :tyyppi :positiivinen-numero
          :kokonaisluku? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:aet] lomake)}
         {:nimi :losa
          :otsikko "L-osa."
          :tyyppi :positiivinen-numero
          :kokonaisluku? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:losa] lomake)}
         {:nimi :let
          :otsikko "L-et."
          :tyyppi :positiivinen-numero
          :kokonaisluku? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:let] lomake)})
       (lomake/ryhma
         {:rivi? true}
         {:nimi :pituus
          :otsikko "Pituus (m)"
          :tyyppi :positiivinen-numero
          :vayla-tyyli? true
          :disabled? true
          :tarkkaile-ulkopuolisia-muutoksia? true
          :muokattava? (constantly false)}
         {:nimi :ajoratojen_pituus
          :otsikko "Pituus ajoradat (m)"
          :tyyppi :positiivinen-numero
          :vayla-tyyli? true
          :disabled? true
          :tarkkaile-ulkopuolisia-muutoksia? true
          :muokattava? (constantly false)})
       (lomake/ryhma
         {:otsikko "Suolarajoitus"
          :rivi? true}
         {:nimi :suolarajoitus
          :otsikko "Suolan max määrä"
          :tyyppi :positiivinen-numero
          :yksikkö "t/ajoratakm"
          :vayla-tyyli? true})
       (lomake/ryhma
         {:rivi? true}
         {:nimi :formiaatti
          :tyyppi :checkbox
          :palstoja 3
          :teksti "Alueella tulee käyttää suolan sijaan formiaattia"})])))

(defn lomake-rajoitusalue
  "Rajoitusalueen lisäys/muokkaus"
  [e! rajoituslomake]
  (let [muokkaustila? true
        ;; TODO: Muokkaus
        ;; TODO: Oikeustarkastukset
        ]

    [lomake/lomake
     {:ei-borderia? true
      :voi-muokata? true
      :tarkkaile-ulkopuolisia-muutoksia? false
      :otsikko (when muokkaustila?
                 (if (:id rajoituslomake) "Muokkaa rajoitusta" "Lisää rajoitusalue"))
      ;; TODO: Muokkaus
      :muokkaa! (r/partial #(e! (suolarajoitukset-tiedot/->PaivitaLomake %)))
      :footer-fn (fn [data]
                   [:div.row

                    [:div.row
                     [:div.col-xs-7 {:style {:padding-left "0"}}
                      [napit/tallenna
                       "Tallenna"
                       #(e! (suolarajoitukset-tiedot/->TallennaLomake data false))
                       {:disabled false :paksu? true}]
                      [napit/tallenna
                       "Tallenna ja lisää seuraava"
                       #(e! (suolarajoitukset-tiedot/->TallennaLomake data true))
                       {:disabled false :paksu? true}]]
                     [:div.col-xs-5 {:style {:float "right"}}
                      (when (:rajoitusalue_id data)
                        [napit/poista
                         "Poista"
                         #(modal/nayta! {:otsikko "Rajoitusalueen poistaminen"}
                            [rajoituksen-poiston-varmistus-modaali e!
                             {:data data
                              :varmistus-fn (fn []
                                              (modal/piilota!)
                                              (e! (suolarajoitukset-tiedot/->PoistaSuolarajoitus {:rajoitusalue_id (:rajoitusalue_id data)})))}])
                         {:vayla-tyyli? true}])
                      [napit/yleinen-toissijainen
                       "Peruuta"
                       #(e! (suolarajoitukset-tiedot/->AvaaTaiSuljeSivupaneeli false nil))
                       {:paksu? true}]]]])}

     (lomake-rajoitusalue-skeema rajoituslomake)
     rajoituslomake]))


;; -------

(defn lomake-talvisuolan-kayttoraja
  [urakka]
  (let [saa-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-suola (:id urakka))]

    (fn []
      (let [tiedot {}
            valittavat-indeksit (map :indeksinimi (i/urakkatyypin-indeksit :hoito))]

        [lomake/lomake {:ei-borderia? true
                        :muokkaa! (fn [uusi]
                                    (log "lomaketta muokattu, tiedot:" (pr-str uusi))
                                    ;; TODO: Tuck event
                                    )}
         [(lomake/rivi
            {:nimi :kopioi-rajoitukset
             :tyyppi :checkbox :palstoja 1
             :teksti "Kopioi rajoitukset tuleville hoitovuosille"})
          (lomake/rivi
            {:nimi :talvisuolaraja
             :tyyppi :positiivinen-numero
             :palstoja 1 :pakollinen? true :muokattava? (constantly saa-muokata?)
             :otsikko "Talvisuolan käyttöraja / vuosi"
             :yksikko "kuivatonnia" :placeholder "Ei rajoitusta"})

          (lomake/rivi
            {:nimi :sanktio :tyyppi :positiivinen-numero
             :palstoja 1 :muokattava? (constantly saa-muokata?)
             :otsikko "Sanktio / ylittävä tonni" :yksikko "€"}

            (when (urakka/indeksi-kaytossa-sakoissa?)
              {:nimi :indeksi :tyyppi :valinta :palstoja 1
               :otsikko "Indeksi"
               :muokattava? (constantly saa-muokata?)
               :valinta-nayta #(if (not saa-muokata?)
                                 ""
                                 (if (nil? %) "Ei indeksiä" (str %)))
               :valinnat (conj valittavat-indeksit nil)}))]
         tiedot]))))

(defn lomake-pohjavesialueen-suolasanktio
  [urakka]
  (let [saa-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-suola (:id urakka))]

    (fn []
      (let [tiedot {}
            valittavat-indeksit (map :indeksinimi (i/urakkatyypin-indeksit :hoito))]

        [lomake/lomake {:ei-borderia? true
                        :muokkaa! (fn [uusi]
                                    (log "lomaketta muokattu, tiedot:" (pr-str uusi))
                                    ;; TODO: Tuck event
                                    )}
         [(lomake/rivi
            {:nimi :kopioi-rajoitukset
             :tyyppi :checkbox :palstoja 1
             :teksti "Kopioi rajoitukset tuleville hoitovuosille"})
          (lomake/rivi
            {:otsikko "Sanktio / ylittävä tonni"
             :pakollinen? true
             :muokattava? (constantly saa-muokata?) :nimi :vainsakkomaara
             :tyyppi :positiivinen-numero :palstoja 1 :yksikko "€"}

            (when (urakka/indeksi-kaytossa-sakoissa?)
              {:otsikko "Indeksi" :nimi :indeksi :tyyppi :valinta
               :muokattava? (constantly saa-muokata?)
               :valinta-nayta #(if (not saa-muokata?)
                                 ""
                                 (if (nil? %) "Ei indeksiä" (str %)))
               :valinnat (conj valittavat-indeksit nil)

               :palstoja 1}))]
         tiedot]))))

(defn taulukko-rajoitusalueet
  "Rajoitusalueiden taulukko."
  [e! rajoitukset voi-muokata?]
  [grid/grid {:tunniste :rajoitusalue_id
              :piilota-muokkaus? true
              ;; Estetään dynaamisesti muuttuva "tiivis gridin" tyyli, jotta siniset viivat eivät mene vääriin kohtiin,
              ;; taulukon sarakemääriä muutettaessa. Tyylejä säädetty toteumat.less tiedostossa.
              :esta-tiivis-grid? true
              :tyhja (if (nil? rajoitukset)
                       [yleiset/ajax-loader "Rajoitusalueita haetaan..."]
                       "Ei Rajoitusalueita")
              :rivi-klikattu #(e! (suolarajoitukset-tiedot/->AvaaTaiSuljeSivupaneeli true
                                    (some (fn [r]
                                            (when (= (:rajoitusalue_id %) (:rajoitusalue_id r)) %))
                                      rajoitukset)))}
   [{:otsikko "Tie" :nimi :tie :tasaa :oikea :leveys 0.3}
    {:otsikko "Osoiteväli" :nimi :osoitevali :leveys 1}
    {:otsikko "Pituus (m)" :nimi :pituus :fmt fmt/pyorista-ehka-kolmeen :tasaa :oikea :leveys 1}
    {:otsikko "Pituus ajoradat (m)" :nimi :ajoratojen_pituus :fmt fmt/pyorista-ehka-kolmeen
     :tasaa :oikea :leveys 1}
    {:otsikko "Pohjavesialue (tunnus)" :nimi :pohjavesialueet
     :luokka "sarake-pohjavesialueet"
     :tyyppi :komponentti
     :komponentti (fn [{:keys [pohjavesialueet]}]
                    (if (seq pohjavesialueet)
                      (into [:div]
                        (mapv (fn [alue]
                                [:div (str (:nimi alue) " (" (:tunnus alue) ")")])
                          pohjavesialueet))
                      "-"))
     :leveys 1}
    {:otsikko "Suolankäyttöraja (t/ajoratakm)" :nimi :suolarajoitus :tasaa :oikea
     :fmt #(if % (fmt/desimaaliluku % 1) "–") :leveys 1}
    {:otsikko "" :nimi :formiaatti :fmt #(when % "Käytettävä formiaattia") :leveys 1}]
   rajoitukset])

(defn urakan-suolarajoitukset* [e! _]
  (komp/luo
    (komp/sisaan
      #(do
         (e! (suolarajoitukset-tiedot/->HaeSuolarajoitukset))))
    (komp/ulos
      (fn []
        ;; Tänne mahdolliset karttatasojen poistot
        ))

    (fn [e! app]
      (let [rajoitusalueet (:suolarajoitukset app)
            lomake-auki? (:rajoitusalue-lomake-auki? app)
            lomake (:lomake app)
            urakka @nav/valittu-urakka
            saa-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-suola (:id urakka))]
        [:div.urakan-suolarajoitukset
         [:h2 "Urakan suolarajoitukset hoitovuosittain"]
         [debug/debug app]

         [:div.kontrollit
          [valinnat/urakan-hoitokausi urakka]]

         [:div.lomakkeet
          [:h3 "Talvisuolan kokonaismäärän käyttöraja"]
          [lomake-talvisuolan-kayttoraja urakka]

          [:h3 "Pohjavesialueen suolasanktio"]
          [lomake-pohjavesialueen-suolasanktio urakka]]

         ;; Pohjavesialueiden rajoitusalueiden taulukko ym.
         [:div.pohjavesialueiden-suolarajoitukset
          [:div.header
           [:h3 {:class "pull-left"}
            "Pohjavesialueiden suolarajoitukset"]
           [napit/uusi "Lisää rajoitus"
            #(e! (suolarajoitukset-tiedot/->AvaaTaiSuljeSivupaneeli true nil))
            {:luokka "pull-right"
             :disabled (not saa-muokata?)}]]

          ;; TODO: Kartta
          #_[kartta]

          ;; Rajoitusalueen lisäys/muokkauslomake. Avautuu sivupaneeliin
          ;; TODO: Lomakkeen avaaminen/sulkeminen tuck-eventeiksi ja app stateen
          (when lomake-auki?
            [:div.overlay-oikealla {:style {:width "600px" :overflow "auto"}}
             [lomake-rajoitusalue e! lomake]])

          [taulukko-rajoitusalueet e! rajoitusalueet saa-muokata?]]]))))

(defn urakan-suolarajoitukset []
  (do
    ;(js/console.log "tila/suunnittelu-suolarajoitukset" (pr-str tila/suunnittelu-suolarajoitukset))
    (tuck/tuck tila/suunnittelu-suolarajoitukset urakan-suolarajoitukset*)))
