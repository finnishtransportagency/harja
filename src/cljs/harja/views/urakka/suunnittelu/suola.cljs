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
            [harja.views.kartta.pohjavesialueet :as pohjavesialueet]
            [harja.ui.kentat :as kentat]
            [taoensso.timbre :as log])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<! reaction-writable]]
                   [harja.tyokalut.ui :refer [for*]]))

(defn- rajoituksen-poiston-varmistus-modaali [e! {:keys [lomake-tila urakka poista-kaikilta-vuosilta?-atom] :as parametrit}]
  [:div
   [:div "Olet poistamassa rajoitusalueen seuraavilta hoitovuosilta:"]

   ;; Useampi poistettava rajoitus
   (if (or @poista-kaikilta-vuosilta?-atom (:kopioidaan-tuleville-vuosille? lomake-tila))
     [:ul
      (for* [vuosi (pvm/tulevat-hoitovuodet
                     ;; Lomakkeella valittu hoitokausi tai urakan ensimmäinen hoitovuosi
                     (if @poista-kaikilta-vuosilta?-atom
                       ;; Jos poistetaan kaikilta vuosilta, niin asetetaan alkuvuodeksi
                       ;; urakan ensimmäinen vuosi
                       (pvm/vuosi (:alkupvm urakka))
                       (:hoitokauden-alkuvuosi lomake-tila))
                     (or
                       ;; Kun poistetaan kaikilta vuosilta, niin aloitetaan urakan ensimmäisestä hoitokaudesta
                       ;; ja jatketaan myös tuleville vuosille
                       @poista-kaikilta-vuosilta?-atom
                       (:kopioidaan-tuleville-vuosille? lomake-tila))
                     urakka)]
        ;; FIXME: Listataan hoitovuodet aikaväleineä, koska sivulla hoitovuoden voi vielä tällä hetkellä valita vanhan mallisesti vain aikavälinä
        ;;        Kun hoitovuosivalinta UI-komponenttia päivitetään Harjassa yleisesti, voisi tässä näyttää hoitovuosien järjestysnumeroja, kuten on speksattu.
        [:li (pvm/hoitokausi-str-alkuvuodesta vuosi)])]
     ;; Vain yksi poistettava rajoitus
     [:ul
      [:li
       (pvm/hoitokausi-str-alkuvuodesta
         ;; Lomakkeella valittu hoitokausi
         (:hoitokauden-alkuvuosi lomake-tila))]])
   [:div
    [kentat/tee-kentta
     {:tyyppi :checkbox
      :teksti "Poista rajoitukset kaikilta hoitovuosilta?" :vayla-tyyli? true
      :nayta-rivina? true :iso-clickalue? true}
     poista-kaikilta-vuosilta?-atom]]])

(defn- rajoituksen-poiston-varmistus-modaali-footer [e! {:keys [varmistus-fn] :as parametrit}]
  [:div.flex-row
   [napit/poista
    "Poista rajoitusalue"
    varmistus-fn
    {:vayla-tyyli? true
     :luokka "suuri tasaa-alkuun"}]
   [napit/yleinen-toissijainen
    "Peruuta"
    (r/partial (fn []
                 (modal/piilota!)))
    {:vayla-tyyli? true
     :luokka "suuri"}]])

