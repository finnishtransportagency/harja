(ns harja.views.urakka.pot2.pot2-lomake
"POT2-lomake"
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.asiakas.kommunikaatio :as k]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
    [harja.domain.pot2 :as pot2-domain]
    [harja.domain.yllapitokohde :as yllapitokohteet-domain]
    [harja.loki :refer [log]]
    [harja.ui.debug :refer [debug]]
    [harja.ui.kentat :as kentat]
    [harja.ui.komponentti :as komp]
    [harja.ui.lomake :as lomake]
    [harja.ui.napit :as napit]
    [harja.ui.ikonit :as ikonit]
    [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
    [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka.paallystys :as paallystys]
    [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
    [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
    [harja.views.urakka.pot2.alusta :as alusta]
    [harja.views.urakka.pot2.paallystekerros :as paallystekerros]
    [harja.views.urakka.pot2.paallyste-ja-alusta-yhteiset :as pot2-yhteinen]
    [harja.views.urakka.pot-yhteinen :as pot-yhteinen]
    [harja.views.urakka.pot2.massa-lomake :as massa-lomake]
    [harja.views.urakka.pot2.murske-lomake :as murske-lomake]
    [harja.tiedot.muokkauslukko :as lukko]
    [harja.ui.grid :as grid]
    [harja.pvm :as pvm]
    [clojure.string :as str]
    [harja.domain.tierekisteri :as tr])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(def pot2-validoinnit
  {:perustiedot paallystys/perustietojen-validointi
   :paallystekerros {:rivi [{:fn paallystekerros/validoi-paallystekerros
                             :sarakkeet {:tr-numero :tr-numero
                                         :tr-ajorata :tr-ajorata
                                         :tr-kaista :tr-kaista
                                         :tr-alkuosa :tr-alkuosa
                                         :tr-alkuetaisyys :tr-alkuetaisyys
                                         :tr-loppuosa :tr-loppuosa
                                         :tr-loppuetaisyys :tr-loppuetaisyys}}
                            ;; TODO: Kaistavalidointi disabloitu, kunnes Digiroad-aineisto on saatu kunnolla käyttöön
                            ;;       validoinnin tueksi
                            #_{:fn pot2-yhteinen/validoi-kaistavalinta
                             :sarakkeet {:tr-kaista :tr-kaista}}]
                     :taulukko [{:fn (r/partial paallystekerros/kohde-toisten-kanssa-paallekkain-validointi true)
                                 :sarakkeet {:tr-numero :tr-numero
                                             :tr-ajorata :tr-ajorata
                                             :tr-kaista :tr-kaista
                                             :tr-alkuosa :tr-alkuosa
                                             :tr-alkuetaisyys :tr-alkuetaisyys
                                             :tr-loppuosa :tr-loppuosa
                                             :tr-loppuetaisyys :tr-loppuetaisyys}}]
                     ;; TR-osoitevälissä täytyy olla jotkin arvot, jotta BE:ssä ei tule virheitä luonnosta tallentaessa
                     :tr-numero [[:ei-tyhja "Anna arvo"]]
                     :tr-alkuosa [[:ei-tyhja "Anna arvo"]]
                     :tr-alkuetaisyys [[:ei-tyhja "Anna arvo"]]
                     :tr-loppuosa [[:ei-tyhja "Anna arvo"]]
                     :tr-loppuetaisyys [[:ei-tyhja "Anna arvo"]]}
   :alusta {:rivi [{:fn alusta/alustan-validointi
                    :sarakkeet {:tr-numero :tr-numero
                                :tr-ajorata :tr-ajorata
                                :tr-kaista :tr-kaista
                                :tr-alkuosa :tr-alkuosa
                                :tr-alkuetaisyys :tr-alkuetaisyys
                                :tr-loppuosa :tr-loppuosa
                                :tr-loppuetaisyys :tr-loppuetaisyys}}
                   ;; TODO: Kaistavalidointi disabloitu, kunnes Digiroad-aineisto on saatu kunnolla käyttöön
                   ;;       validoinnin tueksi
                   #_{:fn pot2-yhteinen/validoi-kaistavalinta
                    :sarakkeet {:tr-kaista :tr-kaista}}]
            :taulukko [{:fn (constantly :default) ;; no-op
                        :sarakkeet {:tr-numero :tr-numero
                                    :tr-ajorata :tr-ajorata
                                    :tr-kaista :tr-kaista
                                    :tr-alkuosa :tr-alkuosa
                                    :tr-alkuetaisyys :tr-alkuetaisyys
                                    :tr-loppuosa :tr-loppuosa
                                    :tr-loppuetaisyys :tr-loppuetaisyys}}]
            ;; TR-osoitevälissä täytyy olla jotkin arvot, jotta BE:ssä ei tule virheitä luonnosta tallentaessa
            :tr-numero [[:ei-tyhja "Anna arvo"]]
            :tr-alkuosa [[:ei-tyhja "Anna arvo"]]
            :tr-alkuetaisyys [[:ei-tyhja "Anna arvo"]]
            :tr-loppuosa [[:ei-tyhja "Anna arvo"]]
            :tr-loppuetaisyys [[:ei-tyhja "Anna arvo"]]}})

