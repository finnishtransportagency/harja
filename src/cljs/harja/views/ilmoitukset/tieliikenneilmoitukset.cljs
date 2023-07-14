(ns harja.views.ilmoitukset.tieliikenneilmoitukset
  "Tieliikenneilmoituksien pääsivu."
  (:require [harja.tiedot.ilmoitukset.tieliikenneilmoitukset :as tiedot]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tietyoilmoitukset-tiedot]
            [harja.domain.tieliikenneilmoitukset :refer
             [kuittausvaatimukset-str ilmoitustyypin-lyhenne-ja-nimi
              +ilmoitusten-selitteet+ kuittaustyypin-selite kuittaustyypin-lyhenne
              tilan-selite vaikutuksen-selite] :as domain]
            [harja.domain.palautevayla-domain :as palautevayla]
            [harja.tuck-remoting.ilmoitukset-ohjain :as ilmoitukset-ws]
            [harja.ui.bootstrap :as bs]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :as kentat]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.raportit :as raportit]
            [harja.ui.napit :as napit]
            [harja.ui.lomake :as lomake]
            [harja.ui.protokollat :as protokollat]
            [harja.fmt :as fmt]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.views.kartta :as kartta]
            [harja.views.ilmoituskuittaukset :as kuittaukset]
            [harja.views.ilmoitukset.ilmoituksen-tiedot :as it]
            [harja.views.ilmoitukset.tietyoilmoitukset :as tietyoilmoitukset-view]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.notifikaatiot :as notifikaatiot]
            [tuck.core :refer [tuck]]
            [harja.tiedot.ilmoitukset.viestit :as v]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.kartta :as kartta-tiedot]
            [clojure.string :as str])
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

(defn vihjeet [{ws-kuuntelu-aktiivinen? :aktiivinen? :as ws-ilmoitusten-kuuntelu}]
  [yleiset/vihje-elementti
   [:div
    [:div
     [:span "Ilmoituksia päivitetään automaattisesti. Yhteydenottopyynnöt "]
     [:span.bold "lihavoidaan"]
     [:span ", edellinen valinta korostetaan "]
     [:span.vihje-hento-korostus "sinisellä"]
     [:span ", virka-apupyynnöt "]
     [:span.selite-virkaapu "punaisella"]
     [:span " selitelaatikossa."]]
    [:div [:i (if ws-kuuntelu-aktiivinen?
                 "Uusien ilmoitusten reaaliaikahaku aktiivinen"
                 (str "Uusia ilmoituksia haetaan " (/ tiedot/taustahaun-viive-ms 1000) " sekunnin välein."))]]]])

