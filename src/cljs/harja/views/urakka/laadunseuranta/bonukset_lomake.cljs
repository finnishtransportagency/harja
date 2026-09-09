(ns harja.views.urakka.laadunseuranta.bonukset-lomake
  "Bonuksien käsittely ja luonti"
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<!]]

            [harja.pvm :as pvm]
            [harja.asiakas.kommunikaatio :as k]

            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.urakka :as u-domain]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]

            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.urakka :as uu-tiedot]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.urakka.laadunseuranta.bonukset :as tiedot]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot-sanktiot]

            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- hae-tpi-idlla
  [tpi-id]
  (some
    #(when (= tpi-id (:tpi_id %))
       %)
    @tiedot-urakka/urakan-toimenpideinstanssit))

(defn bonus-konfiguraation-hoitovuosi
  [urakan-alkupvm valittu-hoitokausi kasittelyaika]
  (let [hoitovuoden-paiva (or kasittelyaika
                            (first valittu-hoitokausi))]
    (when (and urakan-alkupvm hoitovuoden-paiva)
      (pvm/paivamaara->mhu-hoitovuosi-nro urakan-alkupvm hoitovuoden-paiva))))

(defn- bonus-lajivalinnat
  [bonus-konfiguraatio urakkatyyppi kayttaja-id tpi]
  (let [bonus-lajit (mapv :laji (tiedot/bonus-konfiguraation-lajit bonus-konfiguraatio))]
    (if (seq bonus-lajit)
      bonus-lajit
      (when (u-domain/yllapitourakka? urakkatyyppi)
        (sanktio-domain/luo-kustannustyypit urakkatyyppi kayttaja-id tpi)))))

(defn- bonus-konfiguraation-tila
  [bonus-konfiguraatio haku-kaynnissa?]
  (cond
    haku-kaynnissa?
    :haku-kaynnissa

    (seq (tiedot/bonus-konfiguraation-lajit bonus-konfiguraatio))
    :valmis

    (and (some? bonus-konfiguraatio)
      (k/virhe? bonus-konfiguraatio))
    :haku-epaonnistui

    :else
    :ei-konfiguraatiota))

(defn- bonus-lajin-nimi
  [bonus-konfiguraatio laji]
  (or (tiedot/bonus-konfiguraation-lajin-nimi bonus-konfiguraatio laji)
    (sanktio-domain/bonuslaji->teksti laji)
    "- Valitse tyyppi -"))

