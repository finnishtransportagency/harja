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
              kuittaustyypin-selite kuittaustyypin-lyhenne
              tilan-selite] :as domain]
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
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.views.kartta :as kartta]
            [harja.views.ilmoituskuittaukset :as kuittaukset]
            [harja.views.ilmoituksen-tiedot :as it]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.notifikaatiot :as notifikaatiot]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.tiedot.ilmoitukset.viestit :as v]
            [harja.ui.kentat :as kentat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.bootstrap :as bs])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def aikavalit [{:nimi "1 tunnin ajalta" :tunteja 1}
                {:nimi "12 tunnin ajalta" :tunteja 12}
                {:nimi "1 päivän ajalta" :tunteja 24}
                {:nimi "1 viikon ajalta" :tunteja 168}
                {:nimi "Vapaa aikaväli" :vapaa-aikavali true}])

(def valittu-atom (atom :tietyoilmoitukset))

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
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (v/->YhdistaValinnat uusi))))
    (komp/sisaan #(notifikaatiot/pyyda-notifikaatiolupa))
    (komp/kuuntelija :ilmoitus-klikattu (fn [_ i] (e! (v/->ValitseIlmoitus i))))
    (fn [e! {valittu-ilmoitus :valittu-ilmoitus :as ilmoitukset}]
      [:span
       [kartta/kartan-paikka]
       (if valittu-ilmoitus
         [ilmoituksen-tiedot e! valittu-ilmoitus]

         [bs/tabs {:style :tabs :classes "tabs-taso1"
                   :active valittu-atom}

          "Tieliikennekeskus"
          :tieliikennekeskus
          ^{:key "tieliikennekeskus"}
          [ilmoitusten-paanakyma e! ilmoitukset]


          "Tietyöilmoitukset"
          :tietyoilmoitukset
          ^{:key "tietyoilmoitukset"}
          [:span.ilmoitukset

           (if false
             [:div
              [:br]
              [napit/uusi "Kirjaa uusi tietyöilmoitus"
               #()
               {}]
              [:br]

              [lomake/lomake
               {:luokka :horizontal
                :otsikko "Hae tietyöilmoituksia"}
               [(lomake/ryhma
                  {:rivi? false
                   :otsikko "Ilmoitettu"}
                  {:nimi :alkuaika
                   :otsikko "Alkaen"
                   :tyyppi :pvm
                   :validoi [[:ei-tyhja "Anna alkuaika"]]}
                  {:nimi :loppuaika
                   :otsikko "Päättyen"
                   :tyyppi :pvm
                   :validoi [[:ei-tyhja "Anna loppuaika"]]}
                  {:nimi :tila
                   :tyyppi :valinta
                   :otsikko "Tila"
                   :valinnat ["Kesken"]
                   :valinta-nayta (constantly "Kesken")})
                (lomake/ryhma
                  {:otsikko "Tierekisteriosoite"}
                  {:otsikko "Tierekisteriosoite"
                   :nimi :tr
                   :pakollinen? true
                   :tyyppi :tierekisteriosoite
                   :ala-nayta-virhetta-komponentissa? true
                   :validoi [[:validi-tr "Reittiä ei saada tehtyä" [:sijainti]]]
                   })]]
              [grid
               {:piilota-toiminnot true
                :max-rivimaara 500
                :max-rivimaaran-ylitys-viesti "Yli 500 ilmoitusta. Tarkenna hakuehtoja."}

               [{:otsikko "Tierekisteriosoite" :nimi :tierekisteriosoite :leveys 3}
                {:otsikko "Ilmoitettu" :nimi :ilmoitettu :leveys 3}
                {:otsikko "Urakka" :nimi :urakka :leveys 3}
                {:otsikko "Ilmoittaja" :nimi :ilmoittaja :leveys 3}
                {:otsikko "Kunnat" :nimi :kunnat :leveys 3}
                {:otsikko "Ilmoitus koskee" :nimi :ilmoituskoskee :leveys 3}
                {:otsikko "Tyotyypit" :nimi :tyotyypit :leveys 3}]
               [{:tierekisteriosoite "4 / 363 / 6637 "
                 :ilmoitettu "29.12.2016"
                 :urakka "Oulun alueurakka 2014-2019"
                 :ilmoittaja "Ilpo Ilmoittaja"
                 :kunnat "Kempele"
                 :ilmoituskoskee "Ensimmäinen ilmoitus työstä"
                 :tyotyypit "Tienrakennus, Päällystystyö"}]]]
             [:div
              [:br]
              [napit/takaisin "Takaisin tietyöilmoituslistaukseen"
               #()
               {}]

              [lomake/lomake
               {:luokka :horizontal
                :otsikko "Ilmoitus liikennettä haittaavasta työstä"}
               [{:nimi :h21
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:div [:h2 "1. Tiedot kohteesta"]])
                 }
                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])
                 }
                {:nimi :urakka
                 :tyyppi :valinta
                 :otsikko "Urakka"
                 :valinnat ["Oulun alueurakka 2014-2019"]
                 :valinta-nayta (constantly "Oulun alueurakka 2014-2019")}
                {:nimi :h21
                 :tyyppi :komponentti
                 :komponentti (fn [_] [napit/yleinen "Hae urakka" #()])}

                (lomake/ryhma
                  {:otsikko "Urakoitsija"
                   :rivi? false}
                  {:nimi :urakoitsija-nimi
                   :otsikko "Nimi"
                   :tyyppi :string}
                  {:nimi :urakoitsija-ytunnus
                   :otsikko "Y-tunnus"
                   :tyyppi :string}
                  {:nimi :br2
                   :tyyppi :komponentti
                   :komponentti (fn [_] [:h4 "Yhteyshenkilö"])
                   }
                  {:nimi :br2
                   :tyyppi :komponentti
                   :komponentti (fn [_] [:br])
                   }
                  {:nimi :urakoitsijan-yhteyshenkilon-etunimi
                   :otsikko "Etunimi"
                   :tyyppi :string}
                  {:nimi :urakoitsijan-yhteyshenkilon-sukunimi
                   :otsikko "Sukunimi"
                   :tyyppi :string}
                  {:nimi :urakoitsijan-yhteyshenkilon-puhelin
                   :otsikko "Puhelinnumero"
                   :tyyppi :string}
                  {:nimi :urakoitsijan-yhteyshenkilon-sahkoposti
                   :otsikko "Sähköposti"
                   :tyyppi :string}
                  )

                (lomake/ryhma
                  {:otsikko "Tilaaja"
                   :rivi? false}
                  {:nimi :tilaaja
                   :tyyppi :valinta
                   :otsikko "Urakka"
                   :valinnat ["POP ELY"]
                   :valinta-nayta (constantly "POP ELY")}
                  {:nimi :br2
                   :tyyppi :komponentti
                   :komponentti (fn [_] [:br])}
                  {:nimi :br2
                   :tyyppi :komponentti
                   :komponentti (fn [_] [:h4 "Yhteyshenkilö"])
                   }
                  {:nimi :br2
                   :tyyppi :komponentti
                   :komponentti (fn [_] [:br])
                   }
                  {:nimi :urakoitsijan-yhteyshenkilon-etunimi
                   :otsikko "Etunimi"
                   :tyyppi :string}
                  {:nimi :urakoitsijan-yhteyshenkilon-sukunimi
                   :otsikko "Sukunimi"
                   :tyyppi :string}
                  {:nimi :urakoitsijan-yhteyshenkilon-puhelin
                   :otsikko "Puhelinnumero"
                   :tyyppi :string}
                  {:nimi :urakoitsijan-yhteyshenkilon-sahkoposti
                   :otsikko "Sähköposti"
                   :tyyppi :string}
                  )
                (lomake/ryhma
                  {:otsikko "Lupa"}
                  {:nimi :luvan-diaarinumero
                  :otsikko "Diaarinumero"
                  :tyyppi :string}
                  {:nimi :h21
                   :tyyppi :komponentti
                   :komponentti (fn [_] [napit/yleinen "Hae lupa" #()])})


                (lomake/ryhma
                  {:otsikko "Työn tiedot"
                   :rivi? false}
                  {:otsikko "Tierekisteriosoite"
                   :nimi :tr
                   :pakollinen? true
                   :tyyppi :tierekisteriosoite
                   :ala-nayta-virhetta-komponentissa? true
                   :validoi [[:validi-tr "Reittiä ei saada tehtyä" [:sijainti]]]}
                  {:nimi :tyonpituus
                   :otsikko "Työn pituus"
                   :tyyppi :string}
                  {:nimi :tien-nimi
                   :otsikko "Tien nimi"
                   :tyyppi :string}
                  {:nimi :kunnat
                   :otsikko "Kunnat"
                   :tyyppi :string}
                  {:nimi :alkupiste
                   :otsikko "Työn alkupiste (osoite, paikannimi)"
                   :tyyppi :string}
                  {:nimi :aloituspvm
                   :otsikko "Aloitus pvm"
                   :tyyppi :pvm}
                  {:nimi :loppupiste
                   :otsikko "Työn loppupiste (osoite, paikannimi)"
                   :tyyppi :string}
                  {:nimi :lopetuspvm
                   :otsikko "Lopetus pvm"
                   :tyyppi :pvm})

                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])
                 }
                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])
                 }
                {:nimi :h21
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:div [:h2 "2. Työvaiheet"]])}
                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])}
                {:nimi :h21231
                 :tyyppi :komponentti
                 :komponentti (fn [_]
                                [grid
                                 {:voi-poistaa? (constantly true)
                                  :voi-lisata? true
                                  :piilota-toiminnot? false}

                                 [{:otsikko "Tierekisteriosoite" :nimi :tierekisteriosoite :leveys 3}
                                  {:otsikko "Alkupiste" :nimi :ilmoitettu :leveys 3}
                                  {:otsikko "Aloitus" :nimi :urakka :leveys 3}
                                  {:otsikko "Lopetuspiste" :nimi :ilmoittaja :leveys 3}
                                  {:otsikko "Lopetus" :nimi :kunnat :leveys 3}]
                                 [{:tierekisteriosoite "4 / 363 / 6637 "
                                   :ilmoitettu "Nelostie Kempeleessä"
                                   :urakka "29.12.2016"
                                   :ilmoittaja "Nelostie Kempeleessä"
                                   :kunnat "29.12.2016"
                                   :ilmoituskoskee "1.1.2017"}]])}
                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])}
                {:nimi :h2123
                 :tyyppi :komponentti
                 :komponentti (fn [_] [napit/uusi "Lisää työvaihe" #()])}

                (lomake/ryhma
                  {:otsikko "Uusi työvaihe"
                   :rivi? false}
                  {:otsikko "Tierekisteriosoite"
                   :nimi :tr
                   :pakollinen? true
                   :tyyppi :tierekisteriosoite
                   :ala-nayta-virhetta-komponentissa? true
                   :validoi [[:validi-tr "Reittiä ei saada tehtyä" [:sijainti]]]}
                  {:nimi :tyonpituus
                   :otsikko "Työn pituus"
                   :tyyppi :string}
                  {:nimi :br1
                   :tyyppi :komponentti
                   :komponentti (fn [_] [:br])}
                  {:nimi :br2
                   :tyyppi :komponentti
                   :komponentti (fn [_] [:br])}
                  {:nimi :alkupiste
                   :otsikko "Alkupiste (osoite, paikannimi)"
                   :tyyppi :string}
                  {:nimi :aloituspvm
                   :otsikko "Aloitus pvm"
                   :tyyppi :pvm}
                  {:nimi :loppupiste
                   :otsikko "Loppupiste (osoite, paikannimi)"
                   :tyyppi :string}
                  {:nimi :lopetuspvm
                   :otsikko "Lopetus pvm"
                   :tyyppi :pvm}
                  )
                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])}

                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])
                 }
                {:nimi :h21
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:h2 "3. Työn tyyppi"])
                 }


                (lomake/ryhma
                  {:rivi? false}

                  {:nimi :tyyppi
                   :tyyppi :checkbox-group
                   :vaihtoehdot [:0 :1 :2 :3 :4 :5 :6 :7 :8 :9 :10 :11 :12 :13 :14 :15 :16 :17 :18 :19]
                   :vaihtoehto-nayta {:14 "Tasoristeystyö",
                                      :18 "Räjäytystyö",
                                      :12 "Siltatyö",
                                      :11 "Silmukka-anturin asent.",
                                      :10 "Kaapelityö",
                                      :13 "Valaistustyö",
                                      :0 "Tienrakennus",
                                      :4 "Jyrsintä-/stabilointityö",
                                      :16 "Tiemerkintätyö",
                                      :7 "Kaidetyö",
                                      :1 "Päällystystyö",
                                      :8 "Tienvarsilaitteiden huolto",
                                      :9 "Kevyenliik. väylän rak.",
                                      :17 "Vesakonraivaus/niittotyö",
                                      :19 "Muu, mikä?",
                                      :2 "Viimeistely",
                                      :5 "Tutkimus/mittaus",
                                      :15 "Liittymä- ja kaistajärj.",
                                      :3 "Rakenteen parannus",
                                      :6 "Alikulkukäytävän rak."}}
                  {:nimi :br1
                   :tyyppi :komponentti
                   :komponentti (fn [_] [:br])}
                  {:nimi :muumika
                   :tyyppi :string})

                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])
                 }
                {:nimi :h21
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:h2 "4. Työaika"])
                 }

                (lomake/ryhma
                  {:rivi? false}
                  {:nimi :h21
                   :tyyppi :komponentti
                   :komponentti (fn [_] [napit/uusi "Lisää" #()])
                   }
                  {:nimi :br1
                   :tyyppi :komponentti
                   :komponentti (fn [_] [:br])
                   }
                  {:nimi :alkuaika
                   :otsikko "Alku"
                   :tyyppi :string}
                  {:nimi :loppuaika
                   :otsikko "Loppu"
                   :tyyppi :string}

                  {:nimi :tyyppi
                   :tyyppi :checkbox-group
                   :vaihtoehdot [:0 :1 :2 :3 :4 :5 :6]
                   :vaihtoehto-nayta {:0 "Maanantai", :1 "Tiistai", :2 "Keskiviikko", :3 "Torstai", :4 "Perjantai", :5 "Lauantai", :6 "Sunnuntai"}
                   })

                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])
                 }

                {:nimi :h21
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:h2 "5. Vaikutukset liikenteelle"])}
                (lomake/ryhma
                  {:otsikko "Arvioitu viivästys"
                   :rivi? false}
                  {:nimi :alkuaika
                   :otsikko "Normaali liikenne (minuuttia)"
                   :tyyppi :string}
                  {:nimi :loppuaika
                   :otsikko "Ruuhka-aika (minuuttia)"
                   :tyyppi :string})
                (lomake/ryhma
                  {:otsikko "Kaistajärjestelyt"
                   :rivi? false}
                  {:nimi :tyyppi
                   :tyyppi :checkbox-group
                   :vaihtoehdot [:0 :1 :2]
                   :vaihtoehto-nayta {:0 "Yksi ajokaista suljettu", :1 "Yksi ajorata suljettu", :2 "Muu, mikä?"}}
                  {:nimi :br1
                   :tyyppi :komponentti
                   :komponentti (fn [_] [:br])}
                  {:nimi :loppuaika
                   :otsikko ""
                   :tyyppi :string})
                (lomake/ryhma
                  {:otsikko "Nopeusrajoitus"
                   :rivi? false}
                  {:nimi :loppuaika
                   :otsikko "Km/h"
                   :tyyppi :string}
                  {:nimi :asdf
                   :otsikko "Metriä"
                   :tyyppi :string})

                (lomake/ryhma
                  {:otsikko "Kulkurajoitukset"
                   :rivi? false}
                  {:nimi :loppuaika
                   :otsikko "Ajoneuvon maksimikorkeus"
                   :tyyppi :string}
                  {:nimi :pituus
                   :otsikko "Ajoneuvon maksimileveys"
                   :tyyppi :string}
                  {:nimi :leveys
                   :otsikko "Ajoneuvon maksimipituus"
                   :tyyppi :string}
                  {:nimi :paino
                   :otsikko "Ajoneuvon maksimipaino"
                   :tyyppi :string}
                  {:nimi :kuumennin
                   :otsikko "Kuumennin käytössä (avotuli)"
                   :tyyppi :checkbox}
                  {:nimi :tyokoneita
                   :otsikko "Työkoneita liikenteen seassa"
                   :tyyppi :checkbox}
                  )

                (lomake/ryhma
                  {:otsikko "Tien pinta työmaalla (muu kuin kiertotie)"
                   :rivi? false}
                  {:nimi :tyyppi
                   :tyyppi :checkbox-group
                   :otsikko "Päällyste"
                   :vaihtoehdot [:0 :1 :2]
                   :vaihtoehto-nayta {:0 "Päällystetty", :1 "Jyrsitty", :2 "Murske"}}
                  {:nimi :br1
                   :tyyppi :komponentti
                   :komponentti (fn [_] [:br])}
                  {:nimi :paino
                   :otsikko "Murskeen pituus (metriä)"
                   :tyyppi :string})

                (lomake/ryhma
                  {:otsikko "Kiertotie"
                   :rivi? false}
                  {:nimi :paino
                   :otsikko "Pituus (metriä)"
                   :tyyppi :string}
                  {:nimi :br1
                   :tyyppi :komponentti
                   :komponentti (fn [_] [:br])}
                  {:nimi :tyyppi
                   :otsikko "Päällyste"
                   :tyyppi :checkbox-group
                   :vaihtoehdot [:0 :1 :2 :3]
                   :vaihtoehto-nayta {:0 "Loivat mutkat", :1 "Jyrkät mutkat", :2 "Päällystetty" :3 "Murske"}}
                  {:nimi :br1
                   :tyyppi :komponentti
                   :komponentti (fn [_] [:br])}
                  {:nimi :asdf
                   :otsikko "Kantavuusrajoitus"
                   :tyyppi :string}
                  {:nimi :asdff
                   :otsikko "Painorajoitus"
                   :tyyppi :string})

                (lomake/ryhma
                  {:otsikko "Pysäytykset"
                   :rivi? false}
                  {:nimi :tyyppi
                   :tyyppi :checkbox-group
                   :vaihtoehdot [:0 :1]
                   :vaihtoehto-nayta {:0 "Liikennevalot", :1 "Liikenteenohjaaja"}}
                  {:nimi :br1
                   :tyyppi :komponentti
                   :komponentti (fn [_] [:br])}
                  {:nimi :asdf
                   :otsikko "Alkaen"
                   :tyyppi :pvm-aika}
                  {:nimi :asdff
                   :otsikko "Päättyen"
                   :tyyppi :pvm-aika})

                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])}
                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])}
                {:nimi :h21
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:h2 "6. Vaikutussuunta"])}
                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])}
                {:nimi :tyyppi
                 :tyyppi :checkbox-group
                 :vaihtoehdot [:0 :1]
                 :vaihtoehto-nayta {:0 "Haittaa molemmissa ajosuunnissa ", :1 "Haittaa suunnassa (lähin kaupunki)"}}
                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])}
                {:nimi :asdff
                 :tyyppi :string}


                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])}
                {:nimi :h21
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:h2 "7. Lisätietoja"])}
                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])}
                {:nimi :asdff
                 :tyyppi :text}

                {:nimi :br1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:br])}
                {:nimi :h21
                 :tyyppi :komponentti
                 :komponentti (fn [_] [:h2 "8. Ilmoittaja"])}
                (lomake/ryhma
                  {:rivi? false}
                  {:nimi :urakoitsijan-yhteyshenkilon-etunimi
                   :otsikko "Etunimi"
                   :tyyppi :string}
                  {:nimi :urakoitsijan-yhteyshenkilon-sukunimi
                   :otsikko "Sukunimi"
                   :tyyppi :string}
                  {:nimi :urakoitsijan-yhteyshenkilon-puhelin
                   :otsikko "Puhelinnumero"
                   :tyyppi :string}
                  {:nimi :urakoitsijan-yhteyshenkilon-sahkoposti
                   :otsikko "Sähköposti"
                   :tyyppi :string})

                {:nimi :h21
                 :tyyppi :komponentti
                 :komponentti (fn [_] [napit/tallenna "Tallenna" #()])}
                {:nimi :h2f1
                 :tyyppi :komponentti
                 :komponentti (fn [_] [napit/tallenna "Merkitse työ valmistuneeksi" #()])}

                ]]])]])])))

(defn ilmoitukset []
  (komp/luo
    (komp/sisaan #(notifikaatiot/pyyda-notifikaatiolupa))
    (komp/sisaan-ulos #(do
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (nav/vaihda-kartan-koko! :M))
                      #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
    (komp/lippu tiedot/karttataso-ilmoitukset)

    (fn []
      [tuck tiedot/ilmoitukset ilmoitukset*])))