(defn ilmoituksen-tiedot [e! ilmoitus aiheet-ja-tarkenteet]
  [:div
   [:span
    [:div.margin-vertical-16
     [napit/takaisin "Listaa ilmoitukset" #(e! (v/->PoistaIlmoitusValinta)) {:luokka "nappi-reunaton"} ]]
    [it/ilmoitus e! ilmoitus aiheet-ja-tarkenteet]]])

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

(defn ilmoitusten-hakuehdot [e! valinnat-nyt aiheet-ja-tarkenteet]
  (let [valittu-aihe (:aihe valinnat-nyt)
        tarkenteet (filter
                     #(or (nil? valittu-aihe)
                        (= valittu-aihe (:aihe-id %)))
                     (flatten (map :tarkenteet aiheet-ja-tarkenteet)))]
    [lomake/lomake
     {:luokka "css-grid sm-css-block css-grid-columns-12x1 css-grid-columns-gap-16 padding-horizontal-16"
      :muokkaa! #(e! (v/->AsetaValinnat %))}
     [(valinnat/aikavalivalitsin "Tiedotettu urakkaan aikavälillä"
        tiedot/aikavalit
        (merge valinnat-nyt {:palstoita-vapaa-aikavali? true})
        {:vakioaikavali :valitetty-urakkaan-vakioaikavali
         :alkuaika :valitetty-urakkaan-alkuaika
         :loppuaika :valitetty-urakkaan-loppuaika}
        false
        {:rivi-luokka "grid-column-end-span-2"
         :aikavalivalitsin-flex? true
         :palstoja 2
         :vayla-tyyli? true})
      (valinnat/aikavalivalitsin "Toimenpiteet aloitettu"
        tiedot/toimenpiteiden-aikavalit
        (merge valinnat-nyt {:palstoita-vapaa-aikavali? true})
        {:vakioaikavali :toimenpiteet-aloitettu-vakioaikavali
         :alkuaika :toimenpiteet-aloitettu-alkuaika
         :loppuaika :toimenpiteet-aloitettu-loppuaika}
        false
        {:rivi-luokka "grid-column-end-span-2"
         :aikavalivalitsin-flex? true
         :palstoja 2
         :vayla-tyyli? true})
      {:nimi :hakuehto :otsikko "Hakusana"
       :placeholder "Hae tekstillä..."
       :tyyppi :string
       :vayla-tyyli? true
       :pituus-max 64
       :rivi-luokka "grid-column-end-span-4"
       ::lomake/col-luokka "width-full"
       :palstoja 2}
      {:nimi :aihe
       :palstoja 2
       :rivi-luokka "grid-column-end-span-2"
       ::lomake/col-luokka "width-full"
       :vayla-tyyli? true
       :otsikko "Aihe"
       :placeholder ""
       :tyyppi :valinta
       :jos-tyhja "Aiheita ei saatavilla"
       :valinnat (if
                   (seq aiheet-ja-tarkenteet) (into [nil] (map :aihe-id aiheet-ja-tarkenteet))
                   [])
       :valinta-nayta #(or (palautevayla/hae-aihe aiheet-ja-tarkenteet %) "Ei rajoitusta")}
      {:nimi :tarkenne
       :palstoja 2
       :rivi-luokka "grid-column-end-span-2"
       ::lomake/col-luokka "width-full"
       :otsikko "Tarkenne"
       :jos-tyhja (cond
                    (empty? tarkenteet)
                    "Aiheella ei tarkenteita"
                    :else
                    "")
       :vayla-tyyli? true
       :tyyppi :valinta
       :valinnat (if (seq tarkenteet)
                   (into [nil] (map :tarkenne-id tarkenteet))
                   [])
       :valinta-nayta #(or (palautevayla/hae-tarkenne aiheet-ja-tarkenteet %) "Ei valintaa")}
      {:nimi :tr-numero
       :palstoja 2
       :rivi-luokka "grid-column-end-span-1"
       ::lomake/col-luokka "width-full"
       :vayla-tyyli? true
       :otsikko "Tienumero"
       :placeholder "Rajaa tienumerolla"
       :tyyppi :positiivinen-numero :kokonaisluku? true}
      {:nimi :tunniste
       :palstoja 2
       :rivi-luokka "grid-column-end-span-2"
       ::lomake/col-luokka "width-full"
       :vayla-tyyli? true
       :otsikko "Tunniste"
       :placeholder "Rajaa tunnisteella"
       :tyyppi :string}
      {:nimi :ilmoittaja-nimi
       :palstoja 2
       :rivi-luokka "grid-column-end-span-5"
       ::lomake/col-luokka "width-full"
       :vayla-tyyli? true
       :otsikko "Ilmoittajan nimi"
       :placeholder "Rajaa ilmoittajan nimellä"
       :tyyppi :string}
      {:nimi :ilmoittaja-puhelin
       :palstoja 2
       :rivi-luokka "grid-column-end-span-4"
       ::lomake/col-luokka "width-full"
       :vayla-tyyli? true
       :otsikko "Ilmoittajan puhelinnumero"
       :placeholder "Rajaa ilmoittajan puhelinnumerolla"
       :tyyppi :puhelin}
      {:nimi :selite
       :palstoja 2
       :otsikko "Selite"
       :placeholder "Hae ja valitse selite"
       :tyyppi :haku
       :hae-kun-yli-n-merkkia 0
       :nayta second :fmt second
       :lahde selitehaku
       :rivi-luokka "grid-column-end-span-12"
       ::lomake/col-luokka "width-half"}
      {:nimi :tilat
       :otsikko "Tila"
       :tyyppi :checkbox-group
       :vayla-tyyli? true
       :vaihtoehdot tiedot/tila-filtterit
       :palstoja 2
       :rivi-luokka "grid-column-end-span-3"
       ::lomake/col-luokka "width-full"
       :vaihtoehto-nayta tilan-selite}
      {:nimi :tyypit
       :otsikko "Tyyppi"
       :tyyppi :checkbox-group
       :vayla-tyyli? true
       :palstoja 2
       :rivi-luokka "grid-column-end-span-3"
       ::lomake/col-luokka "width-full"
       :vaihtoehdot [:toimenpidepyynto :tiedoitus :kysely]
       :vaihtoehto-nayta ilmoitustyypin-lyhenne-ja-nimi}
      {:nimi :vaikutukset
       :otsikko "Vaikutukset"
       :tyyppi :checkbox-group
       :palstoja 1
       :rivi-luokka "grid-column-end-span-3"
       ::lomake/col-luokka "width-full"
       :vayla-tyyli? true
       :vaihtoehdot tiedot/vaikutukset-filtterit
       :vaihtoehto-nayta vaikutuksen-selite
       :vihje kuittausvaatimukset-str}
      {:nimi :aloituskuittauksen-ajankohta
       :otsikko "Aloituskuittaus annettu"
       :rivi-luokka "grid-column-end-span-2"
       ::lomake/col-luokka "width-full"
       :vayla-tyyli? true
       :tyyppi :radio-group
       :palstoja 2
       :vaihtoehdot [:kaikki :alle-tunti :myohemmin]
       :vaihtoehto-nayta (fn [arvo]
                           ({:kaikki "Älä rajoita aloituskuittauksella"
                             :alle-tunti "Alle tunnin kuluessa"
                             :myohemmin "Yli tunnin päästä"}
                            arvo))}]
     valinnat-nyt]))

(defn ilmoitustyypin-selite [ilmoitustyyppi]
  (let [tyyppi (domain/ilmoitustyypin-lyhenne ilmoitustyyppi)]
    [:div {:class [tyyppi "text-nowrap"]} tyyppi]))

(defn tunniste-tooltip [tunniste]
  [:div
   [:div.harmaa-teksti "Tunniste"]
   [:span (or tunniste "-")]])

(defn- tarkenne-tai-selite-teksti [aiheet-ja-tarkenteet {:keys [tarkenne selitteet]}]
  (or (palautevayla/hae-tarkenne aiheet-ja-tarkenteet tarkenne)
    (when selitteet
      (str
        (if (= 1 (count selitteet))
          "Selite:\n"
          "Selitteet:\n")
        (domain/parsi-selitteet selitteet)))))

(defn- parsi-palauteluokitus
  "Ilmoitusten luokittelun käyttöönoton välivaiheessa ilmoitusten lisätieto-kentässä on lähetetty aihe ja tarkenne
  tekstimuotoisena. Yritetään parsia tällaiset ja näytetään niistä aihe ja tarkenne ikään kuin ne olisi tulleet
  uuden mallisena."
  [{:keys [aihe tarkenne lisatieto] :as ilmoitus} palauteluokitukset]
  (if (and (nil? aihe) (nil? tarkenne) lisatieto)
    (let [aiheet-ilmoituksessa (filter
                                 #(str/includes? lisatieto (str "Aihe: " (:nimi %)))
                                 palauteluokitukset)
          tarkenteet-ilmoituksessa (filter
                                     #(str/includes? lisatieto (str "Lisätieto: " (:nimi %)))
                                     (flatten (map :tarkenteet palauteluokitukset)))
          aihe-ilmoituksessa (first aiheet-ilmoituksessa)
          tarkenne-ilmoituksessa (first tarkenteet-ilmoituksessa)]
      (cond-> ilmoitus
        (= 1 (count aiheet-ilmoituksessa))
        (->
          (assoc :aihe (:aihe-id aihe-ilmoituksessa))
          (update :lisatieto #(str/replace % (str "Aihe: " (:nimi aihe-ilmoituksessa) " ") "")))

        (and (= 1 (count aiheet-ilmoituksessa)) (= 1 (count tarkenteet-ilmoituksessa)))
        (->
          (assoc :tarkenne (:tarkenne-id (first tarkenteet-ilmoituksessa)))
          (update :lisatieto #(str/replace % (str "Lisätieto: " (:nimi tarkenne-ilmoituksessa) " ") "")) )))
    ilmoitus))


(defn ilmoitusten-paanakyma
  [e! {ws-ilmoitusten-kuuntelu :ws-ilmoitusten-kuuntelu
       valinnat-nyt :valinnat
       kuittaa-monta :kuittaa-monta
       haetut-ilmoitukset :ilmoitukset
       ilmoituksen-haku-kaynnissa? :ilmoituksen-haku-kaynnissa?
       pikakuittaus :pikakuittaus
       aiheet-ja-tarkenteet :aiheet-ja-tarkenteet :as ilmoitukset}]

  (let [{valitut-ilmoitukset :ilmoitukset :as kuittaa-monta-nyt} kuittaa-monta
        valitse-ilmoitus! (when kuittaa-monta-nyt
                            #(e! (v/->ValitseKuitattavaIlmoitus %)))
        pikakuittaus-ilmoitus-id (when pikakuittaus
                                   (get-in pikakuittaus [:ilmoitus :id]))

        tunteja-valittu (-> valinnat-nyt :valitetty-urakkaan-vakioaikavali :tunteja)
        vapaa-alkuaika (-> valinnat-nyt :valitetty-urakkaan-alkuaika)
        vapaa-loppuaika (-> valinnat-nyt :valitetty-urakkaan-loppuaika)
        tuntia-sitten (pvm/tuntia-sitten tunteja-valittu)
        valittu-alkupvm (if tunteja-valittu tuntia-sitten vapaa-alkuaika)
        valittu-loppupvm (if tunteja-valittu (pvm/nyt) vapaa-loppuaika)
        haetut-ilmoitukset (map #(parsi-palauteluokitus % aiheet-ja-tarkenteet) haetut-ilmoitukset)]


    [:span.ilmoitukset
     [debug/debug ilmoitukset]

     [ilmoitusten-hakuehdot e! valinnat-nyt aiheet-ja-tarkenteet]
     [:div
      [:div.margin-top-16
       [kentat/tee-kentta {:tyyppi :checkbox
                           :teksti "Äänimerkki uusista ilmoituksista"}
        tiedot/aanimerkki-uusista-ilmoituksista?]]

      ;; FIXME: Tämä on väliaikainen toiminto WS-kuuntelijan testikäyttöä varten.
      ;;        Käyttäjä voi aktivoida/deaktivoida WS-kuuntelun.
      ;;        Asetus tallennnetaan localstorageen, jolloin valittu asetus on aktiivinen myös refreshin jälkeen.

      [:div.margin-top-16
       [kentat/tee-kentta {:tyyppi :checkbox
                           :teksti "Aktivoi kokeellinen ilmoitusten reaaliaikahaku (testikäyttö)"}
        tiedot/ws-kuuntelija-ominaisuus?]]

      [vihjeet ws-ilmoitusten-kuuntelu]

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
                         "ilmoitusrivi-fade")
        
        :raporttivienti #{:excel :pdf}
        :raporttivienti-lapinakyva? true
        :raporttiparametrit (raportit/urakkaraportin-parametrit
                              (:id @nav/valittu-urakka)
                              :ilmoitukset-raportti
                              {:urakka @nav/valittu-urakka
                               :hallintayksikko @nav/valittu-hallintayksikko
                               :tiedot haetut-ilmoitukset
                               :filtterit @tiedot/ilmoitukset
                               :alkupvm valittu-alkupvm
                               :loppupvm valittu-loppupvm
                               :urakkatyyppi (:tyyppi @nav/valittu-urakka)})}

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
          {:otsikko "Urakka" :otsikkorivi-luokka "urakka" :leveys ""
           :hae #(or (:lyhytnimi %) (fmt/lyhennetty-urakan-nimi (:urakkanimi %)))
           :solun-tooltip (fn [rivi]
                            (if (= (:urakkanimi rivi) (or (:lyhytnimi rivi) (fmt/lyhennetty-urakan-nimi (:urakkanimi rivi))))
                              nil {:teksti (:urakkanimi rivi)}))})
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
        {:otsikko "Tarkenne" :nimi :tarkenne
         :tyyppi :string
         :luokka "pitka-teksti"
         :hae (partial tarkenne-tai-selite-teksti aiheet-ja-tarkenteet)
         :otsikkorivi-luokka "selite" :leveys ""}
        {:otsikko "Kuvaus" :nimi :lisatieto :otsikkorivi-luokka "lisatieto"
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

(defn- ilmoitukset* [e! {valinnat :valinnat :as ilmoitukset-tila}]
  ;; Kun näkymään tullaan, yhdistetään navigaatiosta tulevat valinnat
  (e! (v/->YhdistaValinnat @tiedot/valinnat))

  (komp/luo
    (komp/lippu tiedot/karttataso-ilmoitukset)
    (komp/kuuntelija :ilmoitus-klikattu (fn [_ i] (e! (v/->ValitseIlmoitus (:id i)))))
    (komp/watcher tiedot/valinnat (fn [_ vanhat-valinnat uudet-valinnat]
                                    (e! (v/->YhdistaValinnat uudet-valinnat))
                                    ;; Aloita WS-ilmoitusten kuuntelu uudestaan, mikäli valinnat muuttuvat.
                                    ;; Tarkkaillaan valinnoista vain "perussuodattiemien" muutoksia, jotta palvelinta
                                    ;; ei kuormiteta liikaa muutoksilla palvelinpuolen kuuntelijoihin.
                                    (let [vanhat (select-keys vanhat-valinnat [:urakka :urakkatyyppi :urakoitsija :hallintayksikko])
                                          uudet (select-keys uudet-valinnat [:urakka :urakkatyyppi :urakoitsija :hallintayksikko])]
                                      (when (not= vanhat uudet)
                                        (when @tiedot/ws-kuuntelija-ominaisuus?
                                          (e! (ilmoitukset-ws/->AloitaKuuntelu uudet-valinnat)))))))

    ;; FIXME: Tämä on väliaikainen ominaisuus WS-kuuntelijan testikäyttöä varten.
    (komp/watcher tiedot/ws-kuuntelija-ominaisuus?
      (fn [_ _ uusi-tila]
        (if (true? uusi-tila)
          (e! (ilmoitukset-ws/->AloitaYhteysJaKuuntelu valinnat))
          (e! (ilmoitukset-ws/->KatkaiseYhteys)))))
    (komp/sisaan-ulos #(do
                         (e! (v/->HaeAiheetJaTarkenteet))
                         (notifikaatiot/pyyda-notifikaatiolupa)
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (nav/vaihda-kartan-koko! :M)
                         (nav/vaihda-urakkatyyppi! {:nimi "Kaikki" :arvo :kaikki})
                         (when @nav/valittu-ilmoitus-id
                           (e! (v/->ValitseIlmoitus @nav/valittu-ilmoitus-id)))
                         (kartta-tiedot/kasittele-infopaneelin-linkit!
                           {:ilmoitus {:toiminto (fn [ilmoitus-infopaneelista]
                                                   (e! (v/->ValitseIlmoitus (:id ilmoitus-infopaneelista))))
                                       :teksti "Valitse ilmoitus"}})

                         ;; Aloita uusi WS-yhteys, sekä uusien ilmoituksien kuuntely WebSocketin kautta
                         ;; Kuuntelun aloittamisen yhteydessä annetaan käyttöliittymästä optioksi "valinnat",
                         ;; jotka toimivat suodattimina WebSocketin kautta vastaanotettaville ilmoituksille
                         ;; FIXME: Tämä on väliaikainen ehtolause WS-kuuntelijan testikäyttöä varten.
                         ;;        Otetaan tämä ehtolause pois käytöstä, jos WS-kuuntelu koetaan testeissä vakaaksi.
                         (when @tiedot/ws-kuuntelija-ominaisuus?
                           (e! (ilmoitukset-ws/->AloitaYhteysJaKuuntelu valinnat))))
                      #(do
                         (kartta-tiedot/kasittele-infopaneelin-linkit! nil)
                         (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)

                         ;; Katkaise WS-yhteys ja lopeta samalla uusien ilmoitusten kuuntelu WebSocketin kautta
                         ;; FIXME: Tämä on väliaikainen ehtolause WS-kuuntelijan testikäyttöä varten.
                         ;;        Otetaan tämä ehtolause pois käytöstä, jos WS-kuuntelu koetaan testeissä vakaaksi.
                         (when @tiedot/ws-kuuntelija-ominaisuus?
                           (e! (ilmoitukset-ws/->KatkaiseYhteys)))))
    (fn [e! {:keys [valittu-ilmoitus aiheet-ja-tarkenteet] :as ilmoitukset}]
      [:span
       [kartta/kartan-paikka]
       (if (and @nav/valittu-ilmoitus-id valittu-ilmoitus)
         [ilmoituksen-tiedot e! (parsi-palauteluokitus valittu-ilmoitus aiheet-ja-tarkenteet) aiheet-ja-tarkenteet]
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