(def materiaalikirjasto-tyhja-txt
  "Urakan materiaalikirjasto on tyhjä. Aloita päällystysilmoitus lisäämällä urakalle materiaalit.")

(def materiaalikirjasto-napin-tooltip
  "Urakan materiaalikirjastoon syötetään urakan päällystystöissä käytetyt massat ja murskeet.")

(defn avaa-materiaalikirjasto-nappi [toiminto tyyli]
  [yleiset/wrap-if true
   [yleiset/tooltip {} :% materiaalikirjasto-napin-tooltip]
   [napit/nappi "Muokkaa urakan materiaaleja"
   toiminto
   {:ikoni (ikonit/livicon-pen)
    :luokka "nappi-toissijainen"
    :style (merge {:margin-left "0"}
                  tyyli)}]])

(defn- materiaalit
  "Toimenpiteiden ja materiaalien otsikkorivi, jossa joitakin toimintoja"
  [e! massat murskeet]
  [:div
   [:h5 "Materiaalit"]
   (when (mk-tiedot/materiaalikirjasto-tyhja? massat murskeet)
     [:div {:style {:margin-top "24px"
                    :margin-bottom "24px"}}
      [yleiset/toast-viesti materiaalikirjasto-tyhja-txt]])
   [avaa-materiaalikirjasto-nappi #(e! (mk-tiedot/->NaytaModal true))]])

(defn lisatiedot
  [e! {tila :tila} lisatiedot-atom]
  [:span
   [:h5 "Lisätiedot ja huomautukset"]
   [kentat/tee-kentta {:tyyppi :text :nimi :lisatiedot :koko [80 4]
                       :disabled? (= :lukittu tila)}
    (r/wrap @lisatiedot-atom #(do
                                (e! (pot2-tiedot/->Pot2Muokattu))
                                (reset! lisatiedot-atom %)))]])


;; -- START -- Lomakkeen virheiden ja varoitusten infolaatikko --
(def yha-ja-velho-lahetys-onnistunut-leveys "320px")

(defn- yha-ja-velho-lahetyksen-tila
  "Komponentti näyttää YHA- ja myöhemmin Velho-lähetyksen tilan päällystysilmoituksella."
  [{:keys [lahetys-onnistunut lahetetty] :as lahetyksen-tila}
   {:keys [tila muokattu]}]
  (let [virhe-teksti (pot-yhteinen/lahetys-virhe-teksti lahetyksen-tila)
        muokattu-yhaan-lahettamisen-jalkeen? (when (and muokattu lahetetty)
                                               (> muokattu lahetetty))]
    (cond
      ;; näytetään lähetyksen virheet
      virhe-teksti
      [yleiset/info-laatikko :varoitus
       "YHA-lähetyksessä virhe" ;; TODO enable VELHO lähetys "YHA/Velho lähetyksessä virhe"
       virhe-teksti nil]

      ;; näytetään jos lähetys on onnistunut
      (and lahetys-onnistunut lahetetty)
      [yleiset/info-laatikko (if muokattu-yhaan-lahettamisen-jalkeen?
                               :vahva-ilmoitus
                               :onnistunut)
       (str "YHA-lähetys onnistunut " (pvm/pvm-aika-opt lahetetty))
       (when muokattu-yhaan-lahettamisen-jalkeen?
         (str "Ilmoitusta on muokattu YHA:an lähettämisen jälkeen "
           (pvm/pvm-aika-opt muokattu)
           ". Voit tarvittaessa lähettää ilmoituksen uudelleen listausnäkymästä."))
       (when-not muokattu-yhaan-lahettamisen-jalkeen? yha-ja-velho-lahetys-onnistunut-leveys)]

      ;; näytetään vain valmiiksi täytetyille ilmoituksille, jos lähetystä ei ole tehty
      (and (nil? lahetetty) (false? lahetys-onnistunut)
        (#{:valmis :lukittu} tila))
      [yleiset/info-laatikko :vahva-ilmoitus
       (str "YHA-lähetystä ei vielä tehty.") "" "320px"]

      :else
      nil)))

(defn- lomake-varoitukset->yksinkertaistettu-str
  [varoitukset-map]
  (let [tieosoite-varoitukset (select-keys varoitukset-map tr/paaluvali-avaimet)
        kaista-varoitukset (select-keys varoitukset-map [:tr-kaista])]
    [:<>
     (when (seq tieosoite-varoitukset)
       [:span "Tarkista tieosoitteen oikeellisuus"
        (when (str/includes? (str tieosoite-varoitukset) "päällekkäin")
          " ja/tai päällekkäisyydet")
        "."])
     (when (and (seq kaista-varoitukset)
             (str/includes? (str kaista-varoitukset) "Kaista-aineiston mukaan"))
       [:span "Tarkista kaistatietojen oikeellisuus"])]))

(defn- lomake-virheet->yksinkertaistettu-str
  [virheet-map]
  [:<>
   (when (seq virheet-map)
     ;; Toistaiseksi kehotetaan vain täyttämään pakolliset kentät, koska muun tyyppisiä virheitä ei ole.
     [:span "Täytä pakolliset kentät."])])


(defn- virheet-tai-varoitukset->elementit [virheet tyyppi]
  (if (map? virheet)
    (remove nil?
      (mapv (fn [[rivinro virheet-map]]
              ;; Estä mahdollisten nil-validointien nouseminen virhelistaukseen
              (when (seq virheet-map)
                [:div.virhe-rivi [:span "Rivi " [:b rivinro] ":"]
                 (case tyyppi
                   :varoitus
                   (lomake-varoitukset->yksinkertaistettu-str virheet-map)
                   :virhe
                   (lomake-virheet->yksinkertaistettu-str virheet-map)
                   virheet-map)]))
        (into (sorted-map) virheet)))
    []))

(defn- puuttuva-tieto->str [avain]
  (case avain
    :aloituspvm "Työ aloitettu"
    :valmispvm-kohde "Kohde valmistunut"
    :tr-osoite "Tierekisteriosoite"

    ;; jos ei jostain syystä tunnisteta tai tulevaisuudessa tulee uusia kenttiä:
    (name avain)))

(defn- perustietojen-virheet
  [puuttuvat-pakolliset-perustiedot]
  (when-not (empty? puuttuvat-pakolliset-perustiedot)
    (str "Seuraavat pakolliset perustiedot puuttuvat: "
         (str/join ", "
                   (mapv #(puuttuva-tieto->str %)
                         puuttuvat-pakolliset-perustiedot)))))

(defn- lomakkeen-virheet
  [lahetyksen-tila perustiedot]
  (let [kulutuskerroksen-virheet (virheet-tai-varoitukset->elementit @pot2-tiedot/kohdeosat-virheet-atom :virhe)
        kulutuskerroksen-varoitukset (virheet-tai-varoitukset->elementit @pot2-tiedot/kohdeosat-varoitukset-atom :varoitus)
        alustan-virheet (virheet-tai-varoitukset->elementit @pot2-tiedot/alustarivit-virheet-atom :virhe)
        alustan-varoitukset (virheet-tai-varoitukset->elementit @pot2-tiedot/alustarivit-varoitukset-atom :varoitus)
        kulutuskerroksen-tekstit (concat [] kulutuskerroksen-virheet kulutuskerroksen-varoitukset)
        alustan-tekstit (concat [] alustan-virheet alustan-varoitukset)
        perustietojen-virheet (perustietojen-virheet (::lomake/puuttuvat-pakolliset-kentat perustiedot))]
    [:div.pot-lomakkeen-virheet
     (when
       (or (seq kulutuskerroksen-tekstit)
         (seq alustan-tekstit)
         (some? perustietojen-virheet))
       [yleiset/info-laatikko :varoitus
        "Lomakkeessa on virheitä. Lomaketta ei voi lähettää tarkistettavaksi ennen virheiden korjausta. "
        [:<>
         (when (some? perustietojen-virheet)
           [:span
            [:br]
            [:div [:b "Perustietojen virheet ja varoitukset"]]
            [:p perustietojen-virheet]])

         (when (seq kulutuskerroksen-tekstit)
           [:<>
            [:br]
            [:div [:b "Kulutuskerroksen virheet ja varoitukset"]]
            (into [:<>] kulutuskerroksen-tekstit)])

         (when (seq alustan-tekstit)
           [:<>
            [:br]
            [:div [:b "Alustan virheet ja varoitukset"]]
            (into [:<>] alustan-tekstit)])]
        nil])

     ;; YHA- ja VELHO-lähetyksen tila näytetään omassa info-laatikossaan, mutta saman virhe-elementin alla.
     [yha-ja-velho-lahetyksen-tila lahetyksen-tila perustiedot]]))

;; -- END -- Lomakkeen virheiden ja varoitusten infolaatikko --

(defn- perustiedot-ilman-lomaketietoja
  [perustiedot]
  (assoc (lomake/ilman-lomaketietoja perustiedot)
    :asiatarkastus (lomake/ilman-lomaketietoja (:asiatarkastus perustiedot))
    :tekninen-osa (lomake/ilman-lomaketietoja (:tekninen-osa perustiedot))))

(defn pot2-lomake
  [e! {paallystysilmoitus-lomakedata :paallystysilmoitus-lomakedata
       :as              app}
   lukko urakka kayttaja]
  ;; Toistaiseksi ei käytetä lukkoa POT2-näkymässä
  (let [muokkaa! (fn [f & args]
                   (e! (pot2-tiedot/->PaivitaTila [:paallystysilmoitus-lomakedata] (fn [vanha-arvo]
                                                                                     (apply f vanha-arvo args)))))
        perustiedot-hash-avatessa (hash (perustiedot-ilman-lomaketietoja (:perustiedot paallystysilmoitus-lomakedata)))
        ohjauskahva-paallystekerros (grid/grid-ohjaus)
        ohjauskahva-alusta (grid/grid-ohjaus)
        {:keys [tr-numero tr-alkuosa tr-loppuosa]} (get-in paallystysilmoitus-lomakedata [:perustiedot :tr-osoite])]
    (komp/luo
      (komp/lippu pot2-tiedot/pot2-nakymassa?)
      (komp/sisaan (fn [this]
                     (e! (paallystys/->HaeTrOsienPituudet tr-numero nil nil))
                     (e! (paallystys/->HaeTrOsienTiedot tr-numero tr-alkuosa tr-loppuosa))
                     (e! (mk-tiedot/->HaePot2MassatJaMurskeet))
                     (reset! pot2-tiedot/kohdeosat-virheet-atom nil)
                     (reset! pot2-tiedot/alustarivit-virheet-atom nil)
                     (reset! pot2-tiedot/valittu-alustan-sort :tieosoite)
                     (reset! pot2-tiedot/kohdeosat-atom
                             (-> (:paallystekerros paallystysilmoitus-lomakedata)
                                 (pot2-domain/lisaa-paallystekerroksen-jarjestysnro 1)
                                 (yllapitokohteet-domain/jarjesta-yllapitokohteet)
                                 (yllapitokohteet-domain/indeksoi-kohdeosat)))
                     (reset! pot2-tiedot/alustarivit-atom
                             (-> (:alusta paallystysilmoitus-lomakedata)
                                 (yllapitokohteet-domain/jarjesta-yllapitokohteet)
                                 (yllapitokohteet-domain/indeksoi-kohdeosat)))
                     (reset! pot2-tiedot/lisatiedot-atom (:lisatiedot paallystysilmoitus-lomakedata))
                     (nav/vaihda-kartan-koko! :S)))
      (fn [e! {:keys [paallystysilmoitus-lomakedata massat murskeet materiaalikoodistot
                      pot2-massa-lomake pot2-murske-lomake paikkauskohteet?] :as app}]
        (let [lukittu? (lukko/nakyma-lukittu? lukko)
              {:keys [perustiedot lahetyksen-tila tallennus-kaynnissa? muokattu?]} paallystysilmoitus-lomakedata
              perustiedot-app (select-keys paallystysilmoitus-lomakedata #{:perustiedot :kirjoitusoikeus? :ohjauskahvat})
              massalomake-app (select-keys app #{:pot2-massa-lomake :materiaalikoodistot})
              murskelomake-app (select-keys app #{:pot2-murske-lomake :materiaalikoodistot})
              alusta-app (select-keys paallystysilmoitus-lomakedata #{:kirjoitusoikeus? :perustiedot :alusta :alustalomake :tr-osien-pituudet :ohjauskahvat})
              paallystekerros-app (select-keys paallystysilmoitus-lomakedata #{:kirjoitusoikeus? :perustiedot :paallystekerros :tr-osien-pituudet :ohjauskahvat})
              tallenna-app (select-keys (get-in app [:paallystysilmoitus-lomakedata :perustiedot])
                                        #{:tekninen-osa :tila :versio})
              {:keys [tila]} perustiedot
              huomautukset (paallystys/perustietojen-huomautukset (:tekninen-osa perustiedot) (:valmispvm-kohde perustiedot))
              virheet (conj [] (-> perustiedot ::lomake/virheet))
              puuttuvat-pakolliset-kentat (-> perustiedot ::lomake/puuttuvat-pakolliset-kentat)
              valmis-tallennettavaksi? (and
                                         (not= tila :lukittu)
                                         (empty? (flatten (keep vals virheet)))
                                         (empty? puuttuvat-pakolliset-kentat)
                                         (empty? (keep identity (vals @pot2-tiedot/kohdeosat-virheet-atom)))
                                         (empty? (keep identity (vals @pot2-tiedot/alustarivit-virheet-atom))))
              perustiedot-hash-rendatessa (hash (perustiedot-ilman-lomaketietoja (:perustiedot paallystysilmoitus-lomakedata)))
              tietoja-muokattu? (or
                                  (not= perustiedot-hash-avatessa perustiedot-hash-rendatessa)
                                  muokattu?)]
          (e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata :ohjauskahvat :paallystekerros] ohjauskahva-paallystekerros))
          (e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata :ohjauskahvat :alusta] ohjauskahva-alusta))
          [:div.pot2-lomake
           [napit/takaisin
            "Takaisin ilmoitusluetteloon"
            #(if tietoja-muokattu?
               (varmista-kayttajalta/varmista-kayttajalta
                 {:otsikko "Lomakkeelta poistuminen"
                  :sisalto (str "Lomakkeella on tallentamattomia tietoja. Jos poistut, menetät tekemäsi muutokset. Haluatko varmasti poistua lomakkeelta?")
                  :hyvaksy "Poistu tallentamatta"
                  :peruuta-txt "Palaa lomakkeelle"
                  :toiminto-fn (fn []
                                 (e! (pot2-tiedot/->MuutaTila [:paallystysilmoitus-lomakedata] nil)))})
               (e! (pot2-tiedot/->MuutaTila [:paallystysilmoitus-lomakedata] nil)))]
           [pot-yhteinen/otsikkotiedot e! perustiedot urakka]
           [lomakkeen-virheet lahetyksen-tila perustiedot]
           [pot-yhteinen/paallystysilmoitus-perustiedot
            e! perustiedot-app urakka false muokkaa! pot2-validoinnit huomautukset paikkauskohteet?]
           [:hr]
           [materiaalit e! massat murskeet]
           [yleiset/valitys-vertical]
           [paallystekerros/paallystekerros e! paallystekerros-app {:massat massat
                                                                    :materiaalikoodistot materiaalikoodistot
                                                                    :validointi (:paallystekerros pot2-validoinnit)
                                                                    :virheet-atom pot2-tiedot/kohdeosat-virheet-atom
                                                                    :varoitukset-atom pot2-tiedot/kohdeosat-varoitukset-atom}
            pot2-tiedot/kohdeosat-atom]
           [yleiset/valitys-vertical]
           [alusta/alusta e! alusta-app {:massat massat :murskeet murskeet
                                         :materiaalikoodistot materiaalikoodistot
                                         :validointi (:alusta pot2-validoinnit)
                                         :virheet-atom pot2-tiedot/alustarivit-virheet-atom
                                         :varoitukset-atom pot2-tiedot/alustarivit-varoitukset-atom}
            pot2-tiedot/alustarivit-atom]

           ;; jos käyttäjä haluaa katsella sivupaneelissa massan tai murskeen tietoja
           (cond (and pot2-massa-lomake (:sivulle? pot2-massa-lomake))
                 [massa-lomake/massa-lomake e! massalomake-app]

                 (and pot2-murske-lomake (:sivulle? pot2-murske-lomake))
                 [murske-lomake/murske-lomake e! murskelomake-app ]

                 :else
                 [:span])
           [yleiset/valitys-vertical]
           [lisatiedot e! perustiedot pot2-tiedot/lisatiedot-atom]
           (when-not (empty? @pot2-tiedot/kohdeosat-atom)
             [:span
              [pot-yhteinen/kasittely e! perustiedot-app urakka lukittu? muokkaa! pot2-validoinnit huomautukset]
              [yleiset/valitys-vertical]])

           ;; Näytetään virhe-infolaatikko vielä toisen kerran tallennusnapin yläpuolella, jotta virheet tulee huomioitua.
           [lomakkeen-virheet lahetyksen-tila perustiedot]

           [pot-yhteinen/tallenna e! perustiedot-app tallenna-app {:kayttaja kayttaja
                                                                   :urakka-id (:id urakka)
                                                                   :valmis-tallennettavaksi? valmis-tallennettavaksi?
                                                                   :tallennus-kaynnissa? tallennus-kaynnissa?}]
           [yleiset/valitys-vertical]])))))