(defn lomake-rajoitusalue-skeema [lomake muokkaustila?]
  (into []
    (concat
      [(lomake/rivi
         {:nimi :kopioidaan-tuleville-vuosille?
          :palstoja 3
          :tyyppi :checkbox
          :teksti (if muokkaustila?
                    "Tee muutokset myös tuleville hoitovuosille"
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
          ;:tarkkaile-ulkopuolisia-muutoksia? true
          :muokattava? (constantly false)
          ::lomake/col-luokka "col-xs-4"
          :rivi-luokka "lomakeryhman-rivi-tausta"}
         {:nimi :ajoratojen_pituus
          :otsikko "Pituus ajoradat (m)"
          :tyyppi :positiivinen-numero
          :vayla-tyyli? true
          :disabled? true
          ;:tarkkaile-ulkopuolisia-muutoksia? true
          :muokattava? (constantly false)
          ::lomake/col-luokka "col-xs-8"
          :rivi-luokka "lomakeryhman-rivi-tausta"})
       (lomake/ryhma
         {:ryhman-luokka "lomakeryhman-otsikko-tausta"
          :rivi? true}
         {:nimi :pohjavesialueet
          :otsikko "Pohjavesialue"
          :tyyppi :komponentti
          :komponentti (fn [{{:keys [pohjavesialueet]} :data}]
                         (if (seq pohjavesialueet)
                           (into [:div]
                             (mapv (fn [alue]
                                     ^{:key (hash alue)}
                                     [:div (str (:nimi alue) " (" (:tunnus alue) ")")])
                               pohjavesialueet))
                           [:div.grid-solu-varoitus "Tierekisteriosoite ei ole pohjavesialueella, tarkista osoite"]))


          ;:tarkkaile-ulkopuolisia-muutoksia? true
          :muokattava? (constantly false)
          ::lomake/col-luokka "col-xs-12"
          :rivi-luokka "lomakeryhman-rivi-tausta"})
       (lomake/ryhma
         {:otsikko "Suolarajoitus"
          :rivi? true
          :ryhman-luokka "lomakeryhman-otsikko-tausta"}
         {:nimi :suolarajoitus
          :otsikko "Suolan max määrä (NaCl ja CaCl2)"
          :tyyppi :positiivinen-numero
          :yksikko "t/ajoratakm"
          :piilota-yksikko-otsikossa? true
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
          :teksti "Alueella tulee käyttää formiaattia"
          :rivi-luokka "lomakeryhman-rivi-tausta"})])))

(defn lomake-rajoitusalue
  "Rajoitusalueen lisäys/muokkaus"
  [e! {:keys [lomake valittu-hoitovuosi] :as app} urakka]
  (let [saa-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-suola (:id urakka))
        ;; Aseta hoitovuosi lomakkeelle, mikäli sitä ei ole
        rajoituslomake (if (nil? (:hoitokauden-alkuvuosi lomake))
                         (assoc lomake :hoitokauden-alkuvuosi valittu-hoitovuosi)
                         lomake)
        muokkaustila? (boolean (:rajoitusalue_id rajoituslomake))
        disabled? (or (not (get-in app [:lomake ::tila/validi?]))  (:hae-tiedot-kaynnissa? app) (not saa-muokata?))]
    [:div.lomake-rajoitusalue
       #_ [debug/debug (:lomake app)]
     [lomake/lomake
      {:ei-borderia? true
       :voi-muokata? saa-muokata?
       :tarkkaile-ulkopuolisia-muutoksia? true
       :otsikko [:div (when saa-muokata?
                        (if muokkaustila? "Muokkaa rajoitusta" "Lisää rajoitusalue"))
                 [:div.small-text.harmaa
                  (str (urakka/hoitokauden-jarjestysnumero valittu-hoitovuosi (:loppupvm urakka)) ". hoitovuosi"
                    " (" (pvm/hoitokausi-str-alkuvuodesta-vuodet valittu-hoitovuosi) ")")]]
       :muokkaa! (r/partial #(e! (suolarajoitukset-tiedot/->PaivitaLomake % false)))
       :footer-fn (fn [lomake-tila]
                    [:div
                     (when (:onko-suolatoteumia? app)
                       [:div.flex-row {:style {:padding-bottom "1rem"}}
                        (if muokkaustila?
                          [yleiset/info-laatikko :neutraali "Rajoitusalueen tierekisteriosoitteen muokkaaminen vaikuttaa siihen, miten suolamäärät kohdistuvat
                        rajoitusalueille toteuma- ja raporttisivuilla. Muutokset tehdään yöllisinä ajoina ja ovat nähtävillä seuraavana päivänä."]
                          [yleiset/info-laatikko :neutraali "Rajoitusalueen lisääminen vaikuttaa siihen, miten suolamäärät kohdistuvat
                        rajoitusalueille toteuma- ja raporttisivuilla. Muutokset tehdään yöllisinä ajoina ja ovat nähtävillä seuraavana päivänä."])])
                     [:div.flex-row
                      [:div.alkuun {:style {:padding-left "0"}}
                       [napit/tallenna
                        (if muokkaustila? "Tallenna muutokset" "Tallenna")
                        #(e! (suolarajoitukset-tiedot/->TallennaLomake lomake-tila false))
                        {:disabled disabled? :paksu? true}]
                       (when-not muokkaustila?
                         [napit/tallenna
                          "Tallenna ja lisää seuraava"
                          #(e! (suolarajoitukset-tiedot/->TallennaLomake lomake-tila true))
                          {:disabled disabled? :paksu? true}])]
                      [:div.loppuun
                       (when muokkaustila?
                         [napit/poista
                          "Poista"
                          #(let [poista-kaikilta-vuosilta?-atom (atom false)]
                             (modal/nayta!
                               {:otsikko "Rajoitusalueen poistaminen"
                                :footer [rajoituksen-poiston-varmistus-modaali-footer e!
                                         {:varmistus-fn
                                          (fn []
                                            (modal/piilota!)
                                            (e! (suolarajoitukset-tiedot/map->PoistaSuolarajoitus
                                                  {:rajoitusalue_id (:rajoitusalue_id lomake-tila)
                                                   :hoitokauden-alkuvuosi (if @poista-kaikilta-vuosilta?-atom
                                                                            ;; Jos poistetaan kaikilta vuosilta, niin asetetaan alkuvuodeksi
                                                                            ;; urakan ensimmäinen hoitokausi
                                                                            (pvm/vuosi (:alkupvm urakka))
                                                                            (:hoitokauden-alkuvuosi lomake-tila))
                                                   :kopioidaan-tuleville-vuosille?
                                                   (or
                                                     ;; Kun poistetaan kaikilta vuosilta, niin aloitetaan urakan ensimmäisestä hoitokaudesta
                                                     ;; ja jatketaan myös tuleville vuosille
                                                     @poista-kaikilta-vuosilta?-atom
                                                     (:kopioidaan-tuleville-vuosille? lomake-tila))})))}]}
                               [rajoituksen-poiston-varmistus-modaali e! {:lomake-tila lomake-tila
                                                                          :poista-kaikilta-vuosilta?-atom poista-kaikilta-vuosilta?-atom
                                                                          :urakka urakka}]))
                          {:vayla-tyyli? true
                           :disabled disabled?}])
                       [napit/yleinen-toissijainen
                        "Peruuta"
                        #(e! (suolarajoitukset-tiedot/->AvaaTaiSuljeSivupaneeli false nil))
                        {:paksu? true}]]]])}

      (lomake-rajoitusalue-skeema rajoituslomake muokkaustila?)
      rajoituslomake]]))


;; -------

(defn lomake-talvisuolan-kayttoraja-mhu
  "Talvisuolan käyttörajan lomake mhu urakoille ('teiden-hoito'-tyyppi)"
  [e! app urakka]
  (let [saa-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-suola (:id urakka))
        valittavat-indeksit (map :indeksinimi (i/urakkatyypin-indeksit :hoito))
        lomake (get-in app [:kayttorajat :talvisuolan-sanktiot])
        ;; Aseta tuleville vuosille kopiointi defaulttina päälle, jos sitä ei ole erikseen estetty
        lomake (if (nil? (:kopioi-rajoitukset lomake))
              (assoc lomake :kopioi-rajoitukset true)
              lomake)]
    [:div
     [lomake/lomake {:ei-borderia? true
                     :tarkkaile-ulkopuolisia-muutoksia? false
                     :muokkaa! (fn [data]
                                 (e! (suolarajoitukset-tiedot/->PaivitaKayttorajalomakeMHU data))
                                 ;; Lomake ei tunnista on-blur komentoa alasvetovalikoista. Joten tulkitaan tässä, että onko alasvetovalikon tila muuttunut
                                 (when (= :indeksi (:harja.ui.lomake/viimeksi-muokattu-kentta data))
                                   (e! (suolarajoitukset-tiedot/->TallennaKayttorajalomakeMHU))))
                     :blurrissa! (fn [data] (e! (suolarajoitukset-tiedot/->TallennaKayttorajalomakeMHU)))}
      [(lomake/rivi
         {:nimi :kopioi-rajoitukset
          :tyyppi :checkbox
          :palstoja 1
          :teksti "Kopioi rajoitukset tuleville hoitovuosille"})
       (lomake/rivi
         {:nimi :talvisuolan-kayttoraja
          :tyyppi :positiivinen-numero
          :palstoja 1
          :otsikko "Talvisuolan käyttöraja / vuosi (kuivatonnia)"
          :placeholder "Ei rajoitusta"
          :yksikko "t"
          :piilota-yksikko-otsikossa? true
          :vayla-tyyli? true
          :disabled? true}
         {:nimi :talvisuolan-kayttoraja-info
          :tyhja-otsikko? true
          :tyyppi :komponentti
          :komponentti (fn []
                         [yleiset/tooltip {:suunta :oikea :leveys :levea
                                           :wrapper-luokka "tooltip-wrapper"}
                          [ikonit/harja-icon-status-info]
                          [:div
                           "Talvisuolan käyttöraja kirjataan Tehtävät ja määrät -välilehdellä kohdassa " [:b "Suolaus."]]])})

       (lomake/rivi
         {:nimi :sanktio_ylittavalta_tonnilta
          :tyyppi :positiivinen-numero
          :palstoja 1
          :muokattava? (constantly saa-muokata?)
          :otsikko "Sanktio / ylittävä tonni"
          :yksikko "€"
          :piilota-yksikko-otsikossa? true
          :vayla-tyyli? true}
         {:otsikko "Indeksi"
          :nimi :indeksi
          :tyyppi :komponentti
          :komponentti (fn [_]
                         [:div.kentta-indeksi
                          ;; Talvisuolan kokonaismäärän käyttörajalla ei ole tällä hetkellä indeksiä missään urakassa
                          [:div "Ei indeksiä"]])
          :palstoja 1})]
      lomake]]))

(defn lomake-talvisuolan-kayttoraja-alueurakka
  "Talvisuolan käyttörajan lomake alueurakoille ('hoito'-tyyppi)"
  [e! app urakka]
  (let [saa-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-suola (:id urakka))
        lomake (get-in app [:kayttorajat :talvisuolan-sanktiot])
        tarkasta-sakko-ja-bonus (fn [_ {sakko-tai-bonus :suolasakko-tai-bonus-maara vain-sakko :vain-sakko-maara}]
                                  (when (and (number? vain-sakko)
                                          (number? sakko-tai-bonus)
                                          (not= vain-sakko sakko-tai-bonus)
                                          (not= 0 vain-sakko)
                                          (not= 0 sakko-tai-bonus))
                                    "Sakko/bonus ja sakko eivät saa olla eri arvoja. Käytetään Sakko/bonus arvoa."))]
    [:div
     [lomake/lomake {:ei-borderia? true
                     :muokkaa! (fn [data]
                                 (e! (suolarajoitukset-tiedot/->PaivitaKayttorajalomakeAlueurakka data))
                                 ;; Lomake ei tunnista on-blur komentoa alasvetovalikoista.
                                 ;; Joten tulkitaan tässä, että onko alasvetovalikon tila muuttunut
                                 (when (some #(= (:harja.ui.lomake/viimeksi-muokattu-kentta data) %) #{:suolasakko-kaytossa :maksukuukausi})
                                   (e! (suolarajoitukset-tiedot/->TallennaKayttorajalomakeAlueurakka))))
                     :blurrissa! (fn [data] (e! (suolarajoitukset-tiedot/->TallennaKayttorajalomakeAlueurakka)))}
      [{:teksti "Suolasakko käytössä"
        :nimi :suolasakko-kaytossa :tyyppi :checkbox :palstoja 2
        :muokattava? (constantly saa-muokata?) :nayta-rivina? true
        :vayla-tyyli? true}
       {:otsikko "Talvisuolan käyttöraja / vuosi (kuivatonnia)"
        :nimi :talvisuolan-kayttoraja :tyyppi :positiivinen-numero :palstoja 1
        :placeholder "Ei rajoitusta"
        :muokattava? (constantly saa-muokata?)
        :yksikko "t" :piilota-yksikko-otsikossa? true
        :vayla-tyyli? true}
       {:otsikko "Maksukuukausi" :nimi :maksukuukausi :tyyppi :valinta :palstoja 1
        :valinta-arvo first
        :muokattava? (constantly saa-muokata?)
        :valinta-nayta #(if (not saa-muokata?)
                          ""
                          (if (nil? %) yleiset/+valitse-kuukausi+ (second %)))
        :valinnat [[5 "Toukokuu"] [6 "Kesäkuu"] [7 "Heinäkuu"]
                   [8 "Elokuu"] [9 "Syyskuu"]]
        :vayla-tyyli? true}
       {:otsikko "Suola\u00ADsakko/bonus / ylittävä tonni"
        :nimi :suolasakko-tai-bonus-maara :tyyppi :positiivinen-numero :palstoja 1
        :muokattava? (constantly saa-muokata?)
        :yksikko "€" :piilota-yksikko-otsikossa? false
        :varoita [tarkasta-sakko-ja-bonus]
        :vihje "Jos urakassa käytössä sekä suolasakko että -bonus, täytä vain tämä"
        :vayla-tyyli? false}
       {:otsikko "Vain suola\u00ADsakko / ylittävä tonni"
        :nimi :vain-sakko-maara :tyyppi :positiivinen-numero :palstoja 1
        :muokattava? (constantly saa-muokata?)
        :yksikko "€" :piilota-yksikko-otsikossa? false
        :varoita [tarkasta-sakko-ja-bonus]
        :vihje "Jos urakassa käytössä vain suolasakko eikä bonusta, täytä vain tämä"
        :vayla-tyyli? false}

       {:otsikko "Indeksi"
        :nimi :indeksi
        :tyyppi :komponentti
        :komponentti (fn [_]
                       [:div.kentta-indeksi
                        ;; Näytetään käyttäjälle aina urakan indeksin nimi rajoitusalueiden suolasanktioissa.
                        ;; Indeksin nimi asetetaan aina automaattisesti back-endissä urakan indeksiksi. Käyttäjä ei saa valita sitä itse
                        (if (urakka/indeksi-kaytossa-sakoissa?)
                          [:div (-> @tila/yleiset :urakka :indeksi)]
                          [:div "Ei indeksiä"])])
        :palstoja 1}]
      lomake]]))


(defn lomake-pohjavesialueen-suolasanktio [e! app urakka]
  (let [saa-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-suola (:id urakka))
        ;; Aseta tuleville vuosille kopiointi defaulttina päälle, jos sitä ei ole erikseen estetty
        app (if (nil? (get-in app [:kayttorajat :rajoitusalueiden-suolasanktio :kopioi-rajoitukset]))
              (assoc-in app [:kayttorajat :rajoitusalueiden-suolasanktio :kopioi-rajoitukset] true)
              app)
        valittavat-indeksit (map :indeksinimi (i/urakkatyypin-indeksit :hoito))]

    [:div
     [lomake/lomake {:ei-borderia? true
                     :tarkkaile-ulkopuolisia-muutoksia? false
                     :muokkaa! (fn [data toinen]
                                 (do
                                   (e! (suolarajoitukset-tiedot/->PaivitaRajoitusalueidenSanktiolomake data))

                                   ;; Lomake ei tunnista on-blur komentoa alasvetovalikoista. Joten tulkitaan tässä, että onko alasvetovalikon tila muuttunut
                                   (when (= :indeksi (:harja.ui.lomake/viimeksi-muokattu-kentta data))
                                     (e! (suolarajoitukset-tiedot/->TallennaRajoitusalueidenSanktiolomake)))))

                     :blurrissa! (fn [data] (e! (suolarajoitukset-tiedot/->TallennaRajoitusalueidenSanktiolomake)))}
      [(lomake/rivi
         {:nimi :kopioi-rajoitukset
          :tyyppi :checkbox
          :palstoja 1
          :teksti "Kopioi rajoitukset tuleville hoitovuosille"})
       (lomake/rivi
         {:otsikko "Sanktio / ylittävä tonni"
          :pakollinen? true
          :muokattava? (constantly saa-muokata?)
          :nimi :sanktio_ylittavalta_tonnilta
          :tyyppi :positiivinen-numero
          :palstoja 1
          :yksikko "€"
          :piilota-yksikko-otsikossa? true
          :vayla-tyyli? true}
         {:otsikko "Indeksi"
          :nimi :indeksi
          :tyyppi :komponentti
          :komponentti (fn [_]
                         [:div.kentta-indeksi
                          ;; Näytetään käyttäjälle aina urakan indeksin nimi rajoitusalueiden suolasanktioissa.
                          ;; Indeksin nimi asetetaan aina automaattisesti back-endissä urakan indeksiksi. Käyttäjä ei saa valita sitä itse.
                          (if (urakka/indeksi-kaytossa-sakoissa?)
                            [:div (-> @tila/yleiset :urakka :indeksi)]
                            [:div "Ei indeksiä"])])
          :palstoja 1})]
      (get-in app [:kayttorajat :rajoitusalueiden-suolasanktio])]]))

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
    {:otsikko "Pituus (m)" :nimi :pituus :tasaa :oikea :leveys 0.5}
    {:otsikko "Pituus ajoradat (m)" :nimi :ajoratojen_pituus :tasaa :oikea :leveys 0.75}
    {:otsikko "Pohjavesialue (tunnus)" :nimi :pohjavesialueet
     :luokka "sarake-pohjavesialueet"
     :tyyppi :komponentti
     :komponentti (fn [{:keys [pohjavesialueet]}]
                    (if (seq pohjavesialueet)
                      (into [:div]
                        (mapv (fn [alue]
                                [:div (str (:nimi alue) " (" (:tunnus alue) ")")])
                          pohjavesialueet))
                      [:div.grid-solu-varoitus "Tierekisteriosoite ei ole pohjavesialueella, tarkista osoite"]))
     :leveys 2}
    {:otsikko "Suolankäyttöraja (t/ajoratakm)" :nimi :suolarajoitus :tasaa :oikea
     :fmt #(if % (fmt/desimaaliluku % 1) "–") :leveys 0.8}
    {:otsikko "" :nimi :formiaatti :fmt #(when % "Käytettävä formiaattia") :leveys 0.8}]
   rajoitukset])

(defn urakan-suolarajoitukset* [e! app]
  (komp/luo
    (komp/sisaan
      #(do
         (e! (suolarajoitukset-tiedot/->TarkistaOnkoSuolatoteumia))
         (e! (suolarajoitukset-tiedot/->HaeSuolarajoitukset (pvm/vuosi (first @urakka/valittu-hoitokausi))))
         (e! (suolarajoitukset-tiedot/->HaeTalvisuolanKayttorajat (pvm/vuosi (first @urakka/valittu-hoitokausi))))))
    (komp/ulos
      (fn []
        ;; Tänne mahdolliset karttatasojen poistot
        ))

    (fn [e! app]
      (let [{:keys [alkupvm loppupvm]} (-> @tila/tila :yleiset :urakka) ;; Ota urakan alkamis päivä
            urakan-alkuvuosi (pvm/vuosi alkupvm)
            urakan-loppuvuosi (pvm/vuosi loppupvm)
            rajoitusalueet (:suolarajoitukset app)
            lomake-auki? (:rajoitusalue-lomake-auki? app)
            urakka @nav/valittu-urakka
            valittu-vuosi (if (nil? (:valittu-hoitovuosi app))
                            (pvm/vuosi (first @urakka/valittu-hoitokausi))
                            (:valittu-hoitovuosi app))
            ;; Varmista, ettei vahingossa oteta niin uutta vuotta, että urakka ei tue sellaista
            valittu-vuosi (if (> valittu-vuosi urakan-loppuvuosi)
              urakan-loppuvuosi
              (:valittu-hoitovuosi app))
            hoitovuodet (into [] (range urakan-alkuvuosi (+ 5 urakan-alkuvuosi)))
            saa-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-suola (:id urakka))]
        [:div.urakan-suolarajoitukset
         [:h2 "Urakan suolarajoitukset hoitovuosittain"]
         #_ [debug/debug app]

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

         [:div.kayttoraja-lomakkeet
          [:h3 "Talvisuolan kokonaismäärän käyttöraja"]
          (case (:tyyppi urakka)
            ;; MHU talvisuolan käyttöraja lomake
            :teiden-hoito
            (if (get-in app [:kayttorajat :talvisuolan-sanktiot])
              [lomake-talvisuolan-kayttoraja-mhu e! app urakka]
              [yleiset/ajax-loader "Ladataan..."])

            ;; Alueurakka talvisuolan käyttöraja lomake
            :hoito
            (if (get-in app [:kayttorajat :talvisuolan-sanktiot])
              [lomake-talvisuolan-kayttoraja-alueurakka e! app urakka]
              [yleiset/ajax-loader "Ladataan..."])

            [yleiset/virheviesti-sailio "Tuntematon urakkatyyppi"])

          (when (= :teiden-hoito (:tyyppi urakka))
            [:<>
             [:h3 "Pohjavesialueen suolasanktio"]
             (if (get-in app [:kayttorajat :rajoitusalueiden-suolasanktio])
               ;; Ladataan rajoitusalueiden ylityksen määrittävä lomake, nimestä huolimatta. Käyttöliittymässä on historian painolastista johtuen
               ;; paljon pohjavesialue termiä, vaikka käytännössä käsitellään rajoitusalueita. (joita voi olla monta yhdellä pohjavesialueella)
               [lomake-pohjavesialueen-suolasanktio e! app urakka]
               [yleiset/ajax-loader "Ladataan..."])])]

         ;; Pohjavesialueiden rajoitusalueiden taulukko ym.
         [:div.pohjavesialueiden-suolarajoitukset
          [:div.header
           [:h3 {:class "pull-left"}
            "Pohjavesialueiden suolarajoitukset"]
           [napit/uusi "Lisää rajoitusalue"
            #(e! (suolarajoitukset-tiedot/->AvaaTaiSuljeSivupaneeli true {:kopioidaan-tuleville-vuosille? true}))
            {:luokka "pull-right"
             :disabled (not saa-muokata?)}]]

          ;; TODO: Kartta
          #_[kartta]

          ;; Rajoitusalueen lisäys/muokkauslomake. Avautuu sivupaneeliin
          (when lomake-auki?
            [:div.overlay-oikealla {:style {:width "570px" :overflow "auto"}}
             [lomake-rajoitusalue e! app urakka]])

          [taulukko-rajoitusalueet e! rajoitusalueet saa-muokata?]]]))))

(defn urakan-suolarajoitukset []
  (tuck/tuck tila/suunnittelu-suolarajoitukset urakan-suolarajoitukset*))
