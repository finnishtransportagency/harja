(ns harja.views.urakka.suunnittelu.suola
  "Urakan suolan käytön suunnittelu"
  (:require [reagent.core :refer [atom wrap] :as r]
            [cljs.core.async :refer [<!]]
            [tuck.core :as tuck]

            [harja.domain.oikeudet :as oikeudet]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]

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
   (when (:kopioidaan-tuleville-vuosille? data)
     [:p "Olet poistamassa rajoitusalueen seuraavilta hoitovuosilta <todo---->"])

   [:div {:style {:padding-bottom "1rem"}}
    [:span {:style {:padding-right "1rem"}}
     [napit/yleinen-toissijainen
      "Peruuta"
      (r/partial (fn []
                   (modal/piilota!)))
      {:vayla-tyyli? true
       :luokka "suuri"}]]
    [:span
     [napit/poista
      "Poista rajoitusalue"
      varmistus-fn
      {:vayla-tyyli? true
       :luokka "suuri"}]]]])

(defn lomake-rajoitusalue-skeema [lomake muokkaustila?]
  (into []
    (concat
      [(lomake/rivi
         {:nimi :kopioidaan-tuleville-vuosille?
          :palstoja 3
          :tyyppi :checkbox
          :teksti (if muokkaustila?
                    "Tee muutosket myös tuleville hoitovuosille"
                    "Kopioi rajoitukset tuleville hoitovuosille")})]
      [(lomake/ryhma
         {:otsikko "Sijainti"
          :rivi? true
          :ryhman-luokka "lomakeryhman-otsikko-tausta"}
         {:nimi :tie
          :otsikko "Tie"
          :tyyppi :positiivinen-numero
          :kokonaisluku? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:tie] lomake)
          :virheteksti (validointi/nayta-virhe-teksti [:tie] lomake)
          :rivi-luokka "lomakeryhman-rivi-tausta"}
         {:nimi :aosa
          :otsikko "A-osa"
          :tyyppi :positiivinen-numero
          :kokonaisluku? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:aosa] lomake)
          :rivi-luokka "lomakeryhman-rivi-tausta"}
         {:nimi :aet
          :otsikko "A-et."
          :tyyppi :positiivinen-numero
          :kokonaisluku? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:aet] lomake)
          :rivi-luokka "lomakeryhman-rivi-tausta"}
         {:nimi :losa
          :otsikko "L-osa."
          :tyyppi :positiivinen-numero
          :kokonaisluku? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:losa] lomake)
          :rivi-luokka "lomakeryhman-rivi-tausta"}
         {:nimi :let
          :otsikko "L-et."
          :tyyppi :positiivinen-numero
          :kokonaisluku? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:let] lomake)
          :rivi-luokka "lomakeryhman-rivi-tausta"})
       (lomake/ryhma
         {:ryhman-luokka "lomakeryhman-otsikko-tausta"
          :rivi? true}
         {:nimi :pituus
          :otsikko "Pituus (m)"
          :tyyppi :positiivinen-numero
          :vayla-tyyli? true
          :disabled? true
          :tarkkaile-ulkopuolisia-muutoksia? true
          :muokattava? (constantly false)
          ::lomake/col-luokka "col-xs-4"
          :rivi-luokka "lomakeryhman-rivi-tausta"}
         {:nimi :ajoratojen_pituus
          :otsikko "Pituus ajoradat (m)"
          :tyyppi :positiivinen-numero
          :vayla-tyyli? true
          :disabled? true
          :tarkkaile-ulkopuolisia-muutoksia? true
          :muokattava? (constantly false)
          ::lomake/col-luokka "col-xs-8"
          :rivi-luokka "lomakeryhman-rivi-tausta"})
       (lomake/ryhma
         {:ryhman-luokka "lomakeryhman-otsikko-tausta"
          :rivi? true}
         {:nimi :pohjavesialueet
          :otsikko "Pohjavesialue"
          :tyyppi :komponentti
          :komponentti (fn [data]
                         (mapcat (fn [rivi]
                                   [^{:key (hash rivi)}
                                    [:div (str (:nimi rivi) " (" (:tunnus rivi) ")")]])
                           (:pohjavesialueet (:data data))))


          :tarkkaile-ulkopuolisia-muutoksia? true
          :muokattava? (constantly false)
          ::lomake/col-luokka "col-xs-12"
          :rivi-luokka "lomakeryhman-rivi-tausta"})
       (lomake/ryhma
         {:otsikko "Suolarajoitus"
          :rivi? true
          :ryhman-luokka "lomakeryhman-otsikko-tausta"}
         {:nimi :suolarajoitus
          :otsikko "Suolan max määrä"
          :tyyppi :positiivinen-numero
          :yksikko "t/ajoratakm"
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:suolarajoitus] lomake)
          :pakollinen? true
          ::lomake/col-luokka "col-sm-6"
          :rivi-luokka "lomakeryhman-rivi-tausta"})
       (lomake/ryhma
         {:rivi? true}
         {:nimi :formiaatti
          :tyyppi :checkbox
          :palstoja 3
          :teksti "Alueella tulee käyttää suolan sijaan formiaattia"
          :rivi-luokka "lomakeryhman-rivi-tausta"})])))