(defn bonus-lomake*
  "MH-urakoidan ja ylläpitourakoiden yhteinen bonuslomake.
  Huomioitavaa on, että ylläpidon urakoiden bonukset tallennetaankin oikeasti sanktioina, eikä bonuksina.
  Ylläpidon urakoiden bonuslomakkeessa on myös muita pieniä poikkeuksia."
  [sulje-fn lukutila? voi-muokata? bonus-konfiguraatio e! app]
  (let [{lomakkeen-tiedot :lomake :keys [uusi-liite voi-sulkea? liitteet-haettu?]} app
        urakka-id (:id @nav/valittu-urakka)
        urakan-alkupvm (:alkupvm @nav/valittu-urakka)
        urakan-loppupvm (:loppupvm @nav/valittu-urakka)
        urakan-tyyppi (:tyyppi @nav/valittu-urakka)
        mhu25? (uu-tiedot/mhu25-urakka? @nav/valittu-urakka)
        laskutuskuukaudet (tiedot-sanktiot/pyorayta-laskutuskuukausi-valinnat)

        ;; Lista ylläpitokohteista ylläpitourakoiden kohteenvalintaa varten
        yllapitokohteet (conj
                          @laadunseuranta/urakan-yllapitokohteet-lomakkeelle
                          {:id nil})
        ;; MHU urakoiden toimenpideinstanssi on määrätty. Alueurakoilla ei
        ;; Lisäksi alihankintabonus laitetaan MHU Ylläpidon alle, kun se tehdään 1.10.2022 jälkeen, muut Hoidon johtoon
        toimenpideinstanssit (cond
                               (= :teiden-hoito (:tyyppi @nav/valittu-urakka))
                               (filter #(= "23150" (:t2_koodi %)) @tiedot-urakka/urakan-toimenpideinstanssit)

                               ;; Muille urakakkatyypeille näytetään kaikki toimenpideinstanssit
                               :else
                               @tiedot-urakka/urakan-toimenpideinstanssit)
        hae-tpi-nimi-idlla (fn [tpi-id]
                          (some #(when (= tpi-id (:tpi_id %)) (:tpi_nimi %)) toimenpideinstanssit))
        urakan-alkuvuosi (pvm/vuosi urakan-alkupvm)
        urakan-loppuvuosi (pvm/vuosi urakan-loppupvm)
        urakan-loppukuukausi (pvm/kuukausi urakan-loppupvm)
        viimeinen-hoitovuoden-alkuvuosi (if (>= urakan-loppukuukausi 10)
                                          urakan-loppuvuosi
                                          (dec urakan-loppuvuosi))
        hoitovuodet (if (<= urakan-alkuvuosi viimeinen-hoitovuoden-alkuvuosi)
                      (vec (range urakan-alkuvuosi (inc viimeinen-hoitovuoden-alkuvuosi)))
                      [])
        hoitovuosi->teksti (fn [hoitovuosi]
                             (when (int? hoitovuosi)
                               (let [jarjestysnumero (inc (- hoitovuosi urakan-alkuvuosi))]
                                 (str jarjestysnumero ". hoitovuosi (" hoitovuosi " - " (inc hoitovuosi) ")"))))
        perintapvm->hoitovuosi (fn [perintapvm]
                                 (when perintapvm
                                   (let [vuosi (pvm/vuosi perintapvm)
                                         kuukausi (pvm/kuukausi perintapvm)
                                         hoitovuosi (if (>= kuukausi 10) vuosi (dec vuosi))]
                                     (when (some #{hoitovuosi} hoitovuodet)
                                       hoitovuosi))))]
    (when voi-sulkea? (e! (tiedot/->TyhjennaLomake sulje-fn)))
    (when-not liitteet-haettu? (e! (tiedot/->HaeLiitteet)))
    [lomake/lomake
     {:otsikko "BONUKSEN TIEDOT"
      :otsikko-elementti :h4
      :ei-borderia? true
      :vayla-tyyli? true
      :tarkkaile-ulkopuolisia-muutoksia? true
      :luokka "padding-16 taustavari-taso3"
      :validoi-alussa? false
      :voi-muokata? (and voi-muokata? (not lukutila?))
      :muokkaa! #(e! (tiedot/->PaivitaLomaketta %))
      :footer-fn (fn [bonus]
                   [:<>
                    [:span.nappiwrappi.flex-row
                     [:div.col-xs-8 {:style {:padding-left "0"}}
                      (when-not lukutila?
                        [napit/yleinen-ensisijainen "Tallenna" #(e! (tiedot/->TallennaBonus))
                         {:disabled (not (empty? (::lomake/puuttuvat-pakolliset-kentat bonus)))}])]
                     [:div.col-xs-4 {:style (merge
                                              {:text-align "end" :float "right"}
                                              (when (not lukutila?)
                                                {:display "contents"}))}
                      (when (and (not lukutila?) (:id lomakkeen-tiedot))
                        [napit/kielteinen "Poista" (fn [_]
                                                     (varmista-kayttajalta/varmista-kayttajalta
                                                       {:otsikko "Bonuksen poistaminen"
                                                        :sisalto "Haluatko varmasti poistaa bonuksen? Toimintoa ei voi perua."
                                                        :modal-luokka "varmistus-modal"
                                                        :hyvaksy "Poista"
                                                        :toiminto-fn #(e! (tiedot/->PoistaBonus))}))
                         {:luokka "oikealle"}])
                      [napit/peruuta "Sulje" #(e! (tiedot/->TyhjennaLomake sulje-fn))]]]])}
     [(let [hae-tpin-tiedot (comp hae-tpi-idlla :toimenpideinstanssi)
            tpi (hae-tpin-tiedot lomakkeen-tiedot)]
        {:otsikko "Bonus"
         ;; Laji on bonuksen tyyppi. Tämä on vastaava käsite kuin sanktion laji.
         :nimi :laji
         :tyyppi :valinta
         :pakollinen? true
         ;; Valitse ainoa, jos tyyppejä on vain yksi.
         ;; Esimerkiksi ylläpitourakoiden tapauksessa on saatavilla vain "yllapidon_bonus"
         :valitse-ainoa? true
         ;; Aseta toimenpideinstanssi, jos se voidaan tietää ennalta
         :aseta (fn [rivi arvo]
                  ;; Aseta toimenpideinstanssi, mikäli sitä ei ole asetettu ennakkoon oikein
                  (let [asetettava-tpi (cljs.core/cond (= :teiden-hoito (:tyyppi @nav/valittu-urakka))
                                         (first (filter #(= "23150" (:t2_koodi %)) @tiedot-urakka/urakan-toimenpideinstanssit))

                                         :else
                                         ;; Muuten otetaan vain listan ensimmäinen
                                         (first @tiedot-urakka/urakan-toimenpideinstanssit))]
                    (-> rivi
                      (assoc :toimenpideinstanssi (:tpi_id asetettava-tpi))
                      (assoc :laji arvo))))
         :valinnat (bonus-lajivalinnat bonus-konfiguraatio (:tyyppi @nav/valittu-urakka) (:id @istunto/kayttaja) tpi)
         :valinta-nayta #(bonus-lajin-nimi bonus-konfiguraatio %)
         ::lomake/col-luokka "col-xs-12"
         :validoi [[:ei-tyhja "Valitse laji"]]})

      (when @tiedot-urakka/yllapitourakka?
        {:otsikko "Kohde"
         :tyyppi :valinta
         :nimi :yllapitokohde
         :pakollinen? false
         :muokattava? (constantly voi-muokata?)
         ::lomake/col-luokka "col-xs-12"
         :valinnat yllapitokohteet
         :jos-tyhja "Ei valittavia kohteita"
         :valinta-nayta (fn [arvo voi-muokata?]
                          (if (:id arvo)
                            (yllapitokohde-domain/yllapitokohde-tekstina
                              arvo
                              {:osoite {:tr-numero (:tr-numero arvo)
                                        :tr-alkuosa (:tr-alkuosa arvo)
                                        :tr-alkuetaisyys (:tr-alkuetaisyys arvo)
                                        :tr-loppuosa (:tr-loppuosa arvo)
                                        :tr-loppuetaisyys (:tr-loppuetaisyys arvo)}})
                            (if (and voi-muokata? (not arvo))
                              "- Valitse kohde -"
                              (if (and voi-muokata? (nil? (:id arvo)))
                                "Ei liity kohteeseen"
                                ""))))})

      {:otsikko "Perustelu"
       :nimi :lisatieto
       :tyyppi :text
       :pakollinen? true
       ::lomake/col-luokka "col-xs-12"
       :validoi [[:ei-tyhja "Anna perustelu"]]}
      ;; näytä lukutilassa vain teksti
      (if (and voi-muokata? (not lukutila?) (not (= :teiden-hoito (:tyyppi @nav/valittu-urakka))))
       {:otsikko "Kulun kohdistus"
        :nimi :toimenpideinstanssi
        :pakollinen? true
        :tyyppi :komponentti
        ::lomake/col-luokka "col-xs-12"
        :komponentti (fn [{:keys [muokkaa-lomaketta data]}]
                       [:<>
                        [yleiset/livi-pudotusvalikko
                         {:valitse-oletus? true
                          :vayla-tyyli? true
                          :pakollinen? true
                          :format-fn :tpi_nimi
                          :valinta (first toimenpideinstanssit)
                          ;; Koska MHU urakoilla on määrätty toimenpideinstanssi, niin ei anneta käyttäjän vaihtaa, mutta alueurakoille se sallitaan
                          :disabled (if (= :teiden-hoito (:tyyppi @nav/valittu-urakka)) true false)
                          :valitse-fn #(muokkaa-lomaketta
                                         (assoc data :toimenpideinstanssi (:tpi_id %)))}
                         toimenpideinstanssit]])}
        {:otsikko "Kulun kohdistus"
         :tyyppi :string
         :nimi :toimenpideinstanssi
         :muokattava? (constantly false)
         ::lomake/col-luokka "col-xs-12"
         :hae (fn [rivi]
                (let [tpi-id (:toimenpideinstanssi rivi)]
                  (or (some #(when (= (:tpi_id %) tpi-id) (:tpi_nimi %))
                        @tiedot-urakka/urakan-toimenpideinstanssit)
                    "")))})
      (lomake/ryhma
        {:rivi? true}
        {:otsikko "Summa"
         :nimi :summa
         :tyyppi :euro
         :vaadi-positiivinen-numero? true
         :pakollinen? true
         ::lomake/col-luokka "col-xs-4"
         :validoi [[:ei-tyhja "Anna summa"] [:rajattu-numero 0 999999999 "Anna arvo väliltä 0 - 999 999 999"]]}
        ;; Indeksi näytetään vain 19/20 alkaneille urakoille
        (when (or (= 2019 urakan-alkuvuosi) (= 2020 urakan-alkuvuosi))
          (let [valinnat (when (and
                                 (<= urakan-alkuvuosi 2020)
                                 (= :asiakastyytyvaisyysbonus (:laji lomakkeen-tiedot)))
                           [(:indeksi @nav/valittu-urakka) nil])]
            {:otsikko "Indeksi"
             :nimi :indeksi
             :tyyppi :valinta
             :disabled? (nil? valinnat)
             ::lomake/col-luokka "col-xs-4"
             :valinnat (or valinnat [nil])
             :valinta-nayta #(or % "Ei indeksiä")})))
      (lomake/ryhma
        {:rivi? true}
        {:otsikko "Käsitelty"
         ;; Hox: Sanktioissa kasittelyaika päätyy laatupoikkeaman käsittelyajaksi, bonuksissa erilliskustannuksen pvm:ksi
         ;;      Käytetään käsittelyajasta bonuksienkin puolella laatupoikkeamista tuttua termiä.
         :nimi :kasittelyaika
         :tyyppi :pvm
         :pakollinen? true
         ::lomake/col-luokka "col-xs-4"
         :validoi [[:ei-tyhja "Valitse päivämäärä"]]
         :aseta (fn [rivi arvo]
                  (let [;; MHU25-urakoille laskutuskuukausi on aina 15.09. hoitokauden päättymisvuodelle
                        ;; Hoitokausi: 01.10.YYYY - 30.09.YYYY+1
                        ;; Jos käsittelyaika on loka-joulukuussa -> 15.09.(vuosi+1), muuten 15.9.(sama vuosi)
                        mhu25-perintapvm (when (and mhu25? arvo)
                                           (let [v (pvm/vuosi arvo)
                                                 kk (pvm/kuukausi arvo)
                                                 kohde-vuosi (if (>= kk 10) (inc v) v)]
                                             (pvm/->pvm (str "15.09." kohde-vuosi))))
                        mhu25-laskutuskuukausi-komp (when mhu25-perintapvm
                                                      (some #(when (and
                                                                     (= (pvm/vuosi mhu25-perintapvm) (:vuosi %))
                                                                     (= 9 (:kuukausi %)))
                                                               %)
                                                        laskutuskuukaudet))]
                    (cond-> rivi
                      ;; MHU25: asetetaan laskutuskuukausi automaattisesti hoitokauden syyskuulle
                      mhu25?
                      (-> (assoc :perintapvm mhu25-perintapvm)
                        (assoc :laskutuskuukausi-komp-tiedot mhu25-laskutuskuukausi-komp))

                      ;; Muille: Jos laskutuskuukautta ei ole vielä valittu ja bonusta ei ole tallennettu (id nil),
                      ;; niin asetetaan esivalintana perintapvm valittu kasittelyn pvm
                      (and (not mhu25?) (nil? (:laskutuskuukausi-komp-tiedot rivi)) (nil? (:id rivi)))
                      (assoc :perintapvm arvo)

                      ;; Tallennetaan aina valittu käsittelyaika :kasittelyaika avaimen alle
                      true
                      (assoc :kasittelyaika arvo))))}
        ;; HOX: Sanktion tapauksessa laskutuskuukausi tallennetaan sanktion 'perintapvm'-sarakkeeseen.
        ;;      Bonuksissa (erilliskustannus-taulu) ei ole perintapvm-saraketta, vaan laskutuskuukausi-sarake
        ;;      johon tämä tieto tallennetaan. Lisäksi, yllapidon_bonus tallennetaan poikkeuksellisesti sanktiona.
        ;;      Yhteneväisyyden vuoksi käytetään bonuslomakkeella laskutuskuukaudesta nimeä 'perintapvm'

        (cond
          ;; Muokkausitla ::  ennen -25 alkaneilla urakoilla on laskutuskuukausi, jonka voi valita
          (and (not mhu25?) voi-muokata? (not lukutila?))
          {:otsikko "Laskutuskuukausi"
           :nimi :perintapvm
           :pakollinen? true
           :tyyppi :komponentti
           ::lomake/col-luokka "col-xs-6"
           :huomauta [[:urakan-aikana-ja-hoitokaudella]]
           :komponentti (fn [{:keys [muokkaa-lomaketta data]}]
                          (if-not mhu25?
                            [:<>
                             [yleiset/livi-pudotusvalikko
                              {:data-cy "koontilaskun-kk-dropdown"
                               :vayla-tyyli? true
                               :skrollattava? true
                               :pakollinen? true
                               ;; MHU25-urakoille laskutuskuukausi määräytyy käsittelyajan perusteella, ei käyttäjän valinnasta
                               :disabled mhu25?
                               :valinta (or
                                          ;; Näytetään valintana joko valittua laskutuskuukautta, tai
                                          (-> data :laskutuskuukausi-komp-tiedot)
                                          ;; jos käyttäjä ei tehnyt/muuttanut valintaa, käytetään tietokannasta haettua arvoa
                                          (when (:perintapvm data)
                                            (some #(when (and
                                                           (= (pvm/vuosi (:perintapvm data))
                                                             (:vuosi %))
                                                           (= (pvm/kuukausi (:perintapvm data))
                                                             (:kuukausi %))) %)
                                              laskutuskuukaudet)))
                               :valitse-fn #(muokkaa-lomaketta
                                              (assoc data
                                                ;; Tallennetaan tieto koko laskutuskuukauden valinnasta erikseen, jotta
                                                ;;  sitä voi hyödyntää muualla lomakkeessa.
                                                :laskutuskuukausi-komp-tiedot %
                                                ;; Varsinainen perintapvm poimitaan valitun laskutuskuukauden pvm-kentästä.
                                                :perintapvm (:pvm %)))
                               :format-fn :teksti}
                              laskutuskuukaudet]
                             ;; Piilotetaan teksti ylläpitourakoilta, koska niillä ei ole laskutusyhteenvetoa
                             (when (not @tiedot-urakka/yllapitourakka?)
                               [:div.small-caption.padding-4 "Näkyy laskutusyhteenvedolla"])]

                            ;; Jos mhu25 urakka, niin ei voi muokata. Näytetään tekstinä
                            [:div (some #(when (and
                                                 (= (pvm/vuosi (:perintapvm data)) (pvm/vuosi (:pvm %)))
                                                 (= (pvm/kuukausi (:perintapvm data)) (pvm/kuukausi (:pvm %)))) (:teksti %))
                                    laskutuskuukaudet)]))}

          ;; Muokkaustila :: MHU25+ urakoilla bonuksen perintäpäivä laitetaan aina syyskuun 15 päiväksi
          (and mhu25? voi-muokata? (not lukutila?))
          {:otsikko "Kohdistuu hoitovuodelle"
           :nimi :perintapvm
           :pakollinen? true
           :tyyppi :valinta
           :valinnat hoitovuodet
           :hae #(perintapvm->hoitovuosi (:perintapvm %))
           :aseta (fn [rivi hoitovuosi]
                    (assoc rivi :perintapvm
                      (when hoitovuosi
                        (pvm/hoitokauden-alkupvm hoitovuosi))))
           :valinta-nayta #(or (hoitovuosi->teksti %) " - valitse hoitovuosi -")
           ::lomake/col-luokka "col-xs-6"}

          ;; Lukutila :: ennen -25 alkaneille
          (and (not mhu25?) lukutila?)
          {:otsikko "Laskutuskuukausi"
           :nimi :perintapvm
           :fmt (fn [pvm]
                  ;; Lukutilassa haetaan näytettävä laskutuskuukausi suoraan lomakkeen avaimesta
                  (when pvm
                    (some #(when (and
                                   (= (pvm/vuosi pvm) (pvm/vuosi (:pvm %)))
                                   (= (pvm/kuukausi pvm) (pvm/kuukausi (:pvm %)))) (:teksti %))
                      laskutuskuukaudet)))
           :pakollinen? true
           :tyyppi :pvm
           ::lomake/col-luokka "col-xs-6"}

          ;; Lukutila :: 25+ urakoille
          (and mhu25? lukutila?)
          {:otsikko "Kohdistuu hoitovuodelle"
           :nimi :perintapvm
           :fmt (fn [perintapvm]
                  (some-> perintapvm perintapvm->hoitovuosi hoitovuosi->teksti))
           :pakollinen? true
           :tyyppi :pvm
           ::lomake/col-luokka "col-xs-6"})
        ;; Poistetaan hoidon urakoilta käsittelytapa kokonaan
        (when (not (= :teiden-hoito urakan-tyyppi))
          {:otsikko "Käsittelytapa"
           :nimi :kasittelytapa :tyyppi :valinta
           :pakollinen? true
           :muokattava? (constantly (and voi-muokata? (not lukutila?)))
           ::lomake/col-luokka "col-xs-12"
           :valinnat sanktio-domain/kasittelytavat
           :valinta-nayta #(or (sanktio-domain/kasittelytapa->teksti %) "- valitse käsittelytapa -")}))

      ;; Piilota liitteet lukutilassa kokonaan, koska ne eivät nyt tue pelkästään lukutilaa.
      (if-not lukutila?
        {:otsikko "Liitteet" :nimi :liitteet :kaariva-luokka "sanktioliite"
         :tyyppi :komponentti
         ::lomake/col-luokka "col-xs-12"
         :komponentti (fn [_]
                        [liitteet/liitteet-ja-lisays urakka-id (get-in app [:lomake :liitteet])
                         {:uusi-liite-atom (r/wrap uusi-liite
                                             #(e! (tiedot/->LisaaLiite %)))
                          :uusi-liite-teksti "Lisää liite"
                          :salli-poistaa-lisatty-liite? true
                          :poista-lisatty-liite-fn #(e! (tiedot/->PoistaLisattyLiite))
                          :salli-poistaa-tallennettu-liite? true
                          :nayta-lisatyt-liitteet? false
                          :poista-tallennettu-liite-fn #(e! (tiedot/->PoistaTallennettuLiite %))}])}
        {:otsikko "Liitteet" :nimi :liitteet :kaariva-luokka "sanktioliite"
         :tyyppi :komponentti
         ::lomake/col-luokka "col-xs-12"
         :komponentti (fn [_]
                        [:div
                         (if (and (get-in app [:lomake :liitteet])
                               (not (empty? (get-in app [:lomake :liitteet]))))
                           (doall
                             (for [l (get-in app [:lomake :liitteet])]
                               ^{:key l}
                               [liitteet/liitetiedosto l {:salli-poisto? false
                                                          :nayta-koko? true}]))
                           "Ei liitettä")])})]
     lomakkeen-tiedot]))


(defn bonus-lomake
  [sivupaneeli-auki?-atom avattu-bonus tallennus-onnistui-fn]
  (let [tallennus-onnistui-fn (if (fn? tallennus-onnistui-fn) tallennus-onnistui-fn (constantly nil))
        sulje-fn (fn [tallennus-onnistui?]
                   (when tallennus-onnistui?
                     (tallennus-onnistui-fn))
                   (reset! sivupaneeli-auki?-atom false))
        bonus-konfiguraatio (r/atom nil)
        bonus-konfiguraation-haku-kaynnissa? (r/atom false)
        bonus-konfiguraation-haku-avain (r/atom nil)
        bonukset-tila (r/atom {:liitteet-haettu? false
                               :lomake (or
                                         ;; Muokataan vanhaa bonusta
                                         (when (some? (:id avattu-bonus))
                                           avattu-bonus)
                                         ;; tai alustetaan bonuslomakkeen tila
                                         (tiedot/uusi-bonus))})]
    (fn [_ _ _ lukutila? voi-muokata?]
      (let [urakka @nav/valittu-urakka
            urakka-id (:id urakka)
            urakan-alkupvm (:alkupvm urakka)
            valittu-hoitokausi @tiedot-urakka/valittu-hoitokausi
            kasittelyaika (get-in @bonukset-tila [:lomake :kasittelyaika])
            toimenpideinstanssi-id (get-in @bonukset-tila [:lomake :toimenpideinstanssi])
            hoitovuosi (bonus-konfiguraation-hoitovuosi urakan-alkupvm valittu-hoitokausi kasittelyaika)
            haku-avain [urakka-id hoitovuosi toimenpideinstanssi-id]
            yllapitourakka? @tiedot-urakka/yllapitourakka?
            uusi-haku-kaynnistyy? (and urakka-id
                                   hoitovuosi
                                   (not= haku-avain @bonus-konfiguraation-haku-avain))
            bonus-konfiguraation-tila (bonus-konfiguraation-tila
                                        @bonus-konfiguraatio
                                        (or @bonus-konfiguraation-haku-kaynnissa?
                                          uusi-haku-kaynnistyy?))]
        (when uusi-haku-kaynnistyy?
          (reset! bonus-konfiguraation-haku-avain haku-avain)
          (reset! bonus-konfiguraation-haku-kaynnissa? true)
          (go
            (let [vastaus (<! (tiedot/hae-urakan-bonus-konfiguraatio urakka-id hoitovuosi toimenpideinstanssi-id))]
              (reset! bonus-konfiguraatio vastaus)
              (reset! bonus-konfiguraation-haku-kaynnissa? false))))
        (case bonus-konfiguraation-tila
          :haku-kaynnissa
          (if yllapitourakka?
            [:<>
             [tuck/tuck bonukset-tila
              (r/partial bonus-lomake* sulje-fn lukutila? voi-muokata? @bonus-konfiguraatio)]]
            [ajax-loader "Ladataan..."])

          :haku-epaonnistui
          (if yllapitourakka?
            [:<>
             [tuck/tuck bonukset-tila
              (r/partial bonus-lomake* sulje-fn lukutila? voi-muokata? @bonus-konfiguraatio)]]
            [:div
             [:p "Bonuksia ei voitu ladata juuri nyt."]
             [:p "Yritä hetken kuluttua uudelleen. Jos ongelma jatkuu, ota yhteyttä Harja-tukeen."]])

          :ei-konfiguraatiota
          (if yllapitourakka?
            [:<>
             [tuck/tuck bonukset-tila
              (r/partial bonus-lomake* sulje-fn lukutila? voi-muokata? @bonus-konfiguraatio)]]
            [:div
             [:p "Bonuksia ei ole määritelty tälle urakalle valitulla hoitokaudella ja toimenpideinstanssilla."]
             [:p "Ota yhteyttä Harja-tukeen jotta asia saadaan korjattua."]])

          [:<>
           [tuck/tuck bonukset-tila
            (r/partial bonus-lomake* sulje-fn lukutila? voi-muokata? @bonus-konfiguraatio)]])))))