(defn lomake-rajoitusalue
  "Rajoitusalueen lisäys/muokkaus"
  [e! {:keys [lomake valittu-hoitovuosi] :as app}]
  (let [;; Aseta hoitovuosi lomakkeelle, mikäli sitä ei ole
        rajoituslomake (if (nil? (:hoitokauden_alkuvuosi lomake))
                         (assoc lomake :hoitokauden_alkuvuosi valittu-hoitovuosi)
                         lomake)
        muokkaustila? true
        disabled? (not (get-in app [:lomake ::tila/validi?]))
        ;; TODO: Muokkaus
        ;; TODO: Oikeustarkastukset
        ]
    [:div
     #_ [debug/debug (:lomake app)]
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
                        {:disabled disabled? :paksu? true}]
                       [napit/tallenna
                        "Tallenna ja lisää seuraava"
                        #(e! (suolarajoitukset-tiedot/->TallennaLomake data true))
                        {:disabled disabled? :paksu? true}]]
                      [:div.col-xs-5 {:style {:float "right"}}
                       (when (:rajoitusalue_id data)
                         [napit/poista
                          "Poista"
                          #(modal/nayta! {:otsikko "Rajoitusalueen poistaminen"}
                             [rajoituksen-poiston-varmistus-modaali e!
                              {:data data
                               :varmistus-fn (fn []
                                               (modal/piilota!)
                                               (e! (suolarajoitukset-tiedot/->PoistaSuolarajoitus {:rajoitusalue_id (:rajoitusalue_id data)
                                                                                                   :kopioidaan-tuleville-vuosille? (:kopioidaan-tuleville-vuosille? data)})))}])
                          {:vayla-tyyli? true}])
                       [napit/yleinen-toissijainen
                        "Peruuta"
                        #(e! (suolarajoitukset-tiedot/->AvaaTaiSuljeSivupaneeli false nil))
                        {:paksu? true}]]]])}

      (lomake-rajoitusalue-skeema rajoituslomake muokkaustila?)
      rajoituslomake]]))


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
                                    (merge
                                      {:kopioidaan-tuleville-vuosille? true}
                                      (some (fn [r]
                                                   (when (= (:rajoitusalue_id %) (:rajoitusalue_id r)) %))
                                             rajoitukset))))}
   [{:otsikko "Tie" :nimi :tie :tasaa :oikea :leveys 0.4}
    {:otsikko "Osoiteväli" :nimi :osoitevali :leveys 1}
    {:otsikko "Pituus (m)" :nimi :pituus :fmt fmt/pyorista-ehka-kolmeen :tasaa :oikea :leveys 0.8}
    {:otsikko "Pituus ajoradat (m)" :nimi :ajoratojen_pituus :fmt fmt/pyorista-ehka-kolmeen
     :tasaa :oikea :leveys 0.8}
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
     :leveys 1.4}
    {:otsikko "Suolankäyttöraja (t/ajoratakm)" :nimi :suolarajoitus :tasaa :oikea
     :fmt #(if % (fmt/desimaaliluku % 1) "–") :leveys 0.8}
    {:otsikko "" :nimi :formiaatti :fmt #(when % "Käytettävä formiaattia") :leveys 0.8}]
   rajoitukset])

(defn urakan-suolarajoitukset* [e! _]
  (komp/luo
    (komp/sisaan
      #(do
         (e! (suolarajoitukset-tiedot/->HaeSuolarajoitukset (pvm/vuosi (first @urakka/valittu-hoitokausi))))))
    (komp/ulos
      (fn []
        ;; Tänne mahdolliset karttatasojen poistot
        ))

    (fn [e! app]
      (let [{:keys [alkupvm]} (-> @tila/tila :yleiset :urakka) ;; Ota urakan alkamis päivä
            vuosi (pvm/vuosi alkupvm)
            rajoitusalueet (:suolarajoitukset app)
            lomake-auki? (:rajoitusalue-lomake-auki? app)
            urakka @nav/valittu-urakka
            valittu-vuosi (if (nil? (:valittu-hoitovuosi app))
                            (pvm/vuosi (first @urakka/valittu-hoitokausi))
                            (:valittu-hoitovuosi app))
            hoitovuodet (into [] (range vuosi (+ 5 vuosi)))
            saa-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-suola (:id urakka))]
        [:div.urakan-suolarajoitukset
         [:h2 "Urakan suolarajoitukset hoitovuosittain"]
         [debug/debug app]

         [:div.kontrollit
          [:div.row
           [:div.:div.col-xs-12.col-md-3
            [:span.alasvedon-otsikko-vayla "Hoitovuosi"]
            [yleiset/livi-pudotusvalikko {:valinta valittu-vuosi
                                          :vayla-tyyli? true
                                          :data-cy "hoitokausi-valinta"
                                          :valitse-fn #(e! (suolarajoitukset-tiedot/->ValitseHoitovuosi %))
                                          :format-fn #(str "01.10." % " - 30.9." (inc %))
                                          :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
             hoitovuodet]]]]

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
            #(e! (suolarajoitukset-tiedot/->AvaaTaiSuljeSivupaneeli true {:kopioidaan-tuleville-vuosille? true}))
            {:luokka "pull-right"
             :disabled (not saa-muokata?)}]]

          ;; TODO: Kartta
          #_[kartta]

          ;; Rajoitusalueen lisäys/muokkauslomake. Avautuu sivupaneeliin
          ;; TODO: Lomakkeen avaaminen/sulkeminen tuck-eventeiksi ja app stateen
          (when lomake-auki?
            [:div.overlay-oikealla {:style {:width "570px" :overflow "auto"}}
             [lomake-rajoitusalue e! app]])

          [taulukko-rajoitusalueet e! rajoitusalueet saa-muokata?]]]))))

(defn urakan-suolarajoitukset []
  (do
    ;(js/console.log "tila/suunnittelu-suolarajoitukset" (pr-str tila/suunnittelu-suolarajoitukset))
    (tuck/tuck tila/suunnittelu-suolarajoitukset urakan-suolarajoitukset*)))
